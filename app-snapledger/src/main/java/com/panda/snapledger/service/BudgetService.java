package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.BudgetDTO;
import com.panda.snapledger.domain.Budget;
import com.panda.snapledger.repository.BudgetRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Budget service - manage monthly budget settings.
 */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final RecordRepository recordRepository;

    public BudgetService(BudgetRepository budgetRepository, RecordRepository recordRepository) {
        this.budgetRepository = budgetRepository;
        this.recordRepository = recordRepository;
    }

    /**
     * Get budget for specified month, with calculated spent/remaining.
     */
    public BudgetDTO getBudget(int year, int month) {
        Budget budget = budgetRepository.findByYearAndMonth(year, month).orElse(null);

        // Calculate total spent this month
        BigDecimal spent = recordRepository.sumExpenseByMonth(year, month);
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        if (budget == null) {
            return BudgetDTO.builder()
                    .year(year)
                    .month(month)
                    .amount(BigDecimal.ZERO)
                    .spent(spent)
                    .remaining(BigDecimal.ZERO.subtract(spent))
                    .overBudget(false)
                    .build();
        }

        BigDecimal remaining = budget.getAmount().subtract(spent);
        boolean overBudget = remaining.compareTo(BigDecimal.ZERO) < 0;

        return BudgetDTO.builder()
                .id(budget.getId())
                .year(year)
                .month(month)
                .amount(budget.getAmount())
                .spent(spent)
                .remaining(remaining)
                .overBudget(overBudget)
                .build();
    }

    /**
     * Set or update budget for specified month.
     */
    public BudgetDTO setBudget(int year, int month, BigDecimal amount) {
        Budget budget = budgetRepository.findByYearAndMonth(year, month).orElse(null);

        if (budget == null) {
            budget = new Budget();
            budget.setYear(year);
            budget.setMonth(month);
            budget.setAmount(amount);
        } else {
            budget.setAmount(amount);
        }

        budget = budgetRepository.save(budget);
        return getBudget(year, month);
    }
}
