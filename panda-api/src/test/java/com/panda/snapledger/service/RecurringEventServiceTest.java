package com.panda.snapledger.service;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class RecurringEventServiceTest {

    @Autowired RecurringEventService service;
    @Autowired RecurringEventRepository eventRepo;
    @Autowired RecordRepository recordRepo;

    @BeforeEach
    void clean() {
        recordRepo.deleteAll();
        eventRepo.deleteAll();
    }

    private RecurringEventRequest monthlyReq(String name, Integer totalPeriods) {
        RecurringEventRequest r = new RecurringEventRequest();
        r.setName(name);
        r.setRecordType("支出");
        r.setAmount(new BigDecimal("2985.34"));
        r.setMainCategory("房产");
        r.setAccount("中信银行");
        r.setIntervalType("MONTHLY");
        r.setIntervalValue(1);
        r.setDayOfMonth(20);
        r.setStartDate(LocalDate.of(2024, 12, 20));
        r.setTotalPeriods(totalPeriods);
        return r;
    }

    @Test
    void createInfiniteEventGenerates36Periods() {
        RecurringEvent e = service.create(monthlyReq("商贷", null));
        List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertEquals(36, records.size());
        assertNotNull(e.getGeneratedUntil());
    }

    @Test
    void createFiniteEventGeneratesAllPeriods() {
        RecurringEvent e = service.create(monthlyReq("车贷", 12));
        List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertEquals(12, records.size());
    }

    @Test
    void createEventBackfillsHistoricalRecordsByName() {
        LocalDate base = LocalDate.of(2024, 12, 20);
        for (int i = 0; i < 3; i++) {
            Record r = new Record();
            r.setName("商贷");
            r.setAmount(new BigDecimal("2985.34"));
            r.setRecordType("支出");
            r.setAccount("中信银行");
            r.setDate(base.plusMonths(i));
            recordRepo.save(r);
        }

        RecurringEvent e = service.create(monthlyReq("商贷", null));

        List<Record> linked = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertTrue(linked.stream().allMatch(r -> e.getId().equals(r.getRecurringEventId())));
        assertEquals(0, recordRepo.findByNameAndRecurringEventIdIsNull("商贷").size());
        // 3 条历史 + 33 条新生成
        assertEquals(36, linked.size());
    }

    @Test
    void updateEntireEventSyncsFutureRecords() {
        RecurringEvent e = service.create(monthlyReq("商贷", null));
        BigDecimal oldAmount = e.getAmount();

        RecurringEventRequest updated = monthlyReq("商贷", null);
        updated.setAmount(new BigDecimal("3100.00"));
        service.updateEntireEvent(e.getId(), updated);

        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(e.getId(), LocalDate.now());
        assertFalse(future.isEmpty());
        assertTrue(future.stream().allMatch(r -> new BigDecimal("3100.00").compareTo(r.getAmount()) == 0));
    }

    @Test
    void updateFromPeriodOnlyAffectsTargetPeriods() {
        RecurringEvent e = service.create(monthlyReq("商贷", 12));

        RecurringEventRequest updated = monthlyReq("商贷", 12);
        updated.setAmount(new BigDecimal("3100.00"));
        service.updateFromPeriod(e.getId(), 6, updated);

        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        for (Record r : all) {
            if (r.getPeriodNumber() >= 6) {
                assertEquals(0, new BigDecimal("3100.00").compareTo(r.getAmount()));
            } else {
                assertEquals(0, new BigDecimal("2985.34").compareTo(r.getAmount()));
            }
        }
    }

    @Test
    void endEventDeletesFutureRecordsAndKeepsHistory() {
        RecurringEvent e = service.create(monthlyReq("商贷", null));
        int before = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId()).size();
        int futureBefore = recordRepo.findByRecurringEventIdAndDateAfter(e.getId(), LocalDate.now()).size();

        service.endEvent(e.getId());

        RecurringEvent reloaded = eventRepo.findById(e.getId()).orElseThrow();
        assertEquals(RecurringEvent.STATUS_ENDED, reloaded.getStatus());
        assertNotNull(reloaded.getEndedAt());
        int after = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId()).size();
        assertEquals(before - futureBefore, after);
    }

    @Test
    void deleteEventUnbindsHistoricalRecords() {
        Record hist = new Record();
        hist.setName("商贷");
        hist.setAmount(new BigDecimal("2985.34"));
        hist.setRecordType("支出");
        hist.setAccount("中信银行");
        hist.setDate(LocalDate.of(2024, 12, 20));
        recordRepo.save(hist);

        RecurringEvent e = service.create(monthlyReq("商贷", null));
        Long eventId = e.getId();

        service.deleteEvent(eventId);

        assertFalse(eventRepo.findById(eventId).isPresent());
        // 历史 record 仍存在，解绑
        List<Record> orphans = recordRepo.findByNameAndRecurringEventIdIsNull("商贷");
        assertFalse(orphans.isEmpty());
    }
}
