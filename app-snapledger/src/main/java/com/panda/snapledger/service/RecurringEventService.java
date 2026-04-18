package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import com.panda.snapledger.service.recurring.PeriodDateCalculator;
import com.panda.snapledger.service.recurring.RecurringEventGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecurringEventService {

    private final RecurringEventRepository eventRepo;
    private final RecordRepository recordRepo;
    private final RecurringEventGenerator generator;

    public RecurringEventService(RecurringEventRepository eventRepo,
                                 RecordRepository recordRepo,
                                 RecurringEventGenerator generator) {
        this.eventRepo = eventRepo;
        this.recordRepo = recordRepo;
        this.generator = generator;
    }

    @Transactional(rollbackFor = Exception.class)
    public RecurringEvent create(RecurringEventRequest req) {
        RecurringEvent e = new RecurringEvent();
        applyRequest(e, req);
        e.setStatus(RecurringEvent.STATUS_ACTIVE);
        // 占位：先设为 startDate，保证 not-null 约束；生成完成后更新为实际末期日期
        e.setGeneratedUntil(e.getStartDate());
        e = eventRepo.save(e);

        int totalToGenerate = req.getTotalPeriods() != null
            ? req.getTotalPeriods()
            : RecurringEventGenerator.DEFAULT_WINDOW_PERIODS;
        generator.generate(e, 1, totalToGenerate);
        e.setGeneratedUntil(PeriodDateCalculator.dateOfPeriod(e, totalToGenerate));
        eventRepo.save(e);

        backfillHistorical(e);
        return e;
    }

    @Transactional
    public RecurringEvent updateEntireEvent(Long id, RecurringEventRequest req) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        applyRequest(e, req);
        eventRepo.save(e);
        LocalDate today = LocalDate.now();
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        for (Record r : future) {
            applyTemplateToRecord(e, r);
        }
        recordRepo.saveAll(future);
        return e;
    }

    @Transactional
    public RecurringEvent updateFromPeriod(Long id, int fromPeriod, RecurringEventRequest req) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        applyRequest(e, req);
        eventRepo.save(e);
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(id);
        List<Record> toUpdate = new ArrayList<>();
        for (Record r : all) {
            if (r.getPeriodNumber() != null && r.getPeriodNumber() >= fromPeriod) {
                applyTemplateToRecord(e, r);
                toUpdate.add(r);
            }
        }
        recordRepo.saveAll(toUpdate);
        return e;
    }

    @Transactional
    public void endEvent(Long id) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        LocalDate today = LocalDate.now();
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        recordRepo.deleteAll(future);
        e.setStatus(RecurringEvent.STATUS_ENDED);
        e.setEndedAt(LocalDateTime.now());
        eventRepo.save(e);
    }

    @Transactional
    public void deleteEvent(Long id) {
        LocalDate today = LocalDate.now();
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        recordRepo.deleteAll(future);
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(id);
        for (Record r : all) {
            r.setRecurringEventId(null);
            r.setPeriodNumber(null);
        }
        recordRepo.saveAll(all);
        eventRepo.deleteById(id);
    }

    public List<RecurringEvent> list(String status) {
        return eventRepo.findByStatusOrderByIdDesc(status);
    }

    public RecurringEvent findById(Long id) {
        return eventRepo.findById(id).orElseThrow();
    }

    public List<Record> findRecords(Long eventId) {
        return recordRepo.findByRecurringEventIdOrderByDateDesc(eventId);
    }

    /** 外部调用：把当前 DB 中同名的孤儿记录回溯挂到已存在事件上。 */
    @Transactional
    public void backfillOrphansForEvent(Long eventId) {
        RecurringEvent e = eventRepo.findById(eventId).orElseThrow();
        backfillHistorical(e);
    }

    private void backfillHistorical(RecurringEvent event) {
        List<Record> orphans = recordRepo.findByNameAndRecurringEventIdIsNull(event.getName());
        if (orphans.isEmpty()) return;

        for (Record r : orphans) {
            r.setRecurringEventId(event.getId());
            int period = generator.periodsBetween(event, event.getStartDate(), r.getDate());
            r.setPeriodNumber(period);
        }
        recordRepo.saveAll(orphans);
        deleteDuplicateGeneratedPeriods(event, orphans);
    }

    private void deleteDuplicateGeneratedPeriods(RecurringEvent event, List<Record> historical) {
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(event.getId());
        Set<Long> historyIds = historical.stream().map(Record::getId).collect(Collectors.toSet());
        Map<Integer, List<Record>> byPeriod = new HashMap<>();
        for (Record r : all) {
            if (r.getPeriodNumber() == null) continue;
            byPeriod.computeIfAbsent(r.getPeriodNumber(), k -> new ArrayList<>()).add(r);
        }
        List<Record> toDelete = new ArrayList<>();
        for (List<Record> group : byPeriod.values()) {
            if (group.size() < 2) continue;
            boolean hasHistory = group.stream().anyMatch(r -> historyIds.contains(r.getId()));
            if (!hasHistory) continue;
            for (Record r : group) {
                if (!historyIds.contains(r.getId())) toDelete.add(r);
            }
        }
        if (!toDelete.isEmpty()) recordRepo.deleteAll(toDelete);
    }

    private void applyRequest(RecurringEvent e, RecurringEventRequest req) {
        e.setName(req.getName());
        e.setRecordType(req.getRecordType());
        e.setAmount(req.getAmount());
        e.setMainCategory(req.getMainCategory());
        e.setSubCategory(req.getSubCategory());
        e.setAccount(req.getAccount());
        e.setTargetAccount(req.getTargetAccount());
        e.setIntervalType(req.getIntervalType());
        e.setIntervalValue(req.getIntervalValue() != null ? req.getIntervalValue() : 1);
        e.setDayOfMonth(req.getDayOfMonth());
        e.setDayOfWeek(req.getDayOfWeek());
        e.setStartDate(req.getStartDate());
        e.setTotalPeriods(req.getTotalPeriods());
        e.setNote(req.getNote());
    }

    private void applyTemplateToRecord(RecurringEvent e, Record r) {
        r.setName(e.getName());
        r.setRecordType(e.getRecordType());
        r.setAmount(e.getAmount());
        r.setMainCategory(e.getMainCategory());
        r.setSubCategory(e.getSubCategory());
        r.setAccount(e.getAccount());
        r.setTarget(e.getTargetAccount());
    }
}
