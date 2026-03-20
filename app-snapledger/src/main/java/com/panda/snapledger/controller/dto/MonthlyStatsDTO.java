package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Monthly statistics DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyStatsDTO {

    private int year;
    private int month;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal balance;

    // Category breakdown
    private List<CategoryStatsDTO> categoryStats;
}
