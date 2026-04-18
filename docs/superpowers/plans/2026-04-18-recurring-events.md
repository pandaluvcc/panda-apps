# 周期事件 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Snap Ledger 的周期事件功能——支持创建可重复发生的记账事件（房贷、订阅等），自动生成未来记录，支持"进行中/已结束"状态管理和三种编辑模式。

**Architecture:** 后端新增 `RecurringEvent` 独立实体 + `sl_record` 表加两列 (`recurring_event_id`, `period_number`) 建立关联；`RecurringEventGenerator` 负责期数日期计算和 record 生成；定时任务每日维护无限期事件的滚动窗口（36 期）和有限次数事件的自动结束。前端在"新增记录"页加高级设置 Bottom Sheet，改造列表页、新建详情页。

**Tech Stack:** Spring Boot 3.2 + JPA + MySQL (后端)；Vue 3 Composition API + Vant (前端)；JUnit 5 + `@SpringBootTest`（测试位于 `panda-api/src/test/`）。

**参考 spec:** `docs/superpowers/specs/2026-04-18-recurring-events-design.md`

**对齐现有代码的修正:**
- `sl_record` 表使用 `account`（字符串账户名）而非 `account_id`，周期事件也沿用字符串存账户名（字段名 `account` / `target_account`）
- 周期事件分类字段使用 `main_category` / `sub_category`（字符串，与 Record 一致）
- 所有表使用 `sl_` 前缀

---

## Phase 1 — 后端基础：实体与数据库

### Task 1: 创建 `RecurringEvent` 实体

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/domain/RecurringEvent.java`

- [ ] **Step 1: 写实体类**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sl_recurring_event", indexes = {
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_generated_until", columnList = "generated_until")
})
@Data
public class RecurringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "record_type", nullable = false, length = 20)
    private String recordType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "main_category", length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "account", nullable = false, length = 50)
    private String account;

    @Column(name = "target_account", length = 50)
    private String targetAccount;

    @Column(name = "interval_type", nullable = false, length = 10)
    private String intervalType; // DAILY / WEEKLY / MONTHLY / YEARLY

    @Column(name = "interval_value", nullable = false)
    private Integer intervalValue = 1;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_periods")
    private Integer totalPeriods;

    @Column(name = "generated_until", nullable = false)
    private LocalDate generatedUntil;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_ACTIVE;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    public static final String INTERVAL_DAILY = "DAILY";
    public static final String INTERVAL_WEEKLY = "WEEKLY";
    public static final String INTERVAL_MONTHLY = "MONTHLY";
    public static final String INTERVAL_YEARLY = "YEARLY";

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

- [ ] **Step 2: 启动应用验证 schema 创建**

运行: `cd panda-api && mvn spring-boot:run`
Expected: 启动无错，自动建表 `sl_recurring_event`
检查: `mysql -u snapledger_user -p snapledger_db -e "SHOW CREATE TABLE sl_recurring_event\G"`

- [ ] **Step 3: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/RecurringEvent.java
git commit -m "feat(snapledger): add RecurringEvent entity"
```

---

### Task 2: 给 `Record` 加关联字段

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java`

- [ ] **Step 1: 在 Record 类内新增两个字段**

加在 `updatedAt` 字段之前：

```java
    @Column(name = "recurring_event_id")
    private Long recurringEventId;

    @Column(name = "period_number")
    private Integer periodNumber;
```

- [ ] **Step 2: 启动验证 schema 变更**

Expected: `sl_record` 表新增 `recurring_event_id`, `period_number` 两列

- [ ] **Step 3: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java
git commit -m "feat(snapledger): link Record to RecurringEvent"
```

---

### Task 3: Repository

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecurringEventRepository.java`
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java`

- [ ] **Step 1: 新建 Repository**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringEventRepository extends JpaRepository<RecurringEvent, Long> {
    List<RecurringEvent> findByStatusOrderByIdDesc(String status);
    List<RecurringEvent> findByStatusAndTotalPeriodsIsNull(String status);
    List<RecurringEvent> findByStatusAndTotalPeriodsIsNotNull(String status);
}
```

- [ ] **Step 2: 在 RecordRepository 加三个查询方法**

```java
    List<Record> findByRecurringEventIdOrderByDateDesc(Long recurringEventId);
    List<Record> findByRecurringEventIdAndDateAfter(Long recurringEventId, LocalDate date);
    List<Record> findByNameAndRecurringEventIdIsNull(String name);
```

（必要 import: `java.time.LocalDate`, `java.util.List`, `com.panda.snapledger.domain.Record`）

- [ ] **Step 3: 编译**

Run: `mvn -pl app-snapledger compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/
git commit -m "feat(snapledger): add repositories for recurring events"
```

---

## Phase 2 — 后端核心：期数生成器

### Task 4: 日期计算器（TDD）

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/recurring/PeriodDateCalculator.java`
- Test: `panda-api/src/test/java/com/panda/snapledger/service/recurring/PeriodDateCalculatorTest.java`

- [ ] **Step 1: 写测试**

```java
package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.RecurringEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodDateCalculatorTest {

    private RecurringEvent event(String type, int value, LocalDate start) {
        RecurringEvent e = new RecurringEvent();
        e.setIntervalType(type);
        e.setIntervalValue(value);
        e.setStartDate(start);
        return e;
    }

    @Test
    void monthlyNthPeriodAddsMonths() {
        RecurringEvent e = event("MONTHLY", 1, LocalDate.of(2026, 1, 19));
        assertEquals(LocalDate.of(2026, 1, 19), PeriodDateCalculator.dateOfPeriod(e, 1));
        assertEquals(LocalDate.of(2026, 2, 19), PeriodDateCalculator.dateOfPeriod(e, 2));
        assertEquals(LocalDate.of(2027, 1, 19), PeriodDateCalculator.dateOfPeriod(e, 13));
    }

    @Test
    void monthlyClampsToMonthEndWhenDayMissing() {
        // 起始 1/31，下一期应为 2 月 28 或 29
        RecurringEvent e = event("MONTHLY", 1, LocalDate.of(2026, 1, 31));
        assertEquals(LocalDate.of(2026, 2, 28), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void weeklyAddsSevenDays() {
        RecurringEvent e = event("WEEKLY", 1, LocalDate.of(2026, 4, 18));
        assertEquals(LocalDate.of(2026, 4, 25), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void dailyAddsDays() {
        RecurringEvent e = event("DAILY", 3, LocalDate.of(2026, 4, 18));
        assertEquals(LocalDate.of(2026, 4, 21), PeriodDateCalculator.dateOfPeriod(e, 2));
    }

    @Test
    void yearlyAddsYears() {
        RecurringEvent e = event("YEARLY", 1, LocalDate.of(2024, 2, 29));
        assertEquals(LocalDate.of(2025, 2, 28), PeriodDateCalculator.dateOfPeriod(e, 2));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd panda-api && mvn -Dtest=PeriodDateCalculatorTest test`
Expected: FAIL (类不存在)

- [ ] **Step 3: 实现**

```java
package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.RecurringEvent;

import java.time.LocalDate;

public final class PeriodDateCalculator {

    private PeriodDateCalculator() {}

    /** 返回第 period 期的日期（period 从 1 开始）。 */
    public static LocalDate dateOfPeriod(RecurringEvent event, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        int offset = (period - 1) * event.getIntervalValue();
        LocalDate start = event.getStartDate();
        return switch (event.getIntervalType()) {
            case RecurringEvent.INTERVAL_DAILY -> start.plusDays(offset);
            case RecurringEvent.INTERVAL_WEEKLY -> start.plusWeeks(offset);
            case RecurringEvent.INTERVAL_MONTHLY -> start.plusMonths(offset);
            case RecurringEvent.INTERVAL_YEARLY -> start.plusYears(offset);
            default -> throw new IllegalArgumentException("unknown interval: " + event.getIntervalType());
        };
    }
}
```

注：`LocalDate.plusMonths` / `plusYears` 会自动将超出月末的日期调整为月末（如 1/31 + 1 month = 2/28），符合测试预期。

- [ ] **Step 4: 再次运行测试**

Run: `cd panda-api && mvn -Dtest=PeriodDateCalculatorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/recurring/PeriodDateCalculator.java \
        panda-api/src/test/java/com/panda/snapledger/service/recurring/PeriodDateCalculatorTest.java
git commit -m "feat(snapledger): add period date calculator with tests"
```

---

### Task 5: 期数生成器 `RecurringEventGenerator`

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/recurring/RecurringEventGenerator.java`
- Test: `panda-api/src/test/java/com/panda/snapledger/service/recurring/RecurringEventGeneratorTest.java`

**设计要点**：
- 方法 `generate(event, fromPeriod, toPeriod)` 为指定期数区间创建 record（保留已存在的 `period_number`，跳过冲突）
- 方法 `targetWindowEnd(event, today)` 计算无限期事件的目标 `generated_until`（36 期 / 到达 today + ~36 个月）
- 滚动窗口策略：若 `generated_until - today < 180 days` 则继续生成直到覆盖"今天 + 约 36 × 间隔"

- [ ] **Step 1: 写测试（聚焦关键行为）**

```java
package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringEventGeneratorTest {

    @Mock RecordRepository recordRepository;
    @InjectMocks RecurringEventGenerator generator;

    private RecurringEvent monthlyEvent() {
        RecurringEvent e = new RecurringEvent();
        e.setId(1L);
        e.setName("商贷");
        e.setRecordType("支出");
        e.setAmount(new BigDecimal("2985.34"));
        e.setMainCategory("房产");
        e.setAccount("中信银行");
        e.setIntervalType("MONTHLY");
        e.setIntervalValue(1);
        e.setDayOfMonth(20);
        e.setStartDate(LocalDate.of(2024, 12, 20));
        return e;
    }

    @Test
    void generateCreatesRecordsForPeriodRange() {
        when(recordRepository.findByRecurringEventIdOrderByDateDesc(1L))
            .thenReturn(Collections.emptyList());
        ArgumentCaptor<List<Record>> captor = ArgumentCaptor.forClass(List.class);

        generator.generate(monthlyEvent(), 1, 3);

        verify(recordRepository).saveAll(captor.capture());
        List<Record> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals(1, saved.get(0).getPeriodNumber());
        assertEquals(LocalDate.of(2024, 12, 20), saved.get(0).getDate());
        assertEquals(LocalDate.of(2025, 2, 20), saved.get(2).getDate());
        assertEquals(new BigDecimal("2985.34"), saved.get(0).getAmount());
        assertEquals(1L, saved.get(0).getRecurringEventId());
    }

    @Test
    void generateSkipsExistingPeriodNumbers() {
        Record existing = new Record();
        existing.setPeriodNumber(2);
        when(recordRepository.findByRecurringEventIdOrderByDateDesc(1L))
            .thenReturn(List.of(existing));
        ArgumentCaptor<List<Record>> captor = ArgumentCaptor.forClass(List.class);

        generator.generate(monthlyEvent(), 1, 3);

        verify(recordRepository).saveAll(captor.capture());
        List<Record> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().noneMatch(r -> Integer.valueOf(2).equals(r.getPeriodNumber())));
    }

    @Test
    void targetWindowEndReturnsDateCoveringThirtySixPeriods() {
        RecurringEvent e = monthlyEvent();
        LocalDate today = LocalDate.of(2026, 4, 18);
        LocalDate target = generator.targetWindowEnd(e, today);
        // 至少覆盖 today + 36 个月左右
        assertTrue(target.isAfter(today.plusMonths(35)));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd panda-api && mvn -Dtest=RecurringEventGeneratorTest test`
Expected: FAIL

- [ ] **Step 3: 实现**

```java
package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecurringEventGenerator {

    public static final int DEFAULT_WINDOW_PERIODS = 36;

    private final RecordRepository recordRepository;

    public RecurringEventGenerator(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /** 为 [fromPeriod, toPeriod] 区间生成 record，跳过已存在的 period_number。 */
    public void generate(RecurringEvent event, int fromPeriod, int toPeriod) {
        Set<Integer> existing = recordRepository
            .findByRecurringEventIdOrderByDateDesc(event.getId())
            .stream().map(Record::getPeriodNumber).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Record> toSave = new ArrayList<>();
        for (int p = fromPeriod; p <= toPeriod; p++) {
            if (existing.contains(p)) continue;
            toSave.add(buildRecord(event, p));
        }
        if (!toSave.isEmpty()) {
            recordRepository.saveAll(toSave);
        }
    }

    /** 无限期事件：返回滚动窗口的目标截止日期（当前日期后 36 期）。 */
    public LocalDate targetWindowEnd(RecurringEvent event, LocalDate today) {
        // 找到从今天起第 36 期的日期
        LocalDate start = event.getStartDate();
        int periodsSinceStart = periodsBetween(event, start, today);
        int targetPeriod = periodsSinceStart + DEFAULT_WINDOW_PERIODS;
        return PeriodDateCalculator.dateOfPeriod(event, Math.max(targetPeriod, DEFAULT_WINDOW_PERIODS));
    }

    /** 返回从 start_date 到 date 已跨越的期数（用于判断当前已处在第几期）。 */
    public int periodsBetween(RecurringEvent event, LocalDate from, LocalDate to) {
        // 线性扫描（事件最多 36 期窗口，开销可接受）
        int p = 1;
        while (PeriodDateCalculator.dateOfPeriod(event, p + 1).isBefore(to)
            || PeriodDateCalculator.dateOfPeriod(event, p + 1).isEqual(to)) {
            p++;
            if (p > 10_000) break; // 安全阈
        }
        return p;
    }

    private Record buildRecord(RecurringEvent event, int period) {
        Record r = new Record();
        r.setRecurringEventId(event.getId());
        r.setPeriodNumber(period);
        r.setName(event.getName());
        r.setRecordType(event.getRecordType());
        r.setMainCategory(event.getMainCategory());
        r.setSubCategory(event.getSubCategory());
        r.setAmount(event.getAmount());
        r.setAccount(event.getAccount());
        r.setTarget(event.getTargetAccount());
        r.setDate(PeriodDateCalculator.dateOfPeriod(event, period));
        return r;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd panda-api && mvn -Dtest=RecurringEventGeneratorTest test`
Expected: PASS (3 tests)

- [ ] **Step 5: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/recurring/RecurringEventGenerator.java \
        panda-api/src/test/java/com/panda/snapledger/service/recurring/RecurringEventGeneratorTest.java
git commit -m "feat(snapledger): add recurring event record generator"
```

---

## Phase 3 — 后端 Service：CRUD + 修改 + 结束 + 删除

### Task 6: DTO

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecurringEventRequest.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecurringEventResponse.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecurringEventDetailResponse.java`

- [ ] **Step 1: 创建三个 DTO（全部使用 Lombok `@Data`）**

`RecurringEventRequest`：包含所有可创建/更新字段（name, recordType, amount, mainCategory, subCategory, account, targetAccount, intervalType, intervalValue, dayOfMonth, dayOfWeek, startDate, totalPeriods, note）

`RecurringEventResponse`：包含所有 RecurringEvent 字段 + 派生字段 `nextDueDate`（下一未发生期的日期）

`RecurringEventDetailResponse`：继承（或内嵌）Response，加 `records: List<RecordDTO>`

参考现有 `BudgetDTO.java` 模式。

- [ ] **Step 2: 编译**

Run: `mvn -pl app-snapledger compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecurringEvent*
git commit -m "feat(snapledger): add recurring event DTOs"
```

---

### Task 7: `RecurringEventService` —— 创建 + 历史回溯

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/RecurringEventService.java`
- Test: `panda-api/src/test/java/com/panda/snapledger/service/RecurringEventServiceTest.java`

- [ ] **Step 1: 写集成测试（@SpringBootTest）**

测试场景：
1. 创建无限期事件：生成初始 36 期 record；`generated_until` 被设置
2. 创建有限次数事件 (N=12)：只生成 12 条
3. 创建事件前 DB 里已有 3 条同名历史 record（`recurring_event_id=null`）：创建后它们被回填 `recurring_event_id` 和 `period_number`

```java
package com.panda.snapledger.service;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class RecurringEventServiceTest {

    @Autowired RecurringEventService service;
    @Autowired RecurringEventRepository eventRepo;
    @Autowired RecordRepository recordRepo;

    @BeforeEach
    void clean() {
        recordRepo.deleteAll();
        eventRepo.deleteAll();
    }

    private RecurringEventRequest monthlyReq(String name, Integer totalPeriods) {
        RecurringEventRequest r = new RecurringEventRequest();
        r.setName(name);
        r.setRecordType("支出");
        r.setAmount(new BigDecimal("2985.34"));
        r.setMainCategory("房产");
        r.setAccount("中信银行");
        r.setIntervalType("MONTHLY");
        r.setIntervalValue(1);
        r.setDayOfMonth(20);
        r.setStartDate(LocalDate.of(2024, 12, 20));
        r.setTotalPeriods(totalPeriods);
        return r;
    }

    @Test
    void createInfiniteEventGenerates36Periods() {
        RecurringEvent e = service.create(monthlyReq("商贷", null));
        List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertEquals(36, records.size());
        assertNotNull(e.getGeneratedUntil());
    }

    @Test
    void createFiniteEventGeneratesAllPeriods() {
        RecurringEvent e = service.create(monthlyReq("车贷", 12));
        List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        assertEquals(12, records.size());
    }

    @Test
    void createEventBackfillsHistoricalRecordsByName() {
        // 预置 3 条同名历史 record
        for (int i = 0; i < 3; i++) {
            Record r = new Record();
            r.setName("商贷");
            r.setAmount(new BigDecimal("2985.34"));
            r.setRecordType("支出");
            r.setAccount("中信银行");
            r.setDate(LocalDate.of(2024, 12 + i, 20).minusYears(0));
            // 注意：需确保是 2024-12 / 2025-01 / 2025-02
            recordRepo.save(r);
        }

        RecurringEvent e = service.create(monthlyReq("商贷", null));

        List<Record> linked = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
        // 3 条历史 + 剩余 33 条新生成，共 36 条期数
        assertTrue(linked.stream().allMatch(r -> e.getId().equals(r.getRecurringEventId())));
        assertEquals(0, recordRepo.findByNameAndRecurringEventIdIsNull("商贷").size());
    }
}
```

注：第 3 个测试里构造历史日期时用 `LocalDate.of(2024, 12, 20).plusMonths(i)` 更简洁。

- [ ] **Step 2: 运行测试验证失败**

Run: `cd panda-api && mvn -Dtest=RecurringEventServiceTest test`
Expected: FAIL

- [ ] **Step 3: 实现 Service**

```java
package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import com.panda.snapledger.service.recurring.PeriodDateCalculator;
import com.panda.snapledger.service.recurring.RecurringEventGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RecurringEventService {

    private final RecurringEventRepository eventRepo;
    private final RecordRepository recordRepo;
    private final RecurringEventGenerator generator;

    public RecurringEventService(RecurringEventRepository eventRepo,
                                 RecordRepository recordRepo,
                                 RecurringEventGenerator generator) {
        this.eventRepo = eventRepo;
        this.recordRepo = recordRepo;
        this.generator = generator;
    }

    @Transactional(rollbackFor = Exception.class)
    public RecurringEvent create(RecurringEventRequest req) {
        RecurringEvent e = new RecurringEvent();
        applyRequest(e, req);
        e.setStatus(RecurringEvent.STATUS_ACTIVE);
        e = eventRepo.save(e);

        // 生成期数
        int totalToGenerate = req.getTotalPeriods() != null
            ? req.getTotalPeriods()
            : RecurringEventGenerator.DEFAULT_WINDOW_PERIODS;
        generator.generate(e, 1, totalToGenerate);
        e.setGeneratedUntil(PeriodDateCalculator.dateOfPeriod(e, totalToGenerate));
        eventRepo.save(e);

        // 回溯历史同名 record
        backfillHistorical(e);
        return e;
    }

    private void backfillHistorical(RecurringEvent event) {
        List<Record> orphans = recordRepo.findByNameAndRecurringEventIdIsNull(event.getName());
        for (Record r : orphans) {
            r.setRecurringEventId(event.getId());
            // period_number 通过日期反推
            int period = generator.periodsBetween(event, event.getStartDate(), r.getDate());
            r.setPeriodNumber(period);
        }
        if (!orphans.isEmpty()) {
            recordRepo.saveAll(orphans);
            // 删除 generator 生成的与历史 record 冲突的期数
            deleteDuplicateGeneratedPeriods(event, orphans);
        }
    }

    private void deleteDuplicateGeneratedPeriods(RecurringEvent event, List<Record> historical) {
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(event.getId());
        // 对每个 period_number，若有 >1 条且其中包含历史记录（orphans 中存在），删除非历史的那条
        java.util.Set<Long> historyIds = historical.stream().map(Record::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Integer, List<Record>> byPeriod = new java.util.HashMap<>();
        for (Record r : all) {
            if (r.getPeriodNumber() == null) continue;
            byPeriod.computeIfAbsent(r.getPeriodNumber(), k -> new java.util.ArrayList<>()).add(r);
        }
        List<Record> toDelete = new java.util.ArrayList<>();
        for (List<Record> group : byPeriod.values()) {
            if (group.size() < 2) continue;
            boolean hasHistory = group.stream().anyMatch(r -> historyIds.contains(r.getId()));
            if (!hasHistory) continue;
            for (Record r : group) {
                if (!historyIds.contains(r.getId())) toDelete.add(r);
            }
        }
        if (!toDelete.isEmpty()) recordRepo.deleteAll(toDelete);
    }

    private void applyRequest(RecurringEvent e, RecurringEventRequest req) {
        e.setName(req.getName());
        e.setRecordType(req.getRecordType());
        e.setAmount(req.getAmount());
        e.setMainCategory(req.getMainCategory());
        e.setSubCategory(req.getSubCategory());
        e.setAccount(req.getAccount());
        e.setTargetAccount(req.getTargetAccount());
        e.setIntervalType(req.getIntervalType());
        e.setIntervalValue(req.getIntervalValue() != null ? req.getIntervalValue() : 1);
        e.setDayOfMonth(req.getDayOfMonth());
        e.setDayOfWeek(req.getDayOfWeek());
        e.setStartDate(req.getStartDate());
        e.setTotalPeriods(req.getTotalPeriods());
        e.setNote(req.getNote());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Expected: 3 tests PASS

- [ ] **Step 5: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/RecurringEventService.java \
        panda-api/src/test/java/com/panda/snapledger/service/RecurringEventServiceTest.java
git commit -m "feat(snapledger): add recurring event service with history backfill"
```

---

### Task 8: Service —— 修改 / 结束 / 删除

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/RecurringEventService.java`
- Modify: `panda-api/src/test/java/com/panda/snapledger/service/RecurringEventServiceTest.java`

在 Service 上加以下方法：

```java
    @Transactional
    public RecurringEvent updateEntireEvent(Long id, RecurringEventRequest req) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        applyRequest(e, req);
        eventRepo.save(e);
        // 同步所有未来未发生 record
        LocalDate today = LocalDate.now();
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        for (Record r : future) {
            r.setName(e.getName());
            r.setRecordType(e.getRecordType());
            r.setAmount(e.getAmount());
            r.setMainCategory(e.getMainCategory());
            r.setSubCategory(e.getSubCategory());
            r.setAccount(e.getAccount());
            r.setTarget(e.getTargetAccount());
        }
        recordRepo.saveAll(future);
        return e;
    }

    @Transactional
    public RecurringEvent updateFromPeriod(Long id, int fromPeriod, RecurringEventRequest req) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        applyRequest(e, req);
        eventRepo.save(e);
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(id);
        for (Record r : all) {
            if (r.getPeriodNumber() != null && r.getPeriodNumber() >= fromPeriod) {
                r.setName(e.getName());
                r.setRecordType(e.getRecordType());
                r.setAmount(e.getAmount());
                r.setMainCategory(e.getMainCategory());
                r.setSubCategory(e.getSubCategory());
                r.setAccount(e.getAccount());
                r.setTarget(e.getTargetAccount());
            }
        }
        recordRepo.saveAll(all);
        return e;
    }

    @Transactional
    public void endEvent(Long id) {
        RecurringEvent e = eventRepo.findById(id).orElseThrow();
        LocalDate today = LocalDate.now();
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        recordRepo.deleteAll(future);
        e.setStatus(RecurringEvent.STATUS_ENDED);
        e.setEndedAt(java.time.LocalDateTime.now());
        eventRepo.save(e);
    }

    @Transactional
    public void deleteEvent(Long id) {
        LocalDate today = LocalDate.now();
        // 删除未来 record
        List<Record> future = recordRepo.findByRecurringEventIdAndDateAfter(id, today);
        recordRepo.deleteAll(future);
        // 历史 record 解绑
        List<Record> all = recordRepo.findByRecurringEventIdOrderByDateDesc(id);
        for (Record r : all) {
            r.setRecurringEventId(null);
            r.setPeriodNumber(null);
        }
        recordRepo.saveAll(all);
        eventRepo.deleteById(id);
    }

    public List<RecurringEvent> list(String status) {
        return eventRepo.findByStatusOrderByIdDesc(status);
    }

    public RecurringEvent findById(Long id) {
        return eventRepo.findById(id).orElseThrow();
    }
```

- [ ] **Step 1: 在测试类补 4 个测试**（updateEntireEvent 改金额后未来 record 同步；updateFromPeriod 只改指定期及之后；endEvent 删除未来保留历史；deleteEvent 历史解绑）

- [ ] **Step 2: 运行 `mvn -Dtest=RecurringEventServiceTest test`**
Expected: 7 tests PASS

- [ ] **Step 3: 提交**

```bash
git commit -am "feat(snapledger): add update/end/delete for recurring events"
```

---

### Task 9: Controller + API

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/RecurringEventController.java`

- [ ] **Step 1: 写 Controller（参考 `BudgetController.java` 的样式）**

接口（全部返回 `ApiResponse<T>`）：

| 方法 | 路径 |
|---|---|
| POST | `/api/snapledger/recurring-events` |
| GET | `/api/snapledger/recurring-events?status=ACTIVE` |
| GET | `/api/snapledger/recurring-events/{id}` |
| PUT | `/api/snapledger/recurring-events/{id}` |
| PUT | `/api/snapledger/recurring-events/{id}/from-period/{n}` |
| POST | `/api/snapledger/recurring-events/{id}/end` |
| DELETE | `/api/snapledger/recurring-events/{id}` |

GET 详情时返回 `RecurringEventDetailResponse`（带 records 列表）。

- [ ] **Step 2: 启动并用 curl 验证**

```bash
cd panda-api && mvn spring-boot:run
# 在另一终端
curl -X POST http://localhost:8080/api/snapledger/recurring-events \
  -H "Content-Type: application/json" \
  -d '{"name":"商贷","recordType":"支出","amount":2985.34,"mainCategory":"房产","account":"中信银行","intervalType":"MONTHLY","intervalValue":1,"dayOfMonth":20,"startDate":"2024-12-20"}'

curl "http://localhost:8080/api/snapledger/recurring-events?status=ACTIVE"
```
Expected: 200 + 合理 JSON

- [ ] **Step 3: 提交**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/RecurringEventController.java
git commit -m "feat(snapledger): add recurring event REST endpoints"
```

---

## Phase 4 — 后台定时任务

### Task 10: Scheduler —— 滚动窗口扩展 + 自动结束

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/recurring/RecurringEventScheduler.java`
- Test: `panda-api/src/test/java/com/panda/snapledger/service/recurring/RecurringEventSchedulerTest.java`

- [ ] **Step 1: 写测试**

```java
// 关键测试：
// 1. 无限期事件 generated_until 离 today 少于 180 天 → 窗口扩展（generated_until 推后）
// 2. 有限次数事件的最后一期日期 < today → status 变 ENDED
```

- [ ] **Step 2: 实现**

```java
package com.panda.snapledger.service.recurring;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RecurringEventScheduler {

    private static final int EXTEND_THRESHOLD_DAYS = 180;

    private final RecurringEventRepository eventRepo;
    private final RecordRepository recordRepo;
    private final RecurringEventGenerator generator;

    public RecurringEventScheduler(RecurringEventRepository eventRepo,
                                    RecordRepository recordRepo,
                                    RecurringEventGenerator generator) {
        this.eventRepo = eventRepo;
        this.recordRepo = recordRepo;
        this.generator = generator;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void runDaily() {
        extendInfiniteWindows();
        autoEndFiniteEvents();
    }

    void extendInfiniteWindows() {
        LocalDate today = LocalDate.now();
        List<RecurringEvent> events = eventRepo.findByStatusAndTotalPeriodsIsNull(RecurringEvent.STATUS_ACTIVE);
        for (RecurringEvent e : events) {
            if (e.getGeneratedUntil().minusDays(EXTEND_THRESHOLD_DAYS).isAfter(today)) continue;
            int currentPeriod = generator.periodsBetween(e, e.getStartDate(), e.getGeneratedUntil());
            int targetPeriod = currentPeriod + RecurringEventGenerator.DEFAULT_WINDOW_PERIODS;
            generator.generate(e, currentPeriod + 1, targetPeriod);
            e.setGeneratedUntil(PeriodDateCalculator.dateOfPeriod(e, targetPeriod));
            eventRepo.save(e);
        }
    }

    void autoEndFiniteEvents() {
        LocalDate today = LocalDate.now();
        List<RecurringEvent> events = eventRepo.findByStatusAndTotalPeriodsIsNotNull(RecurringEvent.STATUS_ACTIVE);
        for (RecurringEvent e : events) {
            List<Record> records = recordRepo.findByRecurringEventIdOrderByDateDesc(e.getId());
            if (records.isEmpty()) continue;
            LocalDate lastDate = records.get(0).getDate();
            if (lastDate.isBefore(today)) {
                e.setStatus(RecurringEvent.STATUS_ENDED);
                e.setEndedAt(LocalDateTime.now());
                eventRepo.save(e);
            }
        }
    }
}
```

- [ ] **Step 3: 在 `PandaApplication.java` 加 `@EnableScheduling`（若还没有）**

检查: `grep -n "EnableScheduling" panda-api/src/main/java/com/panda/PandaApplication.java`
如果没有则加上。

- [ ] **Step 4: 运行测试**

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat(snapledger): add scheduler for recurring events"
```

---

## Phase 5 — 前端

### Task 11: API 客户端

**Files:**
- Create: `frontend/src/api/snapledger/recurringEvent.js`

- [ ] **Step 1: 参考 `frontend/src/api/snapledger/budget.js` 写 API**

```js
import request from '@/api/request'

export const listRecurringEvents = (status = 'ACTIVE') =>
  request.get('/api/snapledger/recurring-events', { params: { status } })

export const getRecurringEvent = (id) =>
  request.get(`/api/snapledger/recurring-events/${id}`)

export const createRecurringEvent = (data) =>
  request.post('/api/snapledger/recurring-events', data)

export const updateEntireRecurringEvent = (id, data) =>
  request.put(`/api/snapledger/recurring-events/${id}`, data)

export const updateFromPeriod = (id, fromPeriod, data) =>
  request.put(`/api/snapledger/recurring-events/${id}/from-period/${fromPeriod}`, data)

export const endRecurringEvent = (id) =>
  request.post(`/api/snapledger/recurring-events/${id}/end`)

export const deleteRecurringEvent = (id) =>
  request.delete(`/api/snapledger/recurring-events/${id}`)
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/api/snapledger/recurringEvent.js
git commit -m "feat(frontend): add recurring event API client"
```

---

### Task 12: 高级设置弹窗组件

**Files:**
- Create: `frontend/src/components/snapledger/AdvancedSettingsSheet.vue`

- [ ] **Step 1: 实现**

字段：
- Tabs: 单次 / 周期 / 分期（分期占位"敬请期待"）
- 周期字段：区间（picker: 每日/每周/每月/每年）、指定日期（月/周时）、次数（radio: 无限期 / N 次 + 输入框）
- 入账方式：显示"立即入账"只读

组件事件：`@confirm="(config) => ..."`，config 形如 `{ mode: 'single' | 'recurring', intervalType, intervalValue, dayOfMonth, dayOfWeek, totalPeriods }`

Props: `:visible`, `:initial-config`

- [ ] **Step 2: 手工在 `AddRecord.vue` 加按钮调起测试**（见 Task 13）

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/snapledger/AdvancedSettingsSheet.vue
git commit -m "feat(frontend): add advanced settings sheet"
```

---

### Task 13: `AddRecord.vue` 集成

**Files:**
- Modify: `frontend/src/views/snapledger/AddRecord.vue`

- [ ] **Step 1: 在页面合适位置加 "高级设置" 按钮，默认显示 "单次"，点击打开 AdvancedSettingsSheet**

- [ ] **Step 2: 保存记录时分支：**
  - `mode === 'single'`: 走原有 `POST /api/snapledger/record`
  - `mode === 'recurring'`: 走 `POST /api/snapledger/recurring-events`（带上周期配置 + 当前表单里的 name/amount/account/category 等）
  - `mode === 'installment'`: 占位 Toast

- [ ] **Step 3: 本地打开 App，手工验证创建周期事件成功**
访问 http://localhost:5173/ → 记账 → 填表 → 点"单次"→ 选周期 → 保存

- [ ] **Step 4: 提交**

```bash
git commit -am "feat(frontend): integrate advanced settings into AddRecord"
```

---

### Task 14: 列表页改造 `RecurringEvents.vue`

**Files:**
- Modify: `frontend/src/views/snapledger/RecurringEvents.vue`

- [ ] **Step 1: 实现两 Tab + 卡片列表**
- Tabs: 进行中 (count) / 已结束 (count)
- 两次请求 `listRecurringEvents('ACTIVE')` 和 `listRecurringEvents('ENDED')`
- 每个卡片：图标（按 recordType 决定图标类型）、name、副标题（#{nextPeriodNumber} / 期限 / mainCategory）、金额（支出红/收入绿）、下次发生日期
- 卡片 click → 跳 `/snapledger/recurring-events/{id}`

- [ ] **Step 2: 验证**

浏览器访问列表页，确认数据显示正确。

- [ ] **Step 3: 提交**

```bash
git commit -am "feat(frontend): implement recurring events list"
```

---

### Task 15: 详情页 `RecurringEventDetail.vue`

**Files:**
- Create: `frontend/src/views/snapledger/RecurringEventDetail.vue`
- Create: `frontend/src/components/snapledger/RecordActionSheet.vue`
- Modify: `frontend/src/router.js`

- [ ] **Step 1: 在 `router.js` 加路由**

```js
{
  path: '/snapledger/recurring-events/:id',
  name: 'RecurringEventDetail',
  component: () => import('@/views/snapledger/RecurringEventDetail.vue'),
  meta: { module: 'snapledger', transition: 'page-slide' }
}
```

- [ ] **Step 2: 实现详情页**

结构：
```
<PageHeader title="事件详情">
  <template #right><MoreMenu :actions="[结束事件, 删除事件]" /></template>
</PageHeader>
<EventSummaryCard :event="event" />
<PeriodStats :total :elapsed :remaining />
<RecordList :records long-press → RecordActionSheet />
```

长按 record → 弹 `RecordActionSheet`，按钮：
- **编辑**：二级弹窗三选项
  - 修改此记录 → 跳 `AddRecord?recordId=X` 编辑模式
  - 修改整个周期事件 → 跳"编辑事件"页（可复用 AddRecord + 高级设置）
  - 修改连同未来周期 → 同上但调 `updateFromPeriod`
- **删除**：确认后删 record
- **复制**：Toast "功能开发中"
- **退款**：Toast "功能开发中"

- [ ] **Step 3: 实现事件级操作（结束/删除）**

- 结束：确认弹窗 → 调 `endRecurringEvent(id)` → 返回列表
- 删除：确认弹窗 → 调 `deleteRecurringEvent(id)` → 返回列表

- [ ] **Step 4: 浏览器全流程验证**

1. 新增记录页创建周期事件
2. 列表页看到卡片
3. 进详情页看到期数列表
4. 长按一条 record 弹操作卡片
5. 编辑三选项分别能触发对应 API
6. 结束事件后列表页从"进行中"移到"已结束"

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/snapledger/RecurringEventDetail.vue \
        frontend/src/components/snapledger/RecordActionSheet.vue \
        frontend/src/router.js
git commit -m "feat(frontend): add recurring event detail page"
```

---

## Phase 6 — 集成验证

### Task 16: 端到端回归

- [ ] **Step 1: 后端全量测试**
Run: `cd panda-api && mvn test`
Expected: 全部 PASS

- [ ] **Step 2: 浏览器手工回归**
- 创建无限期月度事件 → 详情页能看到 36 条
- 创建 12 期有限事件 → 详情页看到 12 条
- 预置一条同名历史 record → 创建事件后历史被挂上
- 编辑三种模式分别测试
- 结束事件 + 删除事件分别测试

- [ ] **Step 3: 检查 CLAUDE.md 是否需要补 "Recurring Events" 章节**（可选）

- [ ] **Step 4: 最终提交**

```bash
git commit --allow-empty -m "chore(snapledger): recurring events feature complete"
```

---

## 关键简化/YAGNI 提醒

- **不引入 record `status` 字段**：所有生成的 record 都是正式记录
- **不做复制 / 退款按钮逻辑**：Toast 占位即可
- **"入账方式"暂只支持立即入账**：弹窗中显示只读
- **分期 Tab 只占位**：不做实现
- **不做"从历史自动发现事件"功能**：YAGNI

## 执行参考

- 数据库每次启动由 JPA 自动建表/加列（当前配置 `ddl-auto=update`，若非 update 需确认）。若失败需手写 `V__xx.sql` migration 并放 `app-snapledger/src/main/resources/db/migration/`
- 测试都在 `panda-api/src/test/`，使用 `@SpringBootTest(classes = PandaApplication.class)`
- 前端新增页面需检查 `SnapTabbar.vue` 或相应入口是否有链接（列表页入口已存在于 `More.vue`）
