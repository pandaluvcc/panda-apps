package com.panda.snapledger.service;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.BudgetDTO;
import com.panda.snapledger.domain.Budget;
import com.panda.snapledger.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PandaApplication.class)
class BudgetServiceTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    void testGetOrCreateBudget() {
        BudgetDTO budget = budgetService.getBudget(2024, 3);

        assertNotNull(budget);
        assertEquals(2024, budget.getYear());
        assertEquals(3, budget.getMonth());
    }

    @Test
    void testSetBudget() {
        BudgetDTO saved = budgetService.setBudget(2024, 3, new BigDecimal("3000.00"));

        assertEquals(2024, saved.getYear());
        assertEquals(3, saved.getMonth());
        assertEquals(new BigDecimal("3000.00"), saved.getAmount());

        // Verify it's in the database
        Budget budget = budgetRepository.findByYearAndMonth(2024, 3).orElse(null);
        assertNotNull(budget);
    }
}
