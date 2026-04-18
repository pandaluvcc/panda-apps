package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringEventGeneratorTest {

    @Mock RecordRepository recordRepository;
    @InjectMocks RecurringEventGenerator generator;

    private RecurringEvent monthlyEvent() {
        RecurringEvent e = new RecurringEvent();
        e.setId(1L);
        e.setName("商贷");
        e.setRecordType("支出");
        e.setAmount(new BigDecimal("2985.34"));
        e.setMainCategory("房产");
        e.setAccount("中信银行");
        e.setIntervalType("MONTHLY");
        e.setIntervalValue(1);
        e.setDayOfMonth(20);
        e.setStartDate(LocalDate.of(2024, 12, 20));
        return e;
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateCreatesRecordsForPeriodRange() {
        when(recordRepository.findByRecurringEventIdOrderByDateDesc(1L))
            .thenReturn(Collections.emptyList());
        ArgumentCaptor<List<Record>> captor = ArgumentCaptor.forClass(List.class);

        generator.generate(monthlyEvent(), 1, 3);

        verify(recordRepository).saveAll(captor.capture());
        List<Record> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals(1, saved.get(0).getPeriodNumber());
        assertEquals(LocalDate.of(2024, 12, 20), saved.get(0).getDate());
        assertEquals(LocalDate.of(2025, 2, 20), saved.get(2).getDate());
        assertEquals(new BigDecimal("2985.34"), saved.get(0).getAmount());
        assertEquals(1L, saved.get(0).getRecurringEventId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSkipsExistingPeriodNumbers() {
        Record existing = new Record();
        existing.setPeriodNumber(2);
        when(recordRepository.findByRecurringEventIdOrderByDateDesc(1L))
            .thenReturn(List.of(existing));
        ArgumentCaptor<List<Record>> captor = ArgumentCaptor.forClass(List.class);

        generator.generate(monthlyEvent(), 1, 3);

        verify(recordRepository).saveAll(captor.capture());
        List<Record> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().noneMatch(r -> Integer.valueOf(2).equals(r.getPeriodNumber())));
    }

    @Test
    void targetWindowEndReturnsDateCoveringThirtySixPeriods() {
        RecurringEvent e = monthlyEvent();
        LocalDate today = LocalDate.of(2026, 4, 18);
        LocalDate target = generator.targetWindowEnd(e, today);
        assertTrue(target.isAfter(today.plusMonths(35)));
    }
}
