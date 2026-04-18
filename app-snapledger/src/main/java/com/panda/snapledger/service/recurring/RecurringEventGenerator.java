package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecurringEventGenerator {

    public static final int DEFAULT_WINDOW_PERIODS = 36;
    private static final int SAFETY_LIMIT = 10_000;

    private final RecordRepository recordRepository;

    public RecurringEventGenerator(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** 为 [fromPeriod, toPeriod] 区间生成 record，跳过已存在的 period_number。 */
    public void generate(RecurringEvent event, int fromPeriod, int toPeriod) {
        Set<Integer> existing = recordRepository
            .findByRecurringEventIdOrderByDateDesc(event.getId())
            .stream()
            .map(Record::getPeriodNumber)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<Record> toSave = new ArrayList<>();
        for (int p = fromPeriod; p <= toPeriod; p++) {
            if (existing.contains(p)) continue;
            toSave.add(buildRecord(event, p));
        }
        if (!toSave.isEmpty()) {
            recordRepository.saveAll(toSave);
        }
    }

    /** 无限期事件：返回滚动窗口的目标截止日期（当前所在期 + 36 期）。 */
    public LocalDate targetWindowEnd(RecurringEvent event, LocalDate today) {
        int periodsSinceStart = periodsBetween(event, event.getStartDate(), today);
        int targetPeriod = periodsSinceStart + DEFAULT_WINDOW_PERIODS;
        return PeriodDateCalculator.dateOfPeriod(event, Math.max(targetPeriod, DEFAULT_WINDOW_PERIODS));
    }

    /**
     * 返回给定日期 to 对应的期数（1-based）。
     *
     * 对 MONTHLY / YEARLY 事件按自然日历计算：同一历月/年内的记录归属同一期，
     * 避免"2/18 被算进 1/19 期"的错配。对 WEEKLY / DAILY 按顺序线性推进。
     */
    public int periodsBetween(RecurringEvent event, LocalDate from, LocalDate to) {
        int interval = event.getIntervalValue() != null ? event.getIntervalValue() : 1;
        switch (event.getIntervalType()) {
            case RecurringEvent.INTERVAL_MONTHLY: {
                int months = (to.getYear() - from.getYear()) * 12
                    + (to.getMonthValue() - from.getMonthValue());
                return Math.max(1, months / interval + 1);
            }
            case RecurringEvent.INTERVAL_YEARLY: {
                int years = to.getYear() - from.getYear();
                return Math.max(1, years / interval + 1);
            }
            default: {
                int p = 1;
                while (p < SAFETY_LIMIT) {
                    LocalDate next = PeriodDateCalculator.dateOfPeriod(event, p + 1);
                    if (next.isAfter(to)) break;
                    p++;
                }
                return p;
            }
        }
    }

    private Record buildRecord(RecurringEvent event, int period) {
        Record r = new Record();
        r.setRecurringEventId(event.getId());
        r.setPeriodNumber(period);
        r.setName(event.getName());
        r.setRecordType(event.getRecordType());
        r.setMainCategory(event.getMainCategory());
        r.setSubCategory(event.getSubCategory());
        r.setAmount(event.getAmount());
        r.setAccount(event.getAccount());
        r.setTarget(event.getTargetAccount());
        r.setDate(PeriodDateCalculator.dateOfPeriod(event, period));
        return r;
    }
}
