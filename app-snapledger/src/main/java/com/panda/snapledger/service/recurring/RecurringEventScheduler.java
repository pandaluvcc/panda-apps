package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RecurringEventScheduler {

    static final int EXTEND_THRESHOLD_DAYS = 180;

    private final RecurringEventRepository eventRepo;
    private final RecordRepository recordRepo;
    private final RecurringEventGenerator generator;

    public RecurringEventScheduler(RecurringEventRepository eventRepo,
                                    RecordRepository recordRepo,
                                    RecurringEventGenerator generator) {
        this.eventRepo = eventRepo;
        this.recordRepo = recordRepo;
        this.generator = generator;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runDaily() {
        extendInfiniteWindows();
        autoEndFiniteEvents();
    }

    @Transactional
    public void extendInfiniteWindows() {
        LocalDate today = LocalDate.now();
        List<RecurringEvent> events = eventRepo.findByStatusAndTotalPeriodsIsNull(RecurringEvent.STATUS_ACTIVE);
        for (RecurringEvent e : events) {
            if (e.getGeneratedUntil() == null) continue;
            long daysAhead = e.getGeneratedUntil().toEpochDay() - today.toEpochDay();
            if (daysAhead > EXTEND_THRESHOLD_DAYS) continue;
            int currentPeriod = generator.periodsBetween(e, e.getStartDate(), e.getGeneratedUntil());
            int targetPeriod = currentPeriod + RecurringEventGenerator.DEFAULT_WINDOW_PERIODS;
            generator.generate(e, currentPeriod + 1, targetPeriod);
            e.setGeneratedUntil(PeriodDateCalculator.dateOfPeriod(e, targetPeriod));
            eventRepo.save(e);
        }
    }

    @Transactional
    public void autoEndFiniteEvents() {
        LocalDate today = LocalDate.now();
        List<RecurringEvent> events = eventRepo.findByStatusAndTotalPeriodsIsNotNull(RecurringEvent.STATUS_ACTIVE);
        for (RecurringEvent e : events) {
            List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
            if (records.isEmpty()) continue;
            LocalDate lastDate = records.get(0).getDate();
            if (lastDate.isBefore(today)) {
                e.setStatus(RecurringEvent.STATUS_ENDED);
                e.setEndedAt(LocalDateTime.now());
                eventRepo.save(e);
            }
        }
    }
}
