package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.RecurringEvent;

import java.time.LocalDate;

public final class PeriodDateCalculator {

    private PeriodDateCalculator() {}

    /** 返回第 period 期的日期（period 从 1 开始）。 */
    public static LocalDate dateOfPeriod(RecurringEvent event, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        int offset = (period - 1) * event.getIntervalValue();
        LocalDate start = event.getStartDate();
        return switch (event.getIntervalType()) {
            case RecurringEvent.INTERVAL_DAILY -> start.plusDays(offset);
            case RecurringEvent.INTERVAL_WEEKLY -> start.plusWeeks(offset);
            case RecurringEvent.INTERVAL_MONTHLY -> start.plusMonths(offset);
            case RecurringEvent.INTERVAL_YEARLY -> start.plusYears(offset);
            default -> throw new IllegalArgumentException("unknown interval: " + event.getIntervalType());
        };
    }
}
