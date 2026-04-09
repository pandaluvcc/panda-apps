package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.CategoryStatsDTO;
import com.panda.snapledger.controller.dto.MonthlyStatsDTO;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Statistics service - calculate monthly/yearly/category stats.
 */
@Service
public class StatsService {

    private final RecordRepository recordRepository;

    public StatsService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * Get monthly statistics: total income, total expense, balance, and category breakdown.
     */
    public MonthlyStatsDTO getMonthlyStats(int year, int month) {
        List<Record> records = recordRepository.findByYearAndMonth(year, month);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Map<String, BigDecimal> categoryMap = new HashMap<>();

        for (Record record : records) {
            BigDecimal amount = record.getAmount().abs();
            if ("收入".equals(record.getRecordType())) {
                totalIncome = totalIncome.add(amount);
            } else {
                totalExpense = totalExpense.add(amount);
            }

            // Accumulate by main category
            String category = record.getMainCategory();
            if (category != null && !category.isEmpty()) {
                categoryMap.merge(category, amount, BigDecimal::add);
            }
        }

        BigDecimal balance = totalIncome.subtract(totalExpense);

        List<CategoryStatsDTO> categoryStats = buildCategoryStats(categoryMap, totalExpense);

        return MonthlyStatsDTO.builder()
                .year(year)
                .month(month)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .categoryStats(categoryStats)
                .build();
    }

    /**
     * Get category statistics for specified year/month and type.
     */
    public List<CategoryStatsDTO> getCategoryStats(int year, int month, String type) {
        List<Record> records = recordRepository.findByYearAndMonthAndType(year, month, type);

        Map<String, BigDecimal> categoryMap = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Record record : records) {
            BigDecimal amount = record.getAmount().abs();
            total = total.add(amount);
            String category = record.getMainCategory();
            if (category != null && !category.isEmpty()) {
                categoryMap.merge(category, amount, BigDecimal::add);
            }
        }

        return buildCategoryStats(categoryMap, total);
    }

    /**
     * Get yearly statistics aggregated by month.
     */
    public List<MonthlyStatsDTO> getYearlyStats(int year) {
        List<MonthlyStatsDTO> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            result.add(getMonthlyStats(year, month));
        }
        return result;
    }

    private List<CategoryStatsDTO> buildCategoryStats(Map<String, BigDecimal> categoryMap, BigDecimal total) {
        List<CategoryStatsDTO> result = new ArrayList<>();

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return result;
        }

        for (Map.Entry<String, BigDecimal> entry : categoryMap.entrySet()) {
            BigDecimal percentage = entry.getValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);

            result.add(CategoryStatsDTO.builder()
                    .categoryName(entry.getKey())
                    .amount(entry.getValue())
                    .percentage(percentage)
                    .build());
        }

        // Sort by amount descending
        return result.stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
    }
}
