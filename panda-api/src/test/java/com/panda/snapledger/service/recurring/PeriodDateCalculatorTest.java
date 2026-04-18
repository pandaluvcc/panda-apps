package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.RecurringEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodDateCalculatorTest {

    private RecurringEvent event(String type, int value, LocalDate start) {
        RecurringEvent e = new RecurringEvent();
        e.setIntervalType(type);
        e.setIntervalValue(value);
        e.setStartDate(start);
        return e;
    }

    @Test
    void monthlyNthPeriodAddsMonths() {
        RecurringEvent e = event("MONTHLY", 1, LocalDate.of(2026, 1, 19));
        assertEquals(LocalDate.of(2026, 1, 19), PeriodDateCalculator.dateOfPeriod(e, 1));
        assertEquals(LocalDate.of(2026, 2, 19), PeriodDateCalculator.dateOfPeriod(e, 2));
        assertEquals(LocalDate.of(2027, 1, 19), PeriodDateCalculator.dateOfPeriod(e, 13));
    }

    @Test
    void monthlyClampsToMonthEndWhenDayMissing() {
        RecurringEvent e = event("MONTHLY", 1, LocalDate.of(2026, 1, 31));
        assertEquals(LocalDate.of(2026, 2, 28), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void weeklyAddsSevenDays() {
        RecurringEvent e = event("WEEKLY", 1, LocalDate.of(2026, 4, 18));
        assertEquals(LocalDate.of(2026, 4, 25), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void dailyAddsDays() {
        RecurringEvent e = event("DAILY", 3, LocalDate.of(2026, 4, 18));
        assertEquals(LocalDate.of(2026, 4, 21), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void yearlyAddsYears() {
        RecurringEvent e = event("YEARLY", 1, LocalDate.of(2024, 2, 29));
        assertEquals(LocalDate.of(2025, 2, 28), PeriodDateCalculator.dateOfPeriod(e, 2));
    }
}
