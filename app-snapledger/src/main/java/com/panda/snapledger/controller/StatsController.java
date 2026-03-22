package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.MonthlyStatsDTO;
import com.panda.snapledger.controller.dto.CategoryStatsDTO;
import com.panda.snapledger.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Statistics controller.
 */
@RestController
@RequestMapping("/api/snapledger/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Get monthly statistics.
     */
    @GetMapping("/monthly/{year}/{month}")
    @Operation(summary = "获取月度统计")
    public ResponseEntity<MonthlyStatsDTO> getMonthlyStats(
            @PathVariable int year,
            @PathVariable int month) {
        MonthlyStatsDTO stats = statsService.getMonthlyStats(year, month);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get category statistics for specified type.
     */
    @GetMapping("/category/{year}/{month}/{type}")
    @Operation(summary = "获取分类统计")
    public ResponseEntity<List<CategoryStatsDTO>> getCategoryStats(
            @PathVariable int year,
            @PathVariable int month,
            @PathVariable String type) {
        List<CategoryStatsDTO> stats = statsService.getCategoryStats(year, month, type);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get yearly statistics (all months).
     */
    @GetMapping("/yearly/{year}")
    @Operation(summary = "获取年度统计")
    public ResponseEntity<List<MonthlyStatsDTO>> getYearlyStats(@PathVariable int year) {
        List<MonthlyStatsDTO> stats = statsService.getYearlyStats(year);
        return ResponseEntity.ok(stats);
    }
}
