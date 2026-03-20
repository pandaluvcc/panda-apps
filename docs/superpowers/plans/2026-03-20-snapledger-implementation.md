# SnapLedger 实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现个人记账应用核心功能，支持手动记账、日历视图、CSV数据导入

**Architecture:** 后端使用 Spring Boot 3.2 + JPA，前端使用 Vue 3 + Vant 4。数据模型对应 moze 导出的 16 个字段。采用分层架构：Controller → Service → Repository → Domain。

**Tech Stack:** Java 17, Spring Boot 3.2, JPA/Hibernate, MySQL, Vue 3, Vant 4, Axios

---

## 文件结构

### 后端文件（app-snapledger 模块）

| 文件 | 职责 |
|------|------|
| `domain/Record.java` | 记账记录实体，对应 moze 16 字段 |
| `domain/Category.java` | 分类实体 |
| `domain/Account.java` | 账户实体 |
| `domain/Budget.java` | 预算实体 |
| `repository/RecordRepository.java` | 记录数据访问 |
| `repository/CategoryRepository.java` | 分类数据访问 |
| `repository/AccountRepository.java` | 账户数据访问 |
| `repository/BudgetRepository.java` | 预算数据访问 |
| `controller/dto/RecordDTO.java` | 记录传输对象 |
| `controller/dto/CalendarDayDTO.java` | 日历日数据 |
| `controller/dto/CalendarMonthDTO.java` | 日历月数据 |
| `controller/RecordController.java` | 记录 CRUD API |
| `controller/CalendarController.java` | 日历视图 API |
| `controller/CategoryController.java` | 分类 API |
| `controller/AccountController.java` | 账户 API |
| `controller/ImportController.java` | CSV 导入 API |
| `service/RecordService.java` | 记录业务逻辑 |
| `service/CalendarService.java` | 日历业务逻辑 |
| `service/import/MozeCsvImporter.java` | CSV 导入解析 |

### 前端文件

| 文件 | 职责 |
|------|------|
| `api/snapledger/record.js` | 记录 API |
| `api/snapledger/calendar.js` | 日历 API |
| `api/snapledger/category.js` | 分类 API |
| `api/snapledger/account.js` | 账户 API |
| `api/snapledger/import.js` | 导入 API |
| `views/snapledger/Home.vue` | 记账首页 |
| `views/snapledger/AddRecord.vue` | 手动记账页 |
| `views/snapledger/Calendar.vue` | 日历视图页 |
| `views/snapledger/Import.vue` | 数据导入页 |
| `components/snapledger/RecordForm.vue` | 记账表单组件 |
| `components/snapledger/RecordList.vue` | 记录列表组件 |
| `components/snapledger/CalendarGrid.vue` | 日历网格组件 |
| `components/snapledger/CategoryPicker.vue` | 分类选择器 |

---

## Task 1: 数据库表创建

**Files:**
- Create: `docs/superpowers/sql/snapledger-schema.sql`

- [ ] **Step 1: 创建 SQL 脚本**

```sql
-- 记账记录表
CREATE TABLE IF NOT EXISTS sl_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account VARCHAR(50),
    currency VARCHAR(10) DEFAULT 'CNY',
    record_type VARCHAR(20),
    main_category VARCHAR(50),
    sub_category VARCHAR(50),
    amount DECIMAL(12, 2) NOT NULL,
    fee DECIMAL(12, 2) DEFAULT 0,
    discount DECIMAL(12, 2) DEFAULT 0,
    name VARCHAR(100),
    merchant VARCHAR(100),
    date DATE NOT NULL,
    time TIME,
    project VARCHAR(50),
    description VARCHAR(500),
    tags VARCHAR(200),
    target VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_main_category (main_category),
    INDEX idx_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类表
CREATE TABLE IF NOT EXISTS sl_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    main_category VARCHAR(50) NOT NULL,
    sub_category VARCHAR(50),
    type VARCHAR(20) NOT NULL,
    icon VARCHAR(50),
    UNIQUE KEY uk_category (main_category, sub_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 账户表
CREATE TABLE IF NOT EXISTS sl_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20),
    balance DECIMAL(12, 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预算表
CREATE TABLE IF NOT EXISTS sl_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    year INT NOT NULL,
    month INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_year_month (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 执行 SQL 创建表**

Run: 在 MySQL 中执行上述 SQL 脚本

---

## Task 2: 后端 Domain 实体

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/domain/Category.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/domain/Account.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/domain/Budget.java`

- [ ] **Step 1: 创建 Record 实体**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 记账记录实体
 */
@Entity
@Table(name = "sl_record")
@Data
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account", length = 50)
    private String account;

    @Column(name = "currency", length = 10)
    private String currency = "CNY";

    @Column(name = "record_type", length = 20)
    private String recordType;

    @Column(name = "main_category", length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee", precision = 12, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "time")
    private LocalTime time;

    @Column(name = "project", length = 50)
    private String project;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "tags", length = 200)
    private String tags;

    @Column(name = "target", length = 50)
    private String target;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: 创建 Category 实体**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 分类实体
 */
@Entity
@Table(name = "sl_category")
@Data
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "main_category", nullable = false, length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "icon", length = 50)
    private String icon;
}
```

- [ ] **Step 3: 创建 Account 实体**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户实体
 */
@Entity
@Table(name = "sl_account")
@Data
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "type", length = 20)
    private String type;

    @Column(name = "balance", precision = 12, scale = 2)
    private BigDecimal balance;
}
```

- [ ] **Step 4: 创建 Budget 实体**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算实体
 */
@Entity
@Table(name = "sl_budget")
@Data
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/
git commit -m "feat(snapledger): add domain entities (Record, Category, Account, Budget)"
```

---

## Task 3: 后端 Repository 层

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/repository/CategoryRepository.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/repository/AccountRepository.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/repository/BudgetRepository.java`

- [ ] **Step 1: 创建 RecordRepository**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {

    List<Record> findByDateOrderByTimeDesc(LocalDate date);

    List<Record> findByDateBetweenOrderByDateDescTimeDesc(LocalDate start, LocalDate end);

    @Query("SELECT r FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month ORDER BY r.date DESC, r.time DESC")
    List<Record> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(r.amount) FROM Record r WHERE r.date = :date AND r.recordType = :recordType")
    BigDecimal sumAmountByDateAndRecordType(@Param("date") LocalDate date, @Param("recordType") String recordType);

    @Query("SELECT SUM(r.amount) FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month AND r.recordType = :recordType")
    BigDecimal sumAmountByYearMonthAndRecordType(@Param("year") int year, @Param("month") int month, @Param("recordType") String recordType);

    @Query("SELECT DISTINCT r.mainCategory FROM Record r WHERE r.recordType = :recordType")
    List<String> findDistinctMainCategoriesByRecordType(@Param("recordType") String recordType);

    @Query("SELECT DISTINCT r.subCategory FROM Record r WHERE r.mainCategory = :mainCategory")
    List<String> findDistinctSubCategoriesByMainCategory(@Param("mainCategory") String mainCategory);
}
```

- [ ] **Step 2: 创建 CategoryRepository**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByType(String type);

    List<Category> findByMainCategory(String mainCategory);

    Category findByMainCategoryAndSubCategory(String mainCategory, String subCategory);
}
```

- [ ] **Step 3: 创建 AccountRepository**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByName(String name);

    boolean existsByName(String name);
}
```

- [ ] **Step 4: 创建 BudgetRepository**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByYearAndMonth(Integer year, Integer month);
}
```

- [ ] **Step 5: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/
git commit -m "feat(snapledger): add repository interfaces"
```

---

## Task 4: 后端 DTO 层

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecordDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CalendarDayDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CalendarMonthDTO.java`

- [ ] **Step 1: 创建 RecordDTO**

```java
package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RecordDTO {

    private Long id;
    private String account;
    private String currency;
    private String recordType;
    private String mainCategory;
    private String subCategory;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal discount;
    private String name;
    private String merchant;
    private LocalDate date;
    private LocalTime time;
    private String project;
    private String description;
    private String tags;
    private String target;

    public static RecordDTO fromEntity(Record record) {
        RecordDTO dto = new RecordDTO();
        dto.setId(record.getId());
        dto.setAccount(record.getAccount());
        dto.setCurrency(record.getCurrency());
        dto.setRecordType(record.getRecordType());
        dto.setMainCategory(record.getMainCategory());
        dto.setSubCategory(record.getSubCategory());
        dto.setAmount(record.getAmount());
        dto.setFee(record.getFee());
        dto.setDiscount(record.getDiscount());
        dto.setName(record.getName());
        dto.setMerchant(record.getMerchant());
        dto.setDate(record.getDate());
        dto.setTime(record.getTime());
        dto.setProject(record.getProject());
        dto.setDescription(record.getDescription());
        dto.setTags(record.getTags());
        dto.setTarget(record.getTarget());
        return dto;
    }

    public Record toEntity() {
        Record record = new Record();
        record.setId(this.id);
        record.setAccount(this.account);
        record.setCurrency(this.currency != null ? this.currency : "CNY");
        record.setRecordType(this.recordType);
        record.setMainCategory(this.mainCategory);
        record.setSubCategory(this.subCategory);
        record.setAmount(this.amount);
        record.setFee(this.fee != null ? this.fee : BigDecimal.ZERO);
        record.setDiscount(this.discount != null ? this.discount : BigDecimal.ZERO);
        record.setName(this.name);
        record.setMerchant(this.merchant);
        record.setDate(this.date);
        record.setTime(this.time);
        record.setProject(this.project);
        record.setDescription(this.description);
        record.setTags(this.tags);
        record.setTarget(this.target);
        return record;
    }
}
```

- [ ] **Step 2: 创建 CalendarDayDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayDTO {

    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expense;
    private int recordCount;

    public BigDecimal getBalance() {
        BigDecimal inc = income != null ? income : BigDecimal.ZERO;
        BigDecimal exp = expense != null ? expense : BigDecimal.ZERO;
        return inc.subtract(exp);
    }
}
```

- [ ] **Step 3: 创建 CalendarMonthDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthDTO {

    private int year;
    private int month;
    private List<CalendarDayDTO> days;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;

    public BigDecimal getTotalBalance() {
        BigDecimal inc = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        BigDecimal exp = totalExpense != null ? totalExpense : BigDecimal.ZERO;
        return inc.subtract(exp);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/
git commit -m "feat(snapledger): add DTOs (RecordDTO, CalendarDayDTO, CalendarMonthDTO)"
```

---

## Task 5: 后端 Service 层

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/RecordService.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/CalendarService.java`

- [ ] **Step 1: 创建 RecordService**

```java
package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecordService {

    private final RecordRepository recordRepository;

    public RecordService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<RecordDTO> findByDate(LocalDate date) {
        return recordRepository.findByDateOrderByTimeDesc(date).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<RecordDTO> findByYearMonth(int year, int month) {
        return recordRepository.findByYearAndMonth(year, month).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<RecordDTO> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending().and(Sort.by("time").descending()));
        return recordRepository.findAll(pageable).map(RecordDTO::fromEntity);
    }

    @Transactional
    public RecordDTO create(RecordDTO dto) {
        Record record = dto.toEntity();
        if (record.getDate() == null) {
            record.setDate(LocalDate.now());
        }
        Record saved = recordRepository.save(record);
        return RecordDTO.fromEntity(saved);
    }

    @Transactional
    public RecordDTO update(Long id, RecordDTO dto) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        record.setAccount(dto.getAccount());
        record.setCurrency(dto.getCurrency());
        record.setRecordType(dto.getRecordType());
        record.setMainCategory(dto.getMainCategory());
        record.setSubCategory(dto.getSubCategory());
        record.setAmount(dto.getAmount());
        record.setFee(dto.getFee());
        record.setDiscount(dto.getDiscount());
        record.setName(dto.getName());
        record.setMerchant(dto.getMerchant());
        record.setDate(dto.getDate());
        record.setTime(dto.getTime());
        record.setProject(dto.getProject());
        record.setDescription(dto.getDescription());
        record.setTags(dto.getTags());
        record.setTarget(dto.getTarget());
        return RecordDTO.fromEntity(recordRepository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        recordRepository.deleteById(id);
    }
}
```

- [ ] **Step 2: 创建 CalendarService**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/
git commit -m "feat(snapledger): add services (RecordService, CalendarService)"
```

---

## Task 6: 后端 Controller 层

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/RecordController.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/CalendarController.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/CategoryController.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java`

- [ ] **Step 1: 创建 RecordController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.service.RecordService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/snapledger/records")
@CrossOrigin(origins = "*")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public Page<RecordDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recordService.findAll(page, size);
    }

    @GetMapping("/date/{date}")
    public List<RecordDTO> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recordService.findByDate(date);
    }

    @GetMapping("/month/{year}/{month}")
    public List<RecordDTO> getByMonth(@PathVariable int year, @PathVariable int month) {
        return recordService.findByYearMonth(year, month);
    }

    @PostMapping
    public RecordDTO create(@RequestBody RecordDTO dto) {
        return recordService.create(dto);
    }

    @PutMapping("/{id}")
    public RecordDTO update(@PathVariable Long id, @RequestBody RecordDTO dto) {
        return recordService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        recordService.delete(id);
    }
}
```

- [ ] **Step 2: 创建 CalendarController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.CalendarMonthDTO;
import com.panda.snapledger.service.CalendarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/snapledger/calendar")
@CrossOrigin(origins = "*")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/{year}/{month}")
    public CalendarMonthDTO getMonthCalendar(@PathVariable int year, @PathVariable int month) {
        return calendarService.getMonthCalendar(year, month);
    }
}
```

- [ ] **Step 3: 创建 CategoryController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Category;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final RecordRepository recordRepository;

    public CategoryController(CategoryRepository categoryRepository, RecordRepository recordRepository) {
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
    }

    @GetMapping
    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    @GetMapping("/type/{type}")
    public List<Category> getByType(@PathVariable String type) {
        return categoryRepository.findByType(type);
    }

    @GetMapping("/main-categories/{recordType}")
    public List<String> getMainCategories(@PathVariable String recordType) {
        return recordRepository.findDistinctMainCategoriesByRecordType(recordType);
    }

    @GetMapping("/sub-categories/{mainCategory}")
    public List<String> getSubCategories(@PathVariable String mainCategory) {
        return recordRepository.findDistinctSubCategoriesByMainCategory(mainCategory);
    }

    @PostMapping
    public Category create(@RequestBody Category category) {
        return categoryRepository.save(category);
    }
}
```

- [ ] **Step 4: 创建 AccountController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.repository.AccountRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Account> getAll() {
        return accountRepository.findAll();
    }

    @PostMapping
    public Account create(@RequestBody Account account) {
        return accountRepository.save(account);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/
git commit -m "feat(snapledger): add controllers (Record, Calendar, Category, Account)"
```

---

## Task 7: CSV 导入功能

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/import/MozeCsvImporter.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/ImportController.java`

- [ ] **Step 1: 创建 MozeCsvImporter**

```java
package com.panda.snapledger.service.import;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Category;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MozeCsvImporter {

    private final RecordRepository recordRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public MozeCsvImporter(RecordRepository recordRepository,
                          CategoryRepository categoryRepository,
                          AccountRepository accountRepository) {
        this.recordRepository = recordRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public ImportResult importFromCsv(MultipartFile file) throws IOException {
        List<Record> records = new ArrayList<>();
        Set<String> accounts = new HashSet<>();
        Set<Category> categories = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            for (CSVRecord csvRecord : csvParser) {
                Record record = parseRecord(csvRecord);
                if (record != null) {
                    records.add(record);

                    if (record.getAccount() != null && !record.getAccount().isEmpty()) {
                        accounts.add(record.getAccount());
                    }

                    if (record.getMainCategory() != null && !record.getMainCategory().isEmpty()) {
                        Category cat = new Category();
                        cat.setMainCategory(record.getMainCategory());
                        cat.setSubCategory(record.getSubCategory());
                        cat.setType(record.getRecordType());
                        categories.add(cat);
                    }
                }
            }
        }

        for (String accountName : accounts) {
            if (!accountRepository.existsByName(accountName)) {
                Account account = new Account();
                account.setName(accountName);
                accountRepository.save(account);
            }
        }

        for (Category cat : categories) {
            if (categoryRepository.findByMainCategoryAndSubCategory(
                    cat.getMainCategory(), cat.getSubCategory()) == null) {
                categoryRepository.save(cat);
            }
        }

        recordRepository.saveAll(records);

        return new ImportResult(records.size(), accounts.size(), categories.size());
    }

    private Record parseRecord(CSVRecord csvRecord) {
        try {
            Record record = new Record();

            record.setAccount(getValue(csvRecord, "账户"));
            record.setCurrency(getValue(csvRecord, "币种", "CNY"));
            record.setRecordType(getValue(csvRecord, "记录类型"));
            record.setMainCategory(getValue(csvRecord, "主类别"));
            record.setSubCategory(getValue(csvRecord, "子类别"));

            String amountStr = getValue(csvRecord, "金额");
            if (amountStr != null && !amountStr.isEmpty()) {
                record.setAmount(new BigDecimal(amountStr.replace(",", "")));
            }

            String feeStr = getValue(csvRecord, "手续费");
            if (feeStr != null && !feeStr.isEmpty()) {
                record.setFee(new BigDecimal(feeStr.replace(",", "")));
            }

            String discountStr = getValue(csvRecord, "折扣");
            if (discountStr != null && !discountStr.isEmpty()) {
                record.setDiscount(new BigDecimal(discountStr.replace(",", "")));
            }

            record.setName(getValue(csvRecord, "名称"));
            record.setMerchant(getValue(csvRecord, "商家"));

            String dateStr = getValue(csvRecord, "日期");
            if (dateStr != null && !dateStr.isEmpty()) {
                record.setDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/M/d")));
            }

            String timeStr = getValue(csvRecord, "时间");
            if (timeStr != null && !timeStr.isEmpty()) {
                record.setTime(LocalTime.parse(timeStr));
            }

            record.setProject(getValue(csvRecord, "项目"));
            record.setDescription(getValue(csvRecord, "描述"));
            record.setTags(getValue(csvRecord, "标签"));
            record.setTarget(getValue(csvRecord, "对象"));

            return record;
        } catch (Exception e) {
            return null;
        }
    }

    private String getValue(CSVRecord record, String... headers) {
        for (String header : headers) {
            try {
                String value = record.get(header);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return headers.length > 1 ? headers[headers.length - 1] : null;
    }

    public static class ImportResult {
        private final int recordCount;
        private final int accountCount;
        private final int categoryCount;

        public ImportResult(int recordCount, int accountCount, int categoryCount) {
            this.recordCount = recordCount;
            this.accountCount = accountCount;
            this.categoryCount = categoryCount;
        }

        public int getRecordCount() { return recordCount; }
        public int getAccountCount() { return accountCount; }
        public int getCategoryCount() { return categoryCount; }
    }
}
```

- [ ] **Step 2: 添加 commons-csv 依赖到 pom.xml**

在 `app-snapledger/pom.xml` 的 dependencies 中添加：

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
    <version>1.10.0</version>
</dependency>
```

- [ ] **Step 3: 创建 ImportController**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.service.import.MozeCsvImporter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/snapledger/import")
@CrossOrigin(origins = "*")
public class ImportController {

    private final MozeCsvImporter mozeCsvImporter;

    public ImportController(MozeCsvImporter mozeCsvImporter) {
        this.mozeCsvImporter = mozeCsvImporter;
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("recordCount", result.getRecordCount());
        response.put("accountCount", result.getAccountCount());
        response.put("categoryCount", result.getCategoryCount());
        return response;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app-snapledger/
git commit -m "feat(snapledger): add CSV import functionality"
```

---

## Task 8: 前端 API 层

**Files:**
- Create: `frontend/src/api/snapledger/record.js`
- Create: `frontend/src/api/snapledger/calendar.js`
- Create: `frontend/src/api/snapledger/category.js`
- Create: `frontend/src/api/snapledger/account.js`
- Create: `frontend/src/api/snapledger/import.js`
- Create: `frontend/src/api/snapledger/index.js`
- Modify: `frontend/src/api.js`

- [ ] **Step 1: 创建 record.js**

```javascript
import api from '../index'

export function getRecords(page = 0, size = 20) {
  return api.get('/snapledger/records', { params: { page, size } })
}

export function getRecordsByDate(date) {
  return api.get(`/snapledger/records/date/${date}`)
}

export function getRecordsByMonth(year, month) {
  return api.get(`/snapledger/records/month/${year}/${month}`)
}

export function createRecord(data) {
  return api.post('/snapledger/records', data)
}

export function updateRecord(id, data) {
  return api.put(`/snapledger/records/${id}`, data)
}

export function deleteRecord(id) {
  return api.delete(`/snapledger/records/${id}`)
}
```

- [ ] **Step 2: 创建 calendar.js**

```javascript
import api from '../index'

export function getMonthCalendar(year, month) {
  return api.get(`/snapledger/calendar/${year}/${month}`)
}
```

- [ ] **Step 3: 创建 category.js**

```javascript
import api from '../index'

export function getCategories() {
  return api.get('/snapledger/categories')
}

export function getCategoriesByType(type) {
  return api.get(`/snapledger/categories/type/${type}`)
}

export function getMainCategories(recordType) {
  return api.get(`/snapledger/categories/main-categories/${recordType}`)
}

export function getSubCategories(mainCategory) {
  return api.get(`/snapledger/categories/sub-categories/${encodeURIComponent(mainCategory)}`)
}

export function createCategory(data) {
  return api.post('/snapledger/categories', data)
}
```

- [ ] **Step 4: 创建 account.js**

```javascript
import api from '../index'

export function getAccounts() {
  return api.get('/snapledger/accounts')
}

export function createAccount(data) {
  return api.post('/snapledger/accounts', data)
}
```

- [ ] **Step 5: 创建 import.js**

```javascript
import api from '../index'

export function importCsv(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/snapledger/import/csv', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

- [ ] **Step 6: 创建 index.js**

```javascript
export * from './record'
export * from './calendar'
export * from './category'
export * from './account'
export * from './import'
```

- [ ] **Step 7: 更新 api.js**

在 `frontend/src/api.js` 中取消注释并添加 snapledger 导出：

```javascript
// ========== 快记账 API ==========
export * from '@/api/snapledger/account'
export * from '@/api/snapledger/record'
export * from '@/api/snapledger/calendar'
export * from '@/api/snapledger/category'
export * from '@/api/snapledger/import'
```

- [ ] **Step 8: Commit**

```bash
git add frontend/src/api/
git commit -m "feat(snapledger): add frontend API layer"
```

---

## Task 9: 前端组件

**Files:**
- Create: `frontend/src/components/snapledger/RecordForm.vue`
- Create: `frontend/src/components/snapledger/RecordList.vue`
- Create: `frontend/src/components/snapledger/CalendarGrid.vue`
- Create: `frontend/src/components/snapledger/CategoryPicker.vue`

- [ ] **Step 1: 创建 RecordForm.vue**

```vue
<template>
  <van-cell-group inset>
    <van-field name="recordType" label="类型">
      <template #input>
        <van-radio-group v-model="form.recordType" direction="horizontal">
          <van-radio name="支出">支出</van-radio>
          <van-radio name="收入">收入</van-radio>
        </van-radio-group>
      </template>
    </van-field>

    <van-field
      v-model="form.amount"
      type="number"
      label="金额"
      placeholder="请输入金额"
      :rules="[{ required: true, message: '请输入金额' }]"
    />

    <van-field
      v-model="form.mainCategory"
      is-link
      readonly
      label="分类"
      placeholder="请选择分类"
      @click="showCategoryPicker = true"
    />

    <van-field
      v-model="form.account"
      is-link
      readonly
      label="账户"
      placeholder="请选择账户"
      @click="showAccountPicker = true"
    />

    <van-field
      v-model="form.date"
      is-link
      readonly
      label="日期"
      placeholder="请选择日期"
      @click="showDatePicker = true"
    />

    <van-field
      v-model="form.name"
      label="名称"
      placeholder="可选"
    />

    <van-field
      v-model="form.description"
      rows="2"
      autosize
      label="备注"
      type="textarea"
      placeholder="可选"
    />
  </van-cell-group>

  <!-- 分类选择器 -->
  <van-popup v-model:show="showCategoryPicker" position="bottom" round>
    <van-picker
      :columns="categoryColumns"
      @confirm="onCategoryConfirm"
      @cancel="showCategoryPicker = false"
    />
  </van-popup>

  <!-- 账户选择器 -->
  <van-popup v-model:show="showAccountPicker" position="bottom" round>
    <van-picker
      :columns="accountColumns"
      @confirm="onAccountConfirm"
      @cancel="showAccountPicker = false"
    />
  </van-popup>

  <!-- 日期选择器 -->
  <van-popup v-model:show="showDatePicker" position="bottom" round>
    <van-date-picker
      v-model="selectedDate"
      @confirm="onDateConfirm"
      @cancel="showDatePicker = false"
    />
  </van-popup>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getMainCategories, getSubCategories, getCategories } from '@/api'
import { getAccounts } from '@/api'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: new Date().toISOString().split('T')[0],
  name: '',
  description: ''
})

const categories = ref([])
const accounts = ref([])
const showCategoryPicker = ref(false)
const showAccountPicker = ref(false)
const showDatePicker = ref(false)
const selectedDate = ref(['2024', '01', '01'])

const categoryColumns = computed(() => {
  const mainCats = [...new Set(categories.value.map(c => c.mainCategory))]
  return mainCats.map(cat => ({ text: cat, value: cat }))
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

onMounted(async () => {
  try {
    const [catRes, accRes] = await Promise.all([
      getCategories(),
      getAccounts()
    ])
    categories.value = catRes.data || []
    accounts.value = accRes.data || []
  } catch (e) {
    console.error('Failed to load categories/accounts:', e)
  }
})

function onCategoryConfirm({ selectedOptions }) {
  form.value.mainCategory = selectedOptions[0].text
  showCategoryPicker.value = false
}

function onAccountConfirm({ selectedOptions }) {
  form.value.account = selectedOptions[0].text
  showAccountPicker.value = false
}

function onDateConfirm({ selectedValues }) {
  form.value.date = selectedValues.join('-')
  showDatePicker.value = false
}
</script>
```

- [ ] **Step 2: 创建 RecordList.vue**

```vue
<template>
  <van-cell-group inset>
    <van-cell
      v-for="record in records"
      :key="record.id"
      :title="record.name || record.mainCategory"
      :value="formatAmount(record)"
      :label="record.subCategory || record.mainCategory"
      :value-class="record.recordType === '收入' ? 'text-green' : 'text-red'"
      is-link
      @click="$emit('edit', record)"
    >
      <template #icon>
        <van-icon :name="getCategoryIcon(record.mainCategory)" class="mr-2" />
      </template>
    </van-cell>
    <van-empty v-if="records.length === 0" description="暂无记录" />
  </van-cell-group>
</template>

<script setup>
defineProps({
  records: { type: Array, default: () => [] }
})

defineEmits(['edit'])

function formatAmount(record) {
  const prefix = record.recordType === '收入' ? '+' : '-'
  return `${prefix}¥${record.amount}`
}

function getCategoryIcon(category) {
  const icons = {
    '餐饮': 'food',
    '交通': 'transport',
    '购物': 'shopping-cart',
    '娱乐': 'music',
    '医疗': 'hospital',
    '教育': 'education',
    '居住': 'home',
    '通讯': 'phone',
    '工资': 'gold-coin',
    '理财': 'balance-list'
  }
  return icons[category] || 'notes'
}
</script>

<style scoped>
.text-green { color: #07c160; }
.text-red { color: #ee0a24; }
.mr-2 { margin-right: 8px; }
</style>
```

- [ ] **Step 3: 创建 CalendarGrid.vue**

```vue
<template>
  <div class="calendar-grid">
    <!-- 星期标题 -->
    <div class="week-header">
      <span class="weekend">日</span>
      <span>一</span>
      <span>二</span>
      <span>三</span>
      <span>四</span>
      <span>五</span>
      <span class="weekend">六</span>
    </div>

    <!-- 日期格子 -->
    <div class="days-grid">
      <div
        v-for="day in calendarDays"
        :key="day.date"
        class="day-cell"
        :class="{
          'selected': isSelected(day.date),
          'has-records': day.recordCount > 0,
          'today': isToday(day.date)
        }"
        @click="$emit('select', day.date)"
      >
        <span class="day-number">{{ day.date.getDate() }}</span>
        <span v-if="day.recordCount > 0" class="dot"></span>
        <div v-if="day.income > 0 || day.expense > 0" class="day-amounts">
          <span v-if="day.income > 0" class="income">+{{ formatShort(day.income) }}</span>
          <span v-if="day.expense > 0" class="expense">-{{ formatShort(day.expense) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  days: { type: Array, default: () => [] },
  selectedDate: { type: Date, default: null }
})

defineEmits(['select'])

const calendarDays = computed(() => {
  const firstDay = new Date(props.year, props.month - 1, 1)
  const lastDay = new Date(props.year, props.month, 0)
  const startPadding = firstDay.getDay()

  const result = []

  // 前置空白
  for (let i = 0; i < startPadding; i++) {
    result.push({ date: null, income: 0, expense: 0, recordCount: 0 })
  }

  // 当月日期
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(props.year, props.month - 1, d)
    const dayData = props.days.find(day => {
      const dayDate = new Date(day.date)
      return dayDate.getDate() === d
    }) || { date: date.toISOString().split('T')[0], income: 0, expense: 0, recordCount: 0 }
    result.push({ ...dayData, date })
  }

  return result
})

function isSelected(date) {
  if (!date || !props.selectedDate) return false
  return date.toDateString() === props.selectedDate.toDateString()
}

function isToday(date) {
  if (!date) return false
  return date.toDateString() === new Date().toDateString()
}

function formatShort(amount) {
  if (amount >= 10000) {
    return (amount / 10000).toFixed(1) + 'w'
  }
  if (amount >= 1000) {
    return (amount / 1000).toFixed(1) + 'k'
  }
  return amount.toFixed(0)
}
</script>

<style scoped>
.calendar-grid {
  background: #fff;
  padding: 12px;
}

.week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.week-header .weekend {
  color: #ee0a24;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
}

.day-cell.selected {
  background: #e8f4ff;
}

.day-cell.today .day-number {
  color: #1989fa;
  font-weight: bold;
}

.day-cell.has-records .dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 4px;
  height: 4px;
  background: #1989fa;
  border-radius: 50%;
}

.day-number {
  font-size: 14px;
}

.day-amounts {
  font-size: 10px;
  margin-top: 2px;
}

.income { color: #07c160; }
.expense { color: #ee0a24; }
</style>
```

- [ ] **Step 4: 创建 CategoryPicker.vue**

```vue
<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="category-picker">
      <van-tabs v-model:active="activeTab">
        <van-tab title="支出">
          <van-grid :column-num="4">
            <van-grid-item
              v-for="cat in expenseCategories"
              :key="cat.id"
              :text="cat.mainCategory"
              @click="selectCategory(cat)"
            />
          </van-grid>
        </van-tab>
        <van-tab title="收入">
          <van-grid :column-num="4">
            <van-grid-item
              v-for="cat in incomeCategories"
              :key="cat.id"
              :text="cat.mainCategory"
              @click="selectCategory(cat)"
            />
          </van-grid>
        </van-tab>
      </van-tabs>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getCategories } from '@/api'

const props = defineProps({
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'select'])

const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

const activeTab = ref(0)
const categories = ref([])

const expenseCategories = computed(() =>
  categories.value.filter(c => c.type === '支出')
)

const incomeCategories = computed(() =>
  categories.value.filter(c => c.type === '收入')
)

watch(() => props.show, async (show) => {
  if (show && categories.value.length === 0) {
    try {
      const res = await getCategories()
      categories.value = res.data || []
    } catch (e) {
      console.error('Failed to load categories:', e)
    }
  }
})

function selectCategory(cat) {
  emit('select', cat)
  visible.value = false
}
</script>

<style scoped>
.category-picker {
  max-height: 50vh;
  overflow-y: auto;
}
</style>
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/snapledger/
git commit -m "feat(snapledger): add frontend components (RecordForm, RecordList, CalendarGrid, CategoryPicker)"
```

---

## Task 10: 前端页面

**Files:**
- Modify: `frontend/src/views/snapledger/Home.vue`
- Create: `frontend/src/views/snapledger/AddRecord.vue`
- Create: `frontend/src/views/snapledger/Calendar.vue`
- Create: `frontend/src/views/snapledger/Import.vue`
- Modify: `frontend/src/router.js`

- [ ] **Step 1: 更新 Home.vue**

```vue
<template>
  <div class="snap-home">
    <!-- 顶部概览 -->
    <div class="overview">
      <div class="month-selector" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth }}月
        <van-icon name="arrow-down" />
      </div>
      <div class="summary">
        <div class="item">
          <span class="label">收入</span>
          <span class="value income">+¥{{ totalIncome.toFixed(2) }}</span>
        </div>
        <div class="item">
          <span class="label">支出</span>
          <span class="value expense">-¥{{ totalExpense.toFixed(2) }}</span>
        </div>
        <div class="item">
          <span class="label">结余</span>
          <span class="value">{{ totalBalance >= 0 ? '+' : '' }}¥{{ totalBalance.toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="quick-actions">
      <van-button type="primary" block to="/snap/add">
        <van-icon name="plus" /> 记一笔
      </van-button>
    </div>

    <!-- 最近记录 -->
    <div class="recent-records">
      <van-cell-group inset>
        <van-cell title="最近记录" is-link to="/snap/calendar" />
      </van-cell-group>
      <RecordList :records="recentRecords" @edit="goToEdit" />
    </div>

    <!-- 底部导航 -->
    <van-tabbar v-model="activeTab">
      <van-tabbar-item icon="home-o" to="/snap">首页</van-tabbar-item>
      <van-tabbar-item icon="calendar-o" to="/snap/calendar">日历</van-tabbar-item>
      <van-tabbar-item icon="bar-chart-o" to="/snap/stats">统计</van-tabbar-item>
      <van-tabbar-item icon="setting-o" to="/snap/settings">设置</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar, getRecordsByMonth } from '@/api'
import RecordList from '@/components/snapledger/RecordList.vue'

const router = useRouter()
const activeTab = ref(0)
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const showMonthPicker = ref(false)

const monthData = ref(null)
const recentRecords = ref([])

const totalIncome = computed(() => monthData.value?.totalIncome || 0)
const totalExpense = computed(() => monthData.value?.totalExpense || 0)
const totalBalance = computed(() => totalIncome.value - totalExpense.value)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  try {
    const [calendarRes, recordsRes] = await Promise.all([
      getMonthCalendar(currentYear.value, currentMonth.value),
      getRecordsByMonth(currentYear.value, currentMonth.value)
    ])
    monthData.value = calendarRes.data
    recentRecords.value = (recordsRes.data || []).slice(0, 10)
  } catch (e) {
    console.error('Failed to load data:', e)
  }
}

function goToEdit(record) {
  router.push(`/snap/edit/${record.id}`)
}
</script>

<style scoped>
.snap-home {
  padding-bottom: 60px;
}

.overview {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  border-radius: 0 0 20px 20px;
}

.month-selector {
  text-align: center;
  font-size: 16px;
  margin-bottom: 16px;
}

.summary {
  display: flex;
  justify-content: space-around;
}

.summary .item {
  text-align: center;
}

.summary .label {
  display: block;
  font-size: 12px;
  opacity: 0.8;
}

.summary .value {
  display: block;
  font-size: 18px;
  font-weight: bold;
  margin-top: 4px;
}

.summary .income { color: #90EE90; }
.summary .expense { color: #FFB6C1; }

.quick-actions {
  padding: 16px;
}

.recent-records {
  padding: 0 16px;
}
</style>
```

- [ ] **Step 2: 创建 AddRecord.vue**

```vue
<template>
  <div class="add-record">
    <van-nav-bar
      :title="isEdit ? '编辑记录' : '记一笔'"
      left-arrow
      @click-left="$router.back()"
    />

    <RecordForm v-model="form" />

    <div class="actions">
      <van-button type="primary" block @click="save" :loading="saving">
        保存
      </van-button>
      <van-button v-if="isEdit" type="danger" block @click="remove" :loading="deleting">
        删除
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createRecord, updateRecord, deleteRecord, getRecordsByDate } from '@/api'
import RecordForm from '@/components/snapledger/RecordForm.vue'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: new Date().toISOString().split('T')[0],
  name: '',
  description: ''
})

const saving = ref(false)
const deleting = ref(false)

onMounted(async () => {
  if (isEdit.value) {
    // TODO: 加载现有记录
  }
})

async function save() {
  if (!form.value.amount || !form.value.mainCategory) {
    alert('请填写金额和分类')
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await updateRecord(route.params.id, form.value)
    } else {
      await createRecord(form.value)
    }
    router.push('/snap/calendar')
  } catch (e) {
    console.error('Failed to save:', e)
    alert('保存失败')
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!confirm('确定删除这条记录吗？')) return

  deleting.value = true
  try {
    await deleteRecord(route.params.id)
    router.push('/snap/calendar')
  } catch (e) {
    console.error('Failed to delete:', e)
    alert('删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.add-record {
  min-height: 100vh;
  background: #f7f8fa;
}

.actions {
  padding: 16px;
}
</style>
```

- [ ] **Step 3: 创建 Calendar.vue**

```vue
<template>
  <div class="calendar-page">
    <van-nav-bar title="日历" />

    <!-- 月份切换 -->
    <div class="month-nav">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="current-month">{{ year }}年{{ month }}月</span>
      <van-icon name="arrow-right" @click="nextMonth" />
    </div>

    <!-- 日历网格 -->
    <CalendarGrid
      :year="year"
      :month="month"
      :days="monthData?.days || []"
      :selected-date="selectedDate"
      @select="onDateSelect"
    />

    <!-- 当日记录列表 -->
    <div class="day-records">
      <van-cell-group inset>
        <van-cell :title="formatDate(selectedDate)" />
      </van-cell-group>
      <RecordList :records="dayRecords" @edit="goToEdit" />
    </div>

    <!-- 添加按钮 -->
    <van-floating-bubble
      icon="plus"
      @click="$router.push('/snap/add')"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar, getRecordsByDate } from '@/api'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'
import RecordList from '@/components/snapledger/RecordList.vue'

const router = useRouter()

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const selectedDate = ref(new Date())
const monthData = ref(null)
const dayRecords = ref([])

onMounted(async () => {
  await loadMonthData()
  await loadDayRecords()
})

watch([year, month], loadMonthData)

async function loadMonthData() {
  try {
    const res = await getMonthCalendar(year.value, month.value)
    monthData.value = res.data
  } catch (e) {
    console.error('Failed to load calendar:', e)
  }
}

async function loadDayRecords() {
  try {
    const dateStr = selectedDate.value.toISOString().split('T')[0]
    const res = await getRecordsByDate(dateStr)
    dayRecords.value = res.data || []
  } catch (e) {
    console.error('Failed to load records:', e)
  }
}

function prevMonth() {
  if (month.value === 1) {
    month.value = 12
    year.value--
  } else {
    month.value--
  }
}

function nextMonth() {
  if (month.value === 12) {
    month.value = 1
    year.value++
  } else {
    month.value++
  }
}

function onDateSelect(date) {
  selectedDate.value = date
  loadDayRecords()
}

function formatDate(date) {
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function goToEdit(record) {
  router.push(`/snap/edit/${record.id}`)
}
</script>

<style scoped>
.calendar-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 80px;
}

.month-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: white;
}

.current-month {
  font-size: 16px;
  font-weight: bold;
}

.day-records {
  margin-top: 12px;
}
</style>
```

- [ ] **Step 4: 创建 Import.vue**

```vue
<template>
  <div class="import-page">
    <van-nav-bar title="数据导入" left-arrow @click-left="$router.back()" />

    <div class="import-content">
      <van-cell-group inset>
        <van-cell title="从 moze CSV 导入" />
      </van-cell-group>

      <van-uploader
        v-model="files"
        :max-count="1"
        accept=".csv"
        :after-read="handleUpload"
      />

      <div v-if="result" class="import-result">
        <van-notice-bar
          color="#07c160"
          background="#f0f9eb"
          left-icon="passed"
        >
          导入成功！
        </van-notice-bar>
        <van-cell-group inset>
          <van-cell title="记录数" :value="result.recordCount" />
          <van-cell title="账户数" :value="result.accountCount" />
          <van-cell title="分类数" :value="result.categoryCount" />
        </van-cell-group>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { importCsv } from '@/api'

const files = ref([])
const result = ref(null)

async function handleUpload(file) {
  file.status = 'uploading'
  try {
    const res = await importCsv(file.file)
    result.value = res.data
    file.status = 'done'
  } catch (e) {
    file.status = 'failed'
    console.error('Import failed:', e)
    alert('导入失败: ' + (e.response?.data?.message || e.message))
  }
}
</script>

<style scoped>
.import-page {
  min-height: 100vh;
  background: #f7f8fa;
}

.import-content {
  padding: 16px;
}

.import-result {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 5: 更新 router.js**

在 `frontend/src/router.js` 中添加 snapledger 路由：

```javascript
// 快记账页面
import SnapHome from '@/views/snapledger/Home.vue'
import SnapAddRecord from '@/views/snapledger/AddRecord.vue'
import SnapCalendar from '@/views/snapledger/Calendar.vue'
import SnapImport from '@/views/snapledger/Import.vue'

// 在 routes 数组中添加：
// ========== 快记账路由 ==========
{
  path: '/snap',
  name: 'SnapHome',
  component: SnapHome,
  meta: { module: 'snapledger', transition: 'page-fade' }
},
{
  path: '/snap/add',
  name: 'SnapAddRecord',
  component: SnapAddRecord,
  meta: { module: 'snapledger', transition: 'page-slide' }
},
{
  path: '/snap/edit/:id',
  name: 'SnapEditRecord',
  component: SnapAddRecord,
  meta: { module: 'snapledger', transition: 'page-slide' }
},
{
  path: '/snap/calendar',
  name: 'SnapCalendar',
  component: SnapCalendar,
  meta: { module: 'snapledger', transition: 'page-fade' }
},
{
  path: '/snap/import',
  name: 'SnapImport',
  component: SnapImport,
  meta: { module: 'snapledger', transition: 'page-fade' }
},
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/snapledger/ frontend/src/router.js
git commit -m "feat(snapledger): add frontend pages (Home, AddRecord, Calendar, Import)"
```

---

## Task 11: 集成测试

- [ ] **Step 1: 启动后端服务**

Run: `mvn spring-boot:run -pl panda-api`

- [ ] **Step 2: 启动前端开发服务**

Run: `cd frontend && npm run dev`

- [ ] **Step 3: 测试核心功能**

测试清单：
- [ ] 访问 `/snap` 首页正常显示
- [ ] 点击"记一笔"进入记账页面
- [ ] 填写金额、分类、账户后保存成功
- [ ] 日历视图显示正确
- [ ] 点击日期显示当日记录
- [ ] CSV 导入功能正常

- [ ] **Step 4: 最终 Commit**

```bash
git add .
git commit -m "feat(snapledger): complete P0 core functionality"
```

---

## 后续任务（P1-P3）

### P1: 数据迁移增强
- 支持更多 CSV 格式
- 数据去重逻辑
- 导入预览功能

### P2: 图片记账
- 将 BaiduOcrClient 迁移到 common 模块
- 实现支付截图解析器
- 图片记账页面

### P3: 统计预算
- 统计 API（月度/年度/分类占比）
- 统计页面（图表展示）
- 预算设置与超支提醒
