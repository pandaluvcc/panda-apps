package com.panda.snapledger.service.receivable;

import com.panda.PandaApplication;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class ReceivableLinkingServiceTest {

    @Autowired RecordRepository recordRepository;
    @Autowired ReceivableLinkingService linkingService;

    @BeforeEach
    void cleanup() {
        recordRepository.deleteAll();
    }

    private Record saveRecord(String account, String name, String recordType,
                              String subCategory, BigDecimal amount,
                              LocalDate date, LocalTime time) {
        Record r = new Record();
        r.setAccount(account);
        r.setName(name);
        r.setRecordType(recordType);
        r.setSubCategory(subCategory);
        r.setAmount(amount);
        r.setDate(date);
        r.setTime(time);
        return recordRepository.save(r);
    }

    @Test
    void lendOnceRepayPartial_oneParentOneChildWith1500Paid() {
        Record parent = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-5000"), LocalDate.of(2022, 4, 26), LocalTime.of(18, 26));
        Record child = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("1500"), LocalDate.of(2022, 5, 4), LocalTime.of(10, 54));

        linkingService.linkAll();

        Record p2 = recordRepository.findById(parent.getId()).orElseThrow();
        Record c2 = recordRepository.findById(child.getId()).orElseThrow();
        assertThat(p2.getParentRecordId()).isNull();
        assertThat(c2.getParentRecordId()).isEqualTo(parent.getId());
    }

    @Test
    void lendOnceRepaidFully_twoChildren() {
        Record p = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-4000"), LocalDate.of(2022, 11, 28), LocalTime.of(19, 15));
        Record c1 = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("2000"), LocalDate.of(2022, 11, 28), LocalTime.of(19, 17));
        Record c2 = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("2000"), LocalDate.of(2022, 11, 29), LocalTime.of(19, 17));

        linkingService.linkAll();

        assertThat(recordRepository.findById(c1.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
        assertThat(recordRepository.findById(c2.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
    }

    @Test
    void borrowRecord_parentPositiveChildNegative() {
        Record p = saveRecord("招商银行°", "我妈借我", "应付款项", "借入",
                new BigDecimal("3500"), LocalDate.of(2022, 5, 19), LocalTime.of(18, 12));
        Record c = saveRecord("招商银行°", "我妈借我", "应付款项", "借入",
                new BigDecimal("-1000"), LocalDate.of(2022, 6, 1), LocalTime.of(10, 0));

        linkingService.linkAll();

        assertThat(recordRepository.findById(c.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
    }

    @Test
    void idempotent_reLinkDoesNotDuplicate() {
        Record parent = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-2000"), LocalDate.of(2023, 1, 7), LocalTime.of(14, 20));

        linkingService.linkAll();
        linkingService.linkAll();

        List<Record> all = recordRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(recordRepository.findById(parent.getId()).orElseThrow().getParentRecordId()).isNull();
    }

    @Test
    void mortgagePairsLinkAcrossAccounts() {
        // 房贷跨账户配对：房产°(+) 是借款主记录，中信银行(-) 是还款子记录
        Record parent = saveRecord("房产°", "商贷", "应付款项", "房贷",
                new BigDecimal("2985.34"), LocalDate.of(2025, 7, 20), LocalTime.of(18, 15));
        Record child = saveRecord("中信银行", "商贷", "应付款项", "房贷",
                new BigDecimal("-2985.34"), LocalDate.of(2025, 7, 20), LocalTime.of(23, 59));

        linkingService.linkAll();

        assertThat(recordRepository.findById(parent.getId()).orElseThrow().getParentRecordId()).isNull();
        assertThat(recordRepository.findById(child.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(parent.getId());
    }

    @Test
    void mortgagePairsLink_whenChildTimeEarlierThanParent() {
        // 用户可能先记了还款时间 (07:41)，后记借款时间 (17:19)，但两者同日
        // 算法应按"主方向优先"排序，保证 parent 先入队
        Record child = saveRecord("招商银行°", "6月房贷", "应付款项", "房贷",
                new BigDecimal("-3922.72"), LocalDate.of(2022, 6, 20), LocalTime.of(7, 41));
        Record parent = saveRecord("房产°", "6月房贷", "应付款项", "房贷",
                new BigDecimal("3922.72"), LocalDate.of(2022, 6, 20), LocalTime.of(17, 19));

        linkingService.linkAll();

        assertThat(recordRepository.findById(parent.getId()).orElseThrow().getParentRecordId()).isNull();
        assertThat(recordRepository.findById(child.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(parent.getId());
    }

    @Test
    void mortgageWithoutPaymentStaysOutstanding() {
        // 只有借款侧、无还款侧的房贷（最近的月份）应保持为未还
        Record parent = saveRecord("房产°", "商贷", "应付款项", "房贷",
                new BigDecimal("2985.34"), LocalDate.of(2025, 8, 20), LocalTime.of(18, 15));

        linkingService.linkAll();

        assertThat(recordRepository.findById(parent.getId()).orElseThrow().getParentRecordId()).isNull();
    }
}
