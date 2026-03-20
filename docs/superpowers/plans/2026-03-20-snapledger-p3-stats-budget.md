# SnapLedger P3: 统计预算 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement statistics and budget features - show monthly/yearly spending reports with charts, and set monthly budget with overbudget warning.

**Architecture:** Add StatsController and BudgetController with required APIs. Create Stats.vue and Budget.vue frontend pages with chart visualization.

**Tech Stack:** Java 17, Spring Boot 3.2, Vue 3, Vant 4, Chart.js

---

## File Structure

```
app-snapledger/src/main/java/com/panda/snapledger/
├── controller/
│   ├── StatsController.java       # Statistics endpoints
│   └── BudgetController.java      # Budget endpoints
├── service/
│   ├── StatsService.java          # Statistics calculation
│   └── BudgetService.java         # Budget operations
├── controller/dto/
│   ├── MonthlyStatsDTO.java       # Monthly stats response
│   ├── YearlyStatsDTO.java        # Yearly stats response
│   ├── CategoryStatsDTO.java      # Category stats
│   └── BudgetDTO.java             # Budget data

frontend/src/
├── views/snapledger/
│   ├── Stats.vue                  # Stats page with charts
│   └── Budget.vue                 # Budget setting page
└── api/snapledger/
    ├── stats.js                   # Stats API functions
    └── budget.js                  # Budget API functions
```

---

### Task 1: Create DTOs for Stats and Budget

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/MonthlyStatsDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CategoryStatsDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BudgetDTO.java`

- [x] **Step 1: Create MonthlyStatsDTO**

```java
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
```

- [x] **Step 2: Create CategoryStatsDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Category statistics DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatsDTO {

    private String categoryName;
    private BigDecimal amount;
    private BigDecimal percentage;
    private String type; // income/expense
}
```

- [x] **Step 3: Create BudgetDTO**

```java
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
```

- [x] **Step 4: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/*Stats*.java app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BudgetDTO.java
git commit -m "feat(snapledger): add DTOs for stats and budget features"
```

---

### Task 2: Create StatsService

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/StatsService.java`
- Create: `app-snapledger/src/test/java/com/panda/snapledger/service/StatsServiceTest.java`

- [x] **Step 1: Write failing test**

```java
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
```

- [x] **Step 2: Run test to verify it fails**

```bash
cd app-snapledger && mvn test -Dtest=StatsServiceTest -q
```
Expected: FAIL - class not found

- [x] **Step 3: Create StatsService**

```java
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
            BigDecimal amount = record.getAmount();
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
            BigDecimal amount = record.getAmount();
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
```

- [x] **Step 4: Add query method to RecordRepository**

Make sure `RecordRepository` has:
```java
List<Record> findByYearAndMonth(int year, int month);
List<Record> findByYearAndMonthAndType(int year, int month, String type);
```

- [x] **Step 5: Run test to verify it passes**

```bash
cd app-snapledger && mvn test -Dtest=StatsServiceTest -q
```
Expected: PASS

- [x] **Step 6: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/StatsService.java app-snapledger/src/test/java/com/panda/snapledger/service/StatsServiceTest.java
git commit -m "feat(snapledger): add StatsService for statistics calculation

- Calculate monthly stats (total income/expense/balance)
- Calculate category breakdown sorted by amount
- Support yearly stats aggregation"
```

---

### Task 3: Create BudgetService

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/BudgetService.java`
- Create: `app-snapledger/src/test/java/com/panda/snapledger/service/BudgetServiceTest.java`

- [x] **Step 1: Write failing test**

```java
package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.BudgetDTO;
import com.panda.snapledger.domain.Budget;
import com.panda.snapledger.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
        Budget budget = budgetRepository.findByYearAndMonth(2024, 3);
        assertNotNull(budget);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

- [x] **Step 3: Create BudgetService**

```java
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
        Budget budget = budgetRepository.findByYearAndMonth(year, month);

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
        Budget budget = budgetRepository.findByYearAndMonth(year, month);

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
```

- [x] **Step 4: Add query method to RecordRepository and BudgetRepository**

`BudgetRepository`:
```java
Budget findByYearAndMonth(int year, int month);
```

`RecordRepository`:
```java
BigDecimal sumExpenseByMonth(int year, int month);
```

- [x] **Step 5: Run test to verify it passes**

```bash
cd app-snapledger && mvn test -Dtest=BudgetServiceTest -q
```
Expected: PASS

- [x] **Step 6: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/BudgetService.java app-snapledger/src/test/java/com/panda/snapledger/service/BudgetServiceTest.java
git commit -m "feat(snapledger): add BudgetService for budget management

- Get budget with spent/remaining calculation
- Set/update budget for a month
- Over budget detection"
```

---

### Task 4: Create Controllers

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/StatsController.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/BudgetController.java`

- [x] **Step 1: Create StatsController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.MonthlyStatsDTO;
import com.panda.snapledger.controller.dto.CategoryStatsDTO;
import com.panda.snapledger.service.StatsService;
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
    public ResponseEntity<List<MonthlyStatsDTO>> getYearlyStats(@PathVariable int year) {
        List<MonthlyStatsDTO> stats = statsService.getYearlyStats(year);
        return ResponseEntity.ok(stats);
    }
}
```

- [x] **Step 2: Create BudgetController**

```java
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
```

- [x] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/StatsController.java app-snapledger/src/main/java/com/panda/snapledger/controller/BudgetController.java
git commit -m "feat(snapledger): add StatsController and BudgetController

- GET /api/snapledger/stats/monthly/{year}/{month} - monthly stats
- GET /api/snapledger/stats/category/{year}/{month}/{type} - category stats
- GET /api/snapledger/stats/yearly/{year} - yearly stats
- GET /api/snapledger/budget/{year}/{month} - get budget
- POST /api/snapledger/budget - set budget"
```

---

### Task 5: Create Frontend API Modules

**Files:**
- Create: `frontend/src/api/snapledger/stats.js`
- Create: `frontend/src/api/snapledger/budget.js`

- [x] **Step 1: Create stats.js**

```javascript
import api from '../index'

/**
 * Get monthly statistics
 * @param {number} year
 * @param {number} month
 * @returns {Promise} Monthly stats with category breakdown
 */
export function getMonthlyStats(year, month) {
  return api.get(`/snapledger/stats/monthly/${year}/${month}`)
}

/**
 * Get category statistics
 * @param {number} year
 * @param {number} month
 * @param {string} type - 'income' or 'expense'
 * @returns {Promise} Category stats list
 */
export function getCategoryStats(year, month, type) {
  return api.get(`/snapledger/stats/category/${year}/${month}/${type}`)
}

/**
 * Get yearly statistics
 * @param {number} year
 * @returns {Promise} List of monthly stats
 */
export function getYearlyStats(year) {
  return api.get(`/snapledger/stats/yearly/${year}`)
}
```

- [x] **Step 2: Create budget.js**

```javascript
import api from '../index'

/**
 * Get budget for specified month
 * @param {number} year
 * @param {number} month
 * @returns {Promise} Budget data with spent/remaining
 */
export function getBudget(year, month) {
  return api.get(`/snapledger/budget/${year}/${month}`)
}

/**
 * Set or update budget
 * @param {Object} data - { year, month, amount }
 * @returns {Promise} Updated budget
 */
export function setBudget(data) {
  return api.post('/snapledger/budget', data)
}
```

- [x] **Step 3: Commit**

```bash
git add frontend/src/api/snapledger/stats.js frontend/src/api/snapledger/budget.js
git commit -m "feat(frontend): add stats and budget API modules"
```

---

### Task 6: Create Stats.vue Page

**Files:**
- Create: `frontend/src/views/snapledger/Stats.vue`
- Modify: `frontend/src/router.js` (add route)

- [x] **Step 1: Create Stats.vue**

```vue
<template>
  <div class="stats-page">
    <van-nav-bar title="统计报表" left-arrow @click-left="$router.back()" />

    <div class="stats-content">
      <!-- Year/Month selector -->
      <div class="month-selector">
        <van-field
          v-model="currentDate"
          is-link
          readonly
          label="月份"
          placeholder="选择月份"
          @click="showMonthPicker = true"
        />
      </div>

      <!-- Overview card -->
      <van-card class="overview-card">
        <div class="overview-row">
          <div class="overview-item income">
            <div class="label">收入</div>
            <div class="value">+¥{{ (stats.totalIncome || 0).toFixed(2) }}</div>
          </div>
          <div class="overview-item expense">
            <div class="label">支出</div>
            <div class="value">-¥{{ (stats.totalExpense || 0).toFixed(2) }}</div>
          </div>
        </div>
        <div class="overview-row balance">
          <div class="label">结余</div>
          <div class="value" :class="{ 'positive': stats.balance >= 0, 'negative': stats.balance < 0 }">
            {{ stats.balance > 0 ? '+' : '' }}¥{{ (stats.balance || 0).toFixed(2) }}
          </div>
        </div>
      </van-card>

      <!-- Budget info if exists -->
      <div v-if="budget && budget.amount > 0" class="budget-card">
        <van-cell-group inset>
          <van-cell title="本月预算">
            <template #right-icon>
              <span class="budget-amount">¥{{ budget.amount.toFixed(2) }}</span>
            </template>
          </van-cell>
          <van-cell title="已支出">
            <template #right-icon>
              <span>¥{{ budget.spent.toFixed(2) }}</span>
            </template>
          </van-cell>
          <van-cell title="剩余">
            <template #right-icon>
              <span :class="{ 'over-budget': budget.overBudget, 'remaining': !budget.overBudget }">
                ¥{{ budget.remaining.toFixed(2) }}
              </span>
            </template>
          </van-cell>
        </van-cell-group>
        <div v-if="budget.overBudget" class="over-budget-notice">
          <van-notice-bar color="#ee0a24" background="#fff1f0" left-icon="warning-o" text="⚠️ 已超出本月预算" />
        </div>
      </div>

      <!-- Expense category pie chart -->
      <div v-if="stats.categoryStats && stats.categoryStats.length > 0" class="chart-card">
        <h3 class="chart-title">支出分类占比</h3>
        <div class="chart-container">
          <canvas id="categoryChart" width="300" height="300"></canvas>
        </div>

        <van-cell-group inset class="category-list">
          <van-cell
            v-for="cat in stats.categoryStats"
            :key="cat.categoryName"
            :title="cat.categoryName"
          >
            <template #right-icon>
              <span class="category-amount">¥{{ cat.amount.toFixed(2) }} ({{ cat.percentage.toFixed(1) }}%)</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>

    <!-- Month Picker -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        title="选择月份"
        type="month"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Chart } from 'chart.js'
import { getMonthlyStats } from '@/api/snapledger/stats'
import { getBudget } from '@/api/snapledger/budget'
import { showToast } from 'vant'

const route = useRoute()
const showMonthPicker = ref(false)

const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const currentDate = computed(() => {
  return `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`
})
const selectedDate = ref([currentYear.value.toString(), currentMonth.value.toString()])

const stats = ref({
  totalIncome: 0,
  totalExpense: 0,
  balance: 0,
  categoryStats: []
})

const budget = ref(null)
let categoryChart = null

async function loadStats() {
  try {
    const res = await getMonthlyStats(currentYear.value, currentMonth.value)
    stats.value = res.data || {
      totalIncome: 0,
      totalExpense: 0,
      balance: 0,
      categoryStats: []
    }
    renderChart()
  } catch (e) {
    showToast('加载统计失败: ' + e.message)
  }
}

async function loadBudget() {
  try {
    const res = await getBudget(currentYear.value, currentMonth.value)
    budget.value = res.data
  } catch (e) {
    console.error('Failed to load budget:', e)
  }
}

function renderChart() {
  const canvas = document.getElementById('categoryChart')
  if (!canvas || !stats.value.categoryStats || stats.value.categoryStats.length === 0) {
    return
  }

  if (categoryChart) {
    categoryChart.destroy()
  }

  const labels = stats.value.categoryStats.map(c => c.categoryName)
  const data = stats.value.categoryStats.map(c => c.amount.toNumber())
  const colors = [
    '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF',
    '#FF9F40', '#FF6384', '#C9CBCF', '#4BC0C0', '#36A2EB'
  ]

  categoryChart = new Chart(canvas, {
    type: 'pie',
    data: {
      labels: labels,
      datasets: [{
        data: data,
        backgroundColor: colors,
        hoverBackgroundColor: colors
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom'
        }
      }
    }
  })
}

function onMonthConfirm({ selectedValues }) {
  currentYear.value = parseInt(selectedValues[0])
  currentMonth.value = parseInt(selectedValues[1])
  showMonthPicker.value = false
  loadStats()
  loadBudget()
}

onMounted(() => {
  const year = parseInt(route.query.year)
  const month = parseInt(route.query.month)
  if (year && month) {
    currentYear.value = year
    currentMonth.value = month
    selectedDate.value = [year.toString(), month.toString()]
  }
  loadStats()
  loadBudget()
})
</script>

<style scoped>
.stats-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.stats-content {
  padding: 16px;
}

.month-selector {
  margin-bottom: 16px;
}

.overview-card {
  margin-bottom: 16px;
}

.overview-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.overview-row:last-child {
  margin-bottom: 0;
  padding-top: 12px;
  border-top: 1px solid #eee;
}

.overview-item {
  flex: 1;
  text-align: center;
}

.overview-item .label {
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}

.overview-item .value {
  font-size: 18px;
  font-weight: bold;
}

.overview-item.income .value {
  color: #07c160;
}

.overview-item.expense .value {
  color: #ee0a24;
}

.balance .value.positive {
  color: #07c160;
}

.balance .value.negative {
  color: #ee0a24;
}

.budget-card {
  margin-bottom: 16px;
}

.budget-amount {
  color: #323233;
  font-weight: 500;
}

.over-budget {
  color: #ee0a24;
  font-weight: 500;
}

.remaining {
  color: #07c160;
  font-weight: 500;
}

.over-budget-notice {
  margin-top: 8px;
}

.chart-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.chart-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 500;
  color: #323233;
}

.chart-container {
  height: 300px;
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.category-list {
  margin-top: 8px;
}

.category-amount {
  color: #646566;
}
</style>
```

- [x] **Step 2: Add route to router.js**

Add route after SnapScan:
```javascript
import SnapStats from '@/views/snapledger/Stats.vue'

{
  path: '/snap/stats',
  name: 'SnapStats',
  component: SnapStats,
  meta: { module: 'snapledger', transition: 'page-fade' }
},
```

- [x] **Step 3: Add Chart.js dependency to package.json** (if not already present)

- [x] **Step 4: Commit**

```bash
git add frontend/src/views/snapledger/Stats.vue frontend/src/router.js
git commit -m "feat(frontend): add Stats.vue page with pie chart

- Monthly overview (income/expense/balance)
- Expense category pie chart using Chart.js
- Budget progress display with over budget warning
- Month picker for browsing"
```

---

### Task 7: Create Budget.vue Page

**Files:**
- Create: `frontend/src/views/snapledger/Budget.vue`
- Modify: `frontend/src/router.js` (add route)

- [x] **Step 1: Create Budget.vue**

```vue
<template>
  <div class="budget-page">
    <van-nav-bar title="预算设置" left-arrow @click-left="$router.back()" />

    <div class="budget-content">
      <!-- Month selector -->
      <div class="month-selector">
        <van-field
          v-model="currentDate"
          is-link
          readonly
          label="月份"
          placeholder="选择月份"
          @click="showMonthPicker = true"
        />
      </div>

      <!-- Current budget info -->
      <van-card class="budget-card">
        <div class="budget-info" v-if="budget">
          <div class="info-row">
            <span class="label">预算金额</span>
            <van-field
              v-model.number="budget.amount"
              type="number"
              placeholder="0.00"
              :border="false"
            />
          </div>
          <div class="info-row">
            <span class="label">已支出</span>
            <span class="value">¥{{ (budget.spent || 0).toFixed(2) }}</span>
          </div>
          <div class="info-row remaining">
            <span class="label">剩余</span>
            <span class="value" :class="{ 'over-budget': budget.overBudget, 'remaining': !budget.overBudget }">
              ¥{{ (budget.remaining || 0).toFixed(2) }}
            </span>
          </div>
        </div>
      </van-card>

      <!-- Usage progress bar -->
      <div v-if="budget && budget.amount > 0" class="progress-card">
        <div class="progress-label">
          <span>预算使用进度</span>
          <span>{{ progressPercent.toFixed(1) }}%</span>
        </div>
        <van-progress
          :percentage="progressPercent"
          :color="progressColor"
          :stroke-width="8"
        />
      </div>

      <div v-if="budget && budget.overBudget" class="over-notice">
        <van-notice-bar
          color="#ee0a24"
          background="#fff1f0"
          left-icon="warning-o"
          text="⚠️ 当前已超出预算，请控制消费"
        />
      </div>

      <!-- Save button -->
      <div class="action-button">
        <van-button type="primary" block @click="handleSave" :loading="saving">
          保存预算
        </van-button>
      </div>
    </div>

    <!-- Month Picker -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        title="选择月份"
        type="month"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getBudget, setBudget } from '@/api/snapledger/budget'
import { showToast, showSuccessToast } from 'vant'

const route = useRoute()
const showMonthPicker = ref(false)
const saving = ref(false)

const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const currentDate = computed(() => {
  return `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`
})
const selectedDate = ref([currentYear.value.toString(), currentMonth.value.toString()])

const budget = ref(null)

const progressPercent = computed(() => {
  if (!budget.value || !budget.value.amount || budget.value.amount === 0) {
    return 0
  }
  const percent = (budget.value.spent / budget.value.amount) * 100
  return Math.min(percent, 100)
})

const progressColor = computed(() => {
  if (!budget.value) return '#1989fa'
  if (budget.value.overBudget) return '#ee0a24'
  if (progressPercent.value > 80) return '#ff976a'
  return '#1989fa'
})

async function loadBudget() {
  try {
    const res = await getBudget(currentYear.value, currentMonth.value)
    budget.value = res.data
  } catch (e) {
    showToast('加载预算失败: ' + e.message)
  }
}

async function handleSave() {
  if (!budget.value || !budget.value.amount || budget.value.amount <= 0) {
    showToast('请输入有效的预算金额')
    return
  }

  saving.value = true
  try {
    await setBudget({
      year: currentYear.value,
      month: currentMonth.value,
      amount: budget.value.amount
    })
    await loadBudget()
    showSuccessToast('保存成功')
  } catch (e) {
    showToast('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function onMonthConfirm({ selectedValues }) {
  currentYear.value = parseInt(selectedValues[0])
  currentMonth.value = parseInt(selectedValues[1])
  selectedDate.value = [currentYear.value.toString(), currentMonth.value.toString()]
  showMonthPicker.value = false
  loadBudget()
}

onMounted(() => {
  const year = parseInt(route.query.year)
  const month = parseInt(route.query.month)
  if (year && month) {
    currentYear.value = year
    currentMonth.value = month
    selectedDate.value = [year.toString(), month.toString()]
  }
  loadBudget()
})
</script>

<style scoped>
.budget-page {
  min-height: 100vh;
  background: #f7f8fa;
}

.budget-content {
  padding: 16px;
}

.month-selector {
  margin-bottom: 16px;
}

.budget-card {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row .label {
  color: #646566;
  width: 80px;
}

.info-row .value {
  flex: 1;
  text-align: right;
  color: #323233;
  font-weight: 500;
}

.remaining .value.over-budget {
  color: #ee0a24;
  font-weight: 500;
}

.remaining .value.remaining {
  color: #07c160;
  font-weight: 500;
}

.progress-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #323233;
}

.over-notice {
  margin-bottom: 16px;
}

.action-button {
  padding: 0 16px;
}
</style>
```

- [x] **Step 2: Add route to router.js**

Add route after Stats:
```javascript
import SnapBudget from '@/views/snapledger/Budget.vue'

{
  path: '/snap/budget',
  name: 'SnapBudget',
  component: SnapBudget,
  meta: { module: 'snapledger', transition: 'page-fade' }
},
```

- [x] **Step 3: Add Budget navigation entry to Home.vue footer tabbar**

- [x] **Step 4: Commit**

```bash
git add frontend/src/views/snapledger/Budget.vue frontend/src/router.js frontend/src/views/snapledger/Home.vue
git commit -m "feat(frontend): add Budget.vue page for budget management

- Set/update monthly budget
- Show usage progress bar with color change
- Over budget warning
- Add budget entry to home footer navigation"
```

---

### Task 8: Update Navigation and Add Chart.js

**Files:**
- Modify: `frontend/src/views/snapledger/Home.vue` (update tabbar)
- Check and update: `frontend/package.json` (add Chart.js if needed)

- [x] **Step 1: Add stats and budget to Home.vue tabbar**

Update the van-tabbar in Home.vue to include:
```vue
<van-tabbar v-model="activeTab">
  <van-tabbar-item icon="home-o" to="/snap">首页</van-tabbar-item>
  <van-tabbar-item icon="calendar-o" to="/snap/calendar">日历</van-tabbar-item>
  <van-tabbar-item icon="bar-chart-o" to="/snap/stats">统计</van-tabbar-item>
  <van-tabbar-item icon="setting-o" to="/snap/budget">预算</van-tabbar-item>
</van-tabbar>
```

- [x] **Step 2: Install Chart.js if needed**

```bash
cd frontend && npm install chart.js
```

- [x] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/Home.vue frontend/package.json
git commit -m "feat(frontend): add stats and budget to home navigation, add chart.js dependency"
```

---

### Task 9: Integration Test

**Files:**
- Run tests and build

- [x] **Step 1: Run all backend tests**

```bash
cd app-snapledger && mvn test
```
Expected: All tests pass

- [x] **Step 2: Run frontend build**

```bash
cd frontend && npm run build
```
Expected: Build succeeds

- [x] **Step 3: Commit if any changes**

---

## Summary

This plan implements P3 统计预算 feature:

1. **Task 1**: Create DTOs (MonthlyStatsDTO, CategoryStatsDTO, BudgetDTO)
2. **Task 2**: Create StatsService with statistics calculation
3. **Task 3**: Create BudgetService with budget management
4. **Task 4**: Create StatsController and BudgetController
5. **Task 5**: Create frontend API modules (stats.js, budget.js)
6. **Task 6**: Create Stats.vue page with pie chart
7. **Task 7**: Create Budget.vue page for budget setting
8. **Task 8**: Update navigation and add Chart.js dependency
9. **Task 9**: Integration testing
