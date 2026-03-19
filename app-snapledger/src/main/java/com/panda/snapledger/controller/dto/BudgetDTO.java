package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Budget DTO for a specific month.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDTO {

    private Long id;
    private int year;
    private int month;
    private BigDecimal amount;
    private BigDecimal spent;      // How much already spent
    private BigDecimal remaining;  // Remaining budget
    private boolean overBudget;    // Whether over budget
}
