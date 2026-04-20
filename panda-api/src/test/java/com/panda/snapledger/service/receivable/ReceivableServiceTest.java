package com.panda.snapledger.service.receivable;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.CreateReceivableChildRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class ReceivableServiceTest {

    @Autowired RecordRepository recordRepository;
    @Autowired ReceivableService receivableService;

    @BeforeEach
    void clean() { recordRepository.deleteAll(); }

    private Record saveParent(String recordType, BigDecimal amount) {
        Record r = new Record();
        r.setAccount("招商银行°");
        r.setName("借给阿芳°");
        r.setRecordType(recordType);
        r.setSubCategory("应收款项".equals(recordType) ? "借出" : "借入");
        r.setAmount(amount);
        r.setDate(LocalDate.now().minusDays(10));
        r.setTime(LocalTime.NOON);
        return recordRepository.save(r);
    }

    @Test
    void addChildToReceivable_childAmountPositive() {
        Record parent = saveParent("应收款项", new BigDecimal("-5000"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("1500"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        Record child = receivableService.addChild(parent.getId(), req);

        assertThat(child.getParentRecordId()).isEqualTo(parent.getId());
        assertThat(child.getAmount()).isEqualByComparingTo("1500");
        assertThat(child.getRecordType()).isEqualTo("应收款项");
        assertThat(child.getName()).isEqualTo("借给阿芳°");
    }

    @Test
    void addChildToPayable_childAmountNegative() {
        Record parent = saveParent("应付款项", new BigDecimal("3500"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("1000"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        Record child = receivableService.addChild(parent.getId(), req);

        assertThat(child.getAmount()).isEqualByComparingTo("-1000");
    }

    @Test
    void rejectOverpayment() {
        Record parent = saveParent("应收款项", new BigDecimal("-100"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("200"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        assertThatThrownBy(() -> receivableService.addChild(parent.getId(), req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
