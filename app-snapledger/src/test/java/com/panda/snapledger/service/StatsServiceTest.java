package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.MonthlyStatsDTO;
import com.panda.snapledger.controller.dto.CategoryStatsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StatsServiceTest {

    @Autowired
    private StatsService statsService;

    @Test
    void testGetMonthlyStats() {
        MonthlyStatsDTO stats = statsService.getMonthlyStats(2024, 3);

        assertNotNull(stats);
        assertEquals(2024, stats.getYear());
        assertEquals(3, stats.getMonth());
        assertNotNull(stats.getTotalIncome());
        assertNotNull(stats.getTotalExpense());
        assertNotNull(stats.getCategoryStats());
    }

    @Test
    void testGetCategoryStats() {
        List<CategoryStatsDTO> stats = statsService.getCategoryStats(2024, 3, "expense");

        assertNotNull(stats);
        // Should be sorted by amount descending
        if (stats.size() >= 2) {
            assertTrue(stats.get(0).getAmount().compareTo(stats.get(1).getAmount()) >= 0);
        }
    }
}
