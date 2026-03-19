package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.CalendarDayDTO;
import com.panda.snapledger.controller.dto.CalendarMonthDTO;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CalendarService {

    private final RecordRepository recordRepository;

    public CalendarService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public CalendarMonthDTO getMonthCalendar(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<Record> records = recordRepository.findByDateBetweenOrderByDateDescTimeDesc(start, end);

        Map<LocalDate, List<Record>> recordsByDate = records.stream()
                .collect(Collectors.groupingBy(Record::getDate));

        List<CalendarDayDTO> days = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            List<Record> dayRecords = recordsByDate.getOrDefault(date, List.of());

            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;

            for (Record r : dayRecords) {
                if ("收入".equals(r.getRecordType())) {
                    income = income.add(r.getAmount());
                } else if ("支出".equals(r.getRecordType())) {
                    expense = expense.add(r.getAmount());
                }
            }

            days.add(new CalendarDayDTO(date, income, expense, dayRecords.size()));
            totalIncome = totalIncome.add(income);
            totalExpense = totalExpense.add(expense);
        }

        return new CalendarMonthDTO(year, month, days, totalIncome, totalExpense);
    }
}
