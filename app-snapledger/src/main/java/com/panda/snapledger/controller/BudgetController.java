package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.BudgetDTO;
import com.panda.snapledger.service.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Budget controller.
 */
@RestController
@RequestMapping("/api/snapledger/budget")
@CrossOrigin(origins = "*")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /**
     * Get budget for specified month.
     */
    @GetMapping("/{year}/{month}")
    public ResponseEntity<BudgetDTO> getBudget(
            @PathVariable int year,
            @PathVariable int month) {
        BudgetDTO budget = budgetService.getBudget(year, month);
        return ResponseEntity.ok(budget);
    }

    /**
     * Set or update budget.
     */
    @PostMapping
    public ResponseEntity<BudgetDTO> setBudget(@RequestBody BudgetDTO request) {
        BudgetDTO budget = budgetService.setBudget(
                request.getYear(),
                request.getMonth(),
                request.getAmount()
        );
        return ResponseEntity.ok(budget);
    }
}
