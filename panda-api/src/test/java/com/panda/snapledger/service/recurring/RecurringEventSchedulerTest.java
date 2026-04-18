package com.panda.snapledger.service.recurring;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import com.panda.snapledger.service.RecurringEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PandaApplication.class)
class RecurringEventSchedulerTest {

    @Autowired RecurringEventScheduler scheduler;
    @Autowired RecurringEventService service;
    @Autowired RecurringEventRepository eventRepo;
    @Autowired RecordRepository recordRepo;

    @BeforeEach
    void clean() {
        recordRepo.deleteAll();
        eventRepo.deleteAll();
    }

    private RecurringEventRequest monthlyReq(String name, LocalDate start, Integer totalPeriods) {
        RecurringEventRequest r = new RecurringEventRequest();
        r.setName(name);
        r.setRecordType("支出");
        r.setAmount(new BigDecimal("100.00"));
        r.setAccount("测试账户");
        r.setIntervalType("MONTHLY");
        r.setIntervalValue(1);
        r.setDayOfMonth(start.getDayOfMonth());
        r.setStartDate(start);
        r.setTotalPeriods(totalPeriods);
        return r;
    }

    @Test
    void extendInfiniteWindowsExtendsWhenCloseToEnd() {
        // 起始日期设为很早，使 generatedUntil 接近 today
        LocalDate longAgoStart = LocalDate.now().minusMonths(34);
        RecurringEvent e = service.create(monthlyReq("测试无限", longAgoStart, null));
        LocalDate before = e.getGeneratedUntil();

        scheduler.extendInfiniteWindows();

        RecurringEvent reloaded = eventRepo.findById(e.getId()).orElseThrow();
        assertTrue(reloaded.getGeneratedUntil().isAfter(before),
            "generatedUntil should extend from " + before + " to a later date, got " + reloaded.getGeneratedUntil());
        List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertTrue(records.size() > 36);
    }

    @Test
    void autoEndFiniteEventsEndsWhenAllPastDue() {
        // 有限事件所有期已过：起始日期为 24 个月前，3 期 → 最后一期在 22 个月前
        LocalDate pastStart = LocalDate.now().minusMonths(24);
        RecurringEvent e = service.create(monthlyReq("测试有限", pastStart, 3));
        assertEquals(RecurringEvent.STATUS_ACTIVE, e.getStatus());

        scheduler.autoEndFiniteEvents();

        RecurringEvent reloaded = eventRepo.findById(e.getId()).orElseThrow();
        assertEquals(RecurringEvent.STATUS_ENDED, reloaded.getStatus());
        assertNotNull(reloaded.getEndedAt());
    }

    @Test
    void autoEndDoesNotEndFiniteEventsWithFuturePeriods() {
        // 有限事件最后一期在未来
        LocalDate start = LocalDate.now().minusMonths(1);
        RecurringEvent e = service.create(monthlyReq("测试有限进行中", start, 12));

        scheduler.autoEndFiniteEvents();

        RecurringEvent reloaded = eventRepo.findById(e.getId()).orElseThrow();
        assertEquals(RecurringEvent.STATUS_ACTIVE, reloaded.getStatus());
    }
}
