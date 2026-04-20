# 应收应付款项 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页"应收应付款项"虚拟账户从硬编码 `-37842.18` 切换为基于 CSV 导入的真实数据计算，并新增二级页面：借出/借入事件列表、三态筛选、新增收/还款、手动新增主记录。

**Architecture:** 在 `Record` 上加 `parentRecordId` 自引用字段表达"借出↔收款"父子关系；CSV 导入末尾启发式 FIFO 建立历史父子关系；新增 `ReceivableController` 暴露 list/summary/create/addChild 接口；前端新增 `Receivables.vue` 列表页 + `ReceivableChildForm.vue` 新增收/还款表单。

**Tech Stack:** Spring Boot 3.2 (Java 17) + JPA/MySQL · Vue 3 Composition API + Vite · Vant 移动端 UI。Spec: `docs/superpowers/specs/2026-04-20-receivables-payables-design.md`

---

## Phase 1：只读展示 + 首页数字

### Task 1: DB migration 添加 `parent_record_id` 字段

**Files:**
- Create: `app-snapledger/src/main/resources/db/migration/V7__add_record_parent_id.sql`

- [ ] **Step 1: 写 migration SQL**

```sql
ALTER TABLE sl_record ADD COLUMN parent_record_id BIGINT NULL;
CREATE INDEX idx_record_parent ON sl_record(parent_record_id);
```

- [ ] **Step 2: 重启后端（用户负责），确认 Flyway 应用成功**

```bash
# 用户自己启动后端
# 观察日志：Successfully applied 1 migration to schema `snapledger_db`, now at version v7
```

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/resources/db/migration/V7__add_record_parent_id.sql
git commit -m "feat(snapledger): add parent_record_id column for receivables linkage"
```

---

### Task 2: `Record` 实体加字段

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java`

- [ ] **Step 1: 加 `parentRecordId` 字段**

在 `installmentPeriodNumber` 字段之后、`createdAt` 之前插入：

```java
@Column(name = "parent_record_id")
private Long parentRecordId;
```

- [ ] **Step 2: Compile 验证**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java
git commit -m "feat(snapledger): Record.parentRecordId field"
```

---

### Task 3: `RecordRepository` 新增查询方法

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java`

- [ ] **Step 1: 加方法**

```java
List<Record> findByParentRecordId(Long parentRecordId);

List<Record> findByParentRecordIdIn(List<Long> parentIds);

@Query("SELECT r FROM Record r WHERE r.recordType IN ('应收款项','应付款项') " +
       "AND r.parentRecordId IS NULL ORDER BY r.date DESC, r.time DESC")
List<Record> findAllReceivableParents();

@Query("SELECT r FROM Record r WHERE r.recordType IN ('应收款项','应付款项') " +
       "ORDER BY r.account, r.name, r.recordType, r.date, r.time")
List<Record> findAllReceivableForLinking();

@Modifying
@Query("UPDATE Record r SET r.parentRecordId = NULL " +
       "WHERE r.recordType IN ('应收款项','应付款项')")
int clearAllReceivableParentLinks();
```

确保 `@Modifying` import 存在：`import org.springframework.data.jpa.repository.Modifying;`

- [ ] **Step 2: Compile**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
```

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java
git commit -m "feat(snapledger): repository queries for receivable parent linkage"
```

---

### Task 4: `ReceivableLinkingService` 启发式 FIFO 建链 —— 写测试

**Files:**
- Test: `panda-api/src/test/java/com/panda/snapledger/service/receivable/ReceivableLinkingServiceTest.java`

测试在 `panda-api/src/test/java/` 下，遵循项目测试约定（只有 `panda-api` 依赖所有模块）。

- [ ] **Step 1: 写失败测试**

```java
package com.panda.snapledger.service.receivable;

import com.panda.PandaApplication;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class ReceivableLinkingServiceTest {

    @Autowired RecordRepository recordRepository;
    @Autowired ReceivableLinkingService linkingService;

    @BeforeEach
    void cleanup() {
        recordRepository.deleteAll();
    }

    private Record saveRecord(String account, String name, String recordType,
                              String subCategory, BigDecimal amount,
                              LocalDate date, LocalTime time) {
        Record r = new Record();
        r.setAccount(account);
        r.setName(name);
        r.setRecordType(recordType);
        r.setSubCategory(subCategory);
        r.setAmount(amount);
        r.setDate(date);
        r.setTime(time);
        return recordRepository.save(r);
    }

    @Test
    void lendOnceRepayPartial_oneParentOneChildWith1500Paid() {
        Record parent = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-5000"), LocalDate.of(2022, 4, 26), LocalTime.of(18, 26));
        Record child = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("1500"), LocalDate.of(2022, 5, 4), LocalTime.of(10, 54));

        linkingService.linkAll();

        Record p2 = recordRepository.findById(parent.getId()).orElseThrow();
        Record c2 = recordRepository.findById(child.getId()).orElseThrow();
        assertThat(p2.getParentRecordId()).isNull();
        assertThat(c2.getParentRecordId()).isEqualTo(parent.getId());
    }

    @Test
    void lendOnceRepaidFully_twoChildren() {
        Record p = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-4000"), LocalDate.of(2022, 11, 28), LocalTime.of(19, 15));
        Record c1 = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("2000"), LocalDate.of(2022, 11, 28), LocalTime.of(19, 17));
        Record c2 = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("2000"), LocalDate.of(2022, 11, 29), LocalTime.of(19, 17));

        linkingService.linkAll();

        assertThat(recordRepository.findById(c1.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
        assertThat(recordRepository.findById(c2.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
    }

    @Test
    void borrowRecord_parentPositiveChildNegative() {
        Record p = saveRecord("招商银行°", "我妈借我", "应付款项", "借入",
                new BigDecimal("3500"), LocalDate.of(2022, 5, 19), LocalTime.of(18, 12));
        Record c = saveRecord("招商银行°", "我妈借我", "应付款项", "借入",
                new BigDecimal("-1000"), LocalDate.of(2022, 6, 1), LocalTime.of(10, 0));

        linkingService.linkAll();

        assertThat(recordRepository.findById(c.getId()).orElseThrow().getParentRecordId())
                .isEqualTo(p.getId());
    }

    @Test
    void idempotent_reLinkDoesNotDuplicate() {
        Record parent = saveRecord("招商银行°", "借给阿芳°", "应收款项", "借出",
                new BigDecimal("-2000"), LocalDate.of(2023, 1, 7), LocalTime.of(14, 20));

        linkingService.linkAll();
        linkingService.linkAll();

        List<Record> all = recordRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(recordRepository.findById(parent.getId()).orElseThrow().getParentRecordId()).isNull();
    }

    @Test
    void mortgageSubCategoryExcluded_fromHeuristicPairing() {
        // 房贷类即使存在"反向金额"也不配对（每期独立）
        Record p1 = saveRecord("房产°", "商贷", "应付款项", "房贷",
                new BigDecimal("2985.34"), LocalDate.of(2025, 7, 20), LocalTime.of(18, 15));
        Record p2 = saveRecord("中信银行", "商贷", "应付款项", "房贷",
                new BigDecimal("-2985.34"), LocalDate.of(2025, 7, 20), LocalTime.of(23, 59));

        linkingService.linkAll();

        // 两条都应保持为主记录（账户不同本来就不同分组，这里验证不被启发式关联）
        assertThat(recordRepository.findById(p1.getId()).orElseThrow().getParentRecordId()).isNull();
        assertThat(recordRepository.findById(p2.getId()).orElseThrow().getParentRecordId()).isNull();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd panda-api && mvn test -Dtest=ReceivableLinkingServiceTest -q
```

Expected: `FAIL` — "cannot resolve ReceivableLinkingService"

- [ ] **Step 3: Commit 测试**

```bash
git add panda-api/src/test/java/com/panda/snapledger/service/receivable/ReceivableLinkingServiceTest.java
git commit -m "test(snapledger): failing tests for receivable linking heuristic"
```

---

### Task 5: `ReceivableLinkingService` 实现

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableLinkingService.java`

- [ ] **Step 1: 写实现**

```java
package com.panda.snapledger.service.receivable;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 应收应付款项父子关系启发式建链。
 * 导入 CSV 后调用 linkAll()，基于 (账户, 名称, recordType) 分组 + FIFO 匹配。
 */
@Slf4j
@Service
public class ReceivableLinkingService {

    /** 不参与 FIFO 启发式的子类别（每期独立主记录） */
    private static final Set<String> NON_PAIRING_SUB_CATEGORIES =
            Set.of("房贷", "车贷", "信贷", "利息");

    private final RecordRepository recordRepository;

    public ReceivableLinkingService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void linkAll() {
        int cleared = recordRepository.clearAllReceivableParentLinks();
        log.info("Cleared {} existing receivable parent links", cleared);

        List<Record> all = recordRepository.findAllReceivableForLinking();

        // 分组：(account|name|recordType) -> 按时间升序的 records
        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record r : all) {
            if (isNonPairing(r)) continue;
            String key = groupKey(r);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        int linkedCount = 0;
        for (List<Record> group : groups.values()) {
            linkedCount += processGroup(group);
        }
        log.info("Linked {} receivable child records across {} groups", linkedCount, groups.size());
    }

    private boolean isNonPairing(Record r) {
        return NON_PAIRING_SUB_CATEGORIES.contains(r.getSubCategory());
    }

    private String groupKey(Record r) {
        return (r.getAccount() == null ? "" : r.getAccount())
                + "|" + (r.getName() == null ? "" : r.getName())
                + "|" + r.getRecordType();
    }

    /**
     * 对同组内按时间顺序遍历：主方向金额压入队列，子方向金额按 FIFO 扣减队首主记录。
     * 主方向：应收款项=负 / 应付款项=正
     * 子方向相反。
     */
    private int processGroup(List<Record> group) {
        int linked = 0;
        Deque<PendingParent> queue = new ArrayDeque<>();
        for (Record r : group) {
            boolean isParentDirection = isParentDirection(r);
            if (isParentDirection) {
                queue.add(new PendingParent(r, r.getAmount().abs()));
                continue;
            }
            BigDecimal remaining = r.getAmount().abs();
            if (queue.isEmpty()) {
                // 兜底：子方向无主 → 自成主记录
                log.warn("Orphan child (no parent in queue): id={} name={} amount={}",
                        r.getId(), r.getName(), r.getAmount());
                continue;
            }
            PendingParent head = queue.peek();
            r.setParentRecordId(head.parent.getId());
            recordRepository.save(r);
            linked++;
            head.remaining = head.remaining.subtract(remaining);
            if (head.remaining.signum() <= 0) {
                queue.poll();
            }
        }
        return linked;
    }

    private boolean isParentDirection(Record r) {
        if ("应收款项".equals(r.getRecordType())) {
            return r.getAmount().signum() < 0;
        }
        // 应付款项
        return r.getAmount().signum() > 0;
    }

    private static class PendingParent {
        final Record parent;
        BigDecimal remaining;
        PendingParent(Record p, BigDecimal r) { parent = p; remaining = r; }
    }
}
```

- [ ] **Step 2: 跑测试**

```bash
cd panda-api && mvn test -Dtest=ReceivableLinkingServiceTest -q
```

Expected: 所有测试 PASS

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableLinkingService.java
git commit -m "feat(snapledger): ReceivableLinkingService FIFO heuristic"
```

---

### Task 6: `MozeCsvImporter` 末尾调用 linkAll

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/csvimport/MozeCsvImporter.java`

- [ ] **Step 1: 注入依赖并调用**

在构造器中加入 `ReceivableLinkingService`：

```java
private final ReceivableLinkingService receivableLinkingService;

public MozeCsvImporter(...,  // 现有所有依赖
                       InstallmentDetectionService installmentDetectionService,
                       ReceivableLinkingService receivableLinkingService) {
    // ...
    this.receivableLinkingService = receivableLinkingService;
}
```

在 `installmentDetectionService.detectAll();` 的 catch 块之后、`return new ImportResult(...)` 之前添加：

```java
// === 步骤6：建立应收应付父子关系（清空重建，幂等）===
try {
    receivableLinkingService.linkAll();
} catch (Exception e) {
    log.warn("应收应付建链失败: {}", e.getMessage(), e);
}
```

- [ ] **Step 2: Compile + 跑现有 `MozeCsvImporterTest`**

```bash
cd panda-api && mvn test -Dtest=MozeCsvImporterTest -q
```

Expected: PASS（现有测试不应受影响）

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/csvimport/MozeCsvImporter.java
git commit -m "feat(snapledger): wire receivable linking into CSV import pipeline"
```

---

### Task 7: `ReceivableResponse` + `ReceivableSummaryResponse` DTO

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableResponse.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableChildDto.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableSummaryResponse.java`

- [ ] **Step 1: `ReceivableResponse.java`**

```java
package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class ReceivableResponse {
    private Long id;
    private String recordType;     // 应收款项 / 应付款项
    private String subCategory;    // 借出/借入/房贷/...
    private String name;
    private String account;
    private String target;
    private BigDecimal amount;     // 主记录带符号金额
    private BigDecimal absAmount;  // |amount|
    private BigDecimal paidAmount; // Σ|children|
    private BigDecimal remaining;  // absAmount - paidAmount
    private LocalDate date;
    private LocalTime time;
    private String status;         // IN_PROGRESS / NOT_STARTED / COMPLETED
    private List<ReceivableChildDto> children;
    private Long recurringEventId;
    private Integer periodNumber;

    public static ReceivableResponse of(Record parent, List<Record> children, String status) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ReceivableResponse.builder()
                .id(parent.getId())
                .recordType(parent.getRecordType())
                .subCategory(parent.getSubCategory())
                .name(parent.getName())
                .account(parent.getAccount())
                .target(parent.getTarget())
                .amount(parent.getAmount())
                .absAmount(abs)
                .paidAmount(paid)
                .remaining(abs.subtract(paid).max(BigDecimal.ZERO))
                .date(parent.getDate())
                .time(parent.getTime())
                .status(status)
                .children(children.stream().map(ReceivableChildDto::of).toList())
                .recurringEventId(parent.getRecurringEventId())
                .periodNumber(parent.getPeriodNumber())
                .build();
    }
}
```

- [ ] **Step 2: `ReceivableChildDto.java`**

```java
package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ReceivableChildDto {
    private Long id;
    private BigDecimal amount;     // 带符号
    private BigDecimal absAmount;
    private String account;
    private LocalDate date;
    private LocalTime time;
    private String description;

    public static ReceivableChildDto of(Record r) {
        return ReceivableChildDto.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .absAmount(r.getAmount().abs())
                .account(r.getAccount())
                .date(r.getDate())
                .time(r.getTime())
                .description(r.getDescription())
                .build();
    }
}
```

- [ ] **Step 3: `ReceivableSummaryResponse.java`**

```java
package com.panda.snapledger.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReceivableSummaryResponse {
    private BigDecimal netAmount;      // 进行中+未开始 的净应付金额（应付为负，应收为正）
    private Integer inProgressCount;
    private Integer notStartedCount;
    private Integer completedCount;
}
```

- [ ] **Step 4: Compile**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
```

- [ ] **Step 5: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableResponse.java \
        app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableChildDto.java \
        app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReceivableSummaryResponse.java
git commit -m "feat(snapledger): DTOs for receivables responses"
```

---

### Task 8: `ReceivableService` + 状态判定

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java`

- [ ] **Step 1: 实现服务**

```java
package com.panda.snapledger.service.receivable;

import com.panda.snapledger.controller.dto.ReceivableResponse;
import com.panda.snapledger.controller.dto.ReceivableSummaryResponse;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class ReceivableService {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final RecordRepository recordRepository;

    public ReceivableService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<ReceivableResponse> list(String status, String target) {
        List<Record> parents = recordRepository.findAllReceivableParents();
        Map<Long, List<Record>> childrenByParent = groupChildrenByParent(parents);
        LocalDateTime now = LocalDateTime.now();

        List<ReceivableResponse> result = new ArrayList<>();
        for (Record p : parents) {
            List<Record> children = childrenByParent.getOrDefault(p.getId(), List.of());
            String s = computeStatus(p, children, now);
            if (!s.equals(status)) continue;
            if (target != null && !matchTarget(p, target)) continue;
            result.add(ReceivableResponse.of(p, children, s));
        }
        return result;
    }

    public ReceivableSummaryResponse summary() {
        List<Record> parents = recordRepository.findAllReceivableParents();
        Map<Long, List<Record>> childrenByParent = groupChildrenByParent(parents);
        LocalDateTime now = LocalDateTime.now();

        BigDecimal net = BigDecimal.ZERO;
        int inProgress = 0, notStarted = 0, completed = 0;
        for (Record p : parents) {
            List<Record> children = childrenByParent.getOrDefault(p.getId(), List.of());
            String s = computeStatus(p, children, now);
            switch (s) {
                case STATUS_COMPLETED -> completed++;
                case STATUS_NOT_STARTED -> { notStarted++; net = net.add(signedRemaining(p, children)); }
                case STATUS_IN_PROGRESS -> { inProgress++; net = net.add(signedRemaining(p, children)); }
            }
        }
        return ReceivableSummaryResponse.builder()
                .netAmount(net)
                .inProgressCount(inProgress)
                .notStartedCount(notStarted)
                .completedCount(completed)
                .build();
    }

    private Map<Long, List<Record>> groupChildrenByParent(List<Record> parents) {
        List<Long> ids = parents.stream().map(Record::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<Record>> m = new HashMap<>();
        for (Record c : recordRepository.findByParentRecordIdIn(ids)) {
            m.computeIfAbsent(c.getParentRecordId(), k -> new ArrayList<>()).add(c);
        }
        return m;
    }

    /** 带符号的剩余金额：应付为负、应收为正 */
    private BigDecimal signedRemaining(Record parent, List<Record> children) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = abs.subtract(paid).max(BigDecimal.ZERO);
        // 应付 → 负，应收 → 正
        return "应付款项".equals(parent.getRecordType()) ? remaining.negate() : remaining;
    }

    public String computeStatus(Record parent, List<Record> children, LocalDateTime now) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(abs) >= 0) return STATUS_COMPLETED;

        LocalDate date = parent.getDate();
        LocalTime time = parent.getTime() == null ? LocalTime.MIN : parent.getTime();
        LocalDateTime parentDt = LocalDateTime.of(date, time);
        return parentDt.isAfter(now) ? STATUS_NOT_STARTED : STATUS_IN_PROGRESS;
    }

    private boolean matchTarget(Record parent, String target) {
        String t = parent.getTarget();
        if (target.isEmpty()) return t == null || t.isEmpty() || "不限定对象".equals(t);
        return target.equals(t);
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
```

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java
git commit -m "feat(snapledger): ReceivableService status computation & list/summary"
```

---

### Task 9: `ReceivableController` list + summary

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/ReceivableController.java`

- [ ] **Step 1: 控制器**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.ReceivableResponse;
import com.panda.snapledger.controller.dto.ReceivableSummaryResponse;
import com.panda.snapledger.service.receivable.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/receivables")
@CrossOrigin(origins = "*")
public class ReceivableController {

    private final ReceivableService receivableService;

    public ReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    @GetMapping
    @Operation(summary = "应收应付主记录列表")
    public List<ReceivableResponse> list(
            @RequestParam(defaultValue = "IN_PROGRESS") String status,
            @RequestParam(required = false) String target) {
        return receivableService.list(status, target);
    }

    @GetMapping("/summary")
    @Operation(summary = "应收应付汇总（用于首页虚拟账户）")
    public ReceivableSummaryResponse summary() {
        return receivableService.summary();
    }
}
```

- [ ] **Step 2: 启动后端，验证接口**

```bash
# 用户重启后端，然后手工调用验证：
curl "http://localhost:9090/api/snapledger/receivables/summary"
curl "http://localhost:9090/api/snapledger/receivables?status=IN_PROGRESS"
```

Expected: `summary.netAmount` 接近 `-37842.18`（允许小幅偏差，误差大需排查）。

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/ReceivableController.java
git commit -m "feat(snapledger): ReceivableController list + summary endpoints"
```

---

### Task 10: 前端 API 客户端

**Files:**
- Create: `frontend/src/api/snapledger/receivable.js`

- [ ] **Step 1: API 封装**

```js
import request from '@/utils/request'

export function getReceivables(status = 'IN_PROGRESS', target) {
  return request.get('/api/snapledger/receivables', { params: { status, target } })
}

export function getReceivablesSummary() {
  return request.get('/api/snapledger/receivables/summary')
}

export function addReceivableChild(parentId, payload) {
  return request.post(`/api/snapledger/receivables/${parentId}/children`, payload)
}

export function createReceivable(payload) {
  return request.post('/api/snapledger/receivables', payload)
}

export function deleteReceivable(parentId) {
  return request.delete(`/api/snapledger/receivables/${parentId}`)
}

export function deleteReceivableChild(childId) {
  return request.delete(`/api/snapledger/receivables/children/${childId}`)
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/snapledger/receivable.js
git commit -m "feat(frontend): receivables API client"
```

---

### Task 11: 路由挂载 + 首页入口跳转

**Files:**
- Modify: `frontend/src/router.js`
- Modify: `frontend/src/views/snapledger/Home.vue`

- [ ] **Step 1: 在 router.js 加路由**

在 snapledger 模块路由数组中加入：

```js
{
  path: '/snapledger/receivables',
  name: 'Receivables',
  component: () => import('@/views/snapledger/Receivables.vue'),
  meta: { module: 'snapledger', title: '应收应付款项' }
}
```

- [ ] **Step 2: Home.vue 虚拟账户改为调 summary**

在 `<script setup>` 顶部：
```js
import { getReceivablesSummary } from '@/api/snapledger/receivable'
```

在 `onMounted` 中加 summary 调用：
```js
onMounted(async () => {
  try {
    const [accountsRes, summaryRes] = await Promise.all([
      getAccounts(),
      getReceivablesSummary().catch(() => ({ netAmount: 0 }))
    ])
    accounts.value = accountsRes || []
    VIRTUAL_ACCOUNTS[0].balance = Number(summaryRes.netAmount) || 0
  } catch (e) {
    console.error('Failed to load accounts:', e)
  } finally {
    loading.value = false
  }
})
```

将 `VIRTUAL_ACCOUNTS[0].balance` 的硬编码值 `-37842.18` 改为 `0`（加载前临时值）。

**虚拟账户点击跳转**：定位到现有处理点击账户的函数（可能叫 `handleAccountClick` 或类似），增加：

```js
const handleVirtualClick = (acc) => {
  if (acc.id === 'virtual-receivable-payable') {
    router.push('/snapledger/receivables')
  }
}
```

在 template 里的账户行点击绑定对应判断（若已有 `isVirtual` 的 if-else，改为：`isVirtual` 时也要允许 `virtual-receivable-payable` 跳转）。

- [ ] **Step 3: 前端验证**

```
用户打开浏览器 → /snapledger → 首页应收应付款项显示接口返回金额
点击该行 → 跳 /snapledger/receivables（页面空白是正常，下一步加组件）
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router.js frontend/src/views/snapledger/Home.vue
git commit -m "feat(frontend): wire receivables summary into Home; add route"
```

---

### Task 12: `ReceivableRow.vue` 组件

**Files:**
- Create: `frontend/src/components/snapledger/ReceivableRow.vue`

- [ ] **Step 1: 组件**

```vue
<template>
  <div class="receivable-row" :class="{ selected }" @click="$emit('click', item)">
    <div class="icon" :style="{ background: iconBg }">
      <span class="iconfont">{{ iconGlyph }}</span>
    </div>
    <div class="content">
      <div class="timestamp">{{ formatDateTime(item.date, item.time) }}</div>
      <div class="name">{{ item.name || '(未命名)' }}</div>
      <div class="subtitle">{{ subtitle }}</div>
    </div>
    <div class="right">
      <div class="status-text">{{ statusText }}</div>
      <div class="amount" :class="amountClass">¥{{ formatAmount(item.absAmount) }}</div>
      <div class="target-badge" v-if="item.account">{{ item.account }}</div>
    </div>
    <div class="check-circle" :class="{ active: selected }">
      <span v-if="selected">✓</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})
defineEmits(['click'])

const SUB_CATEGORY_META = {
  '借出':  { bg: '#8fd7c6', glyph: '↗' },
  '代付':  { bg: '#8fd7c6', glyph: '⤴' },
  '报账':  { bg: '#8fd7c6', glyph: '▢' },
  '借入':  { bg: '#d78fa8', glyph: '↙' },
  '信贷':  { bg: '#d78fa8', glyph: '▭' },
  '车贷':  { bg: '#d78fa8', glyph: '⛟' },
  '房贷':  { bg: '#d78fa8', glyph: '⌂' },
  '利息':  { bg: '#d78fa8', glyph: '?' }
}

const meta = computed(() => SUB_CATEGORY_META[props.item.subCategory] || { bg: '#ccc', glyph: '·' })
const iconBg = computed(() => meta.value.bg)
const iconGlyph = computed(() => meta.value.glyph)

const isReceivable = computed(() => props.item.recordType === '应收款项')

const amountClass = computed(() => {
  if (props.item.status === 'COMPLETED') return 'completed'
  return isReceivable.value ? 'positive' : 'negative'
})

const statusText = computed(() => {
  if (props.item.status === 'COMPLETED') return '已完成'
  const paid = Number(props.item.paidAmount) || 0
  if (paid > 0) {
    return isReceivable.value
      ? `已收款 ¥${formatAmount(paid)}`
      : `已还款 ¥${formatAmount(paid)}`
  }
  return isReceivable.value ? '尚未收款' : '尚未还款'
})

const subtitle = computed(() => {
  if (props.item.recurringEventId) {
    const target = props.item.target || '不限定对象'
    return `周期 #${props.item.periodNumber} / 无限期 · ${target}`
  }
  return props.item.target || '不限定对象'
})

function formatDateTime(date, time) {
  if (!date) return ''
  const d = new Date(date)
  const wd = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const dateStr = `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} 周${wd}`
  return time ? `${dateStr} ${time.substring(0, 5)}` : dateStr
}

function formatAmount(n) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.receivable-row { display: grid; grid-template-columns: 48px 1fr auto 24px; gap: 12px; padding: 12px 16px; align-items: center; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.receivable-row.selected { background: #eaf4ff; }
.icon { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 20px; }
.content { min-width: 0; }
.timestamp { font-size: 12px; color: #999; }
.name { font-size: 16px; font-weight: 500; margin-top: 2px; }
.subtitle { font-size: 12px; color: #999; margin-top: 2px; }
.right { text-align: right; }
.status-text { font-size: 12px; color: #999; }
.amount { font-size: 18px; font-weight: 500; margin-top: 2px; }
.amount.positive { color: #52c41a; }
.amount.negative { color: #f56c6c; }
.amount.completed { color: #ccc; text-decoration: line-through; }
.target-badge { display: inline-block; margin-top: 4px; padding: 1px 8px; border: 1px solid #8fc8e8; border-radius: 10px; font-size: 11px; color: #5a9bc8; }
.check-circle { width: 20px; height: 20px; border-radius: 50%; border: 1.5px solid #ccc; display: flex; align-items: center; justify-content: center; font-size: 12px; color: #fff; }
.check-circle.active { background: #4a90e2; border-color: #4a90e2; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/snapledger/ReceivableRow.vue
git commit -m "feat(frontend): ReceivableRow component"
```

---

### Task 13: `Receivables.vue` 列表页（只读，含 3 Tab）

**Files:**
- Create: `frontend/src/views/snapledger/Receivables.vue`

- [ ] **Step 1: 页面**

```vue
<template>
  <div class="receivables-page">
    <!-- 顶部 -->
    <div class="header">
      <button class="back-btn" @click="goBack">‹</button>
      <div class="title">应收应付款项</div>
      <button class="filter-btn" disabled>☰</button>
    </div>

    <!-- Tab -->
    <div class="tabs">
      <div
        v-for="t in tabs" :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="changeTab(t.value)">{{ t.label }}</div>
    </div>

    <!-- chip -->
    <div class="chips">
      <span class="chip active">全部</span>
      <span class="chip">不限定对象</span>
    </div>

    <!-- 汇总 -->
    <div class="summary-row" v-if="!loading && items.length > 0">
      <span>— 不限定对象 ({{ items.length }})</span>
      <span class="summary-amount" :class="summaryClass">
        {{ formatSignedAmount(totalSigned) }}
      </span>
    </div>

    <!-- 列表 -->
    <div class="list" v-if="!loading">
      <ReceivableRow
        v-for="item in items" :key="item.id"
        :item="item"
        :selected="selectedId === item.id"
        @click="handleRowClick(item)"
      />
      <div class="empty" v-if="items.length === 0">暂无记录</div>
    </div>
    <div v-else class="loading">加载中…</div>

    <!-- 底部操作栏 -->
    <div class="action-bar" v-if="selectedItem && selectedItem.status === 'IN_PROGRESS'">
      <span class="action-text">{{ actionLabel }} {{ formatSignedAmount(actionAmountSigned) }}</span>
      <button class="action-btn" @click="goAddChild">+</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getReceivables } from '@/api/snapledger/receivable'
import ReceivableRow from '@/components/snapledger/ReceivableRow.vue'

const router = useRouter()
const TAB_KEY = 'snap.receivables.activeTab'
const tabs = [
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '已完成', value: 'COMPLETED' }
]

const activeTab = ref(sessionStorage.getItem(TAB_KEY) || 'IN_PROGRESS')
const items = ref([])
const loading = ref(false)
const selectedId = ref(null)

const selectedItem = computed(() => items.value.find(i => i.id === selectedId.value))

const totalSigned = computed(() => {
  return items.value.reduce((sum, item) => {
    const remaining = Number(item.remaining) || 0
    return sum + (item.recordType === '应付款项' ? -remaining : remaining)
  }, 0)
})
const summaryClass = computed(() => totalSigned.value < 0 ? 'negative' : 'positive')

const actionLabel = computed(() =>
  selectedItem.value?.recordType === '应收款项' ? '新增收款' : '新增还款')
const actionAmountSigned = computed(() => {
  if (!selectedItem.value) return 0
  const r = Number(selectedItem.value.remaining) || 0
  return selectedItem.value.recordType === '应付款项' ? -r : r
})

async function load() {
  loading.value = true
  try {
    items.value = await getReceivables(activeTab.value)
  } catch (e) {
    console.error(e)
    items.value = []
  } finally {
    loading.value = false
  }
}

function changeTab(v) {
  activeTab.value = v
  sessionStorage.setItem(TAB_KEY, v)
  selectedId.value = null
  load()
}

function handleRowClick(item) {
  if (activeTab.value !== 'IN_PROGRESS') return
  selectedId.value = selectedId.value === item.id ? null : item.id
}

function goAddChild() {
  if (!selectedItem.value) return
  router.push(`/snapledger/receivables/${selectedItem.value.id}/new-child`)
}

function goBack() { router.back() }

function formatSignedAmount(n) {
  const v = Number(n || 0)
  const sign = v < 0 ? '−' : (v > 0 ? '+' : '')
  return `${sign}¥${Math.abs(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

onMounted(load)
watch(activeTab, load)
</script>

<style scoped>
.receivables-page { padding-bottom: 80px; background: #fff; min-height: 100vh; }
.header { display: flex; align-items: center; padding: 12px 16px; gap: 8px; }
.back-btn, .filter-btn { width: 40px; height: 40px; border-radius: 50%; border: none; background: #f5f5f5; font-size: 20px; cursor: pointer; }
.title { flex: 1; text-align: center; font-size: 18px; font-weight: 500; }
.tabs { display: flex; border-bottom: 1px solid #f0f0f0; }
.tab { flex: 1; text-align: center; padding: 12px; color: #999; }
.tab.active { color: #333; font-weight: 500; border-bottom: 2px solid #4a90e2; }
.chips { padding: 12px 16px; display: flex; gap: 8px; }
.chip { padding: 4px 12px; background: #f0f0f0; border-radius: 12px; font-size: 13px; }
.chip.active { background: #d0e8ff; color: #4a90e2; }
.summary-row { display: flex; justify-content: space-between; padding: 8px 16px; font-size: 15px; color: #666; border-bottom: 1px solid #eee; }
.summary-amount.negative { color: #f56c6c; font-weight: 500; }
.summary-amount.positive { color: #52c41a; font-weight: 500; }
.list { }
.empty { text-align: center; color: #999; padding: 40px; }
.loading { text-align: center; color: #999; padding: 40px; }
.action-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 16px; background: #fff; border-top: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 -2px 8px rgba(0,0,0,0.05); }
.action-text { font-size: 16px; }
.action-btn { width: 40px; height: 40px; border-radius: 50%; background: #4a90e2; color: #fff; border: none; font-size: 24px; cursor: pointer; }
</style>
```

- [ ] **Step 2: 前端手工验证**

```
访问 /snapledger/receivables → 应能看到进行中主记录列表
切换 Tab → 列表应变化
点击行 → 勾选 + 底部浮现"新增收款 +¥X"（进行中 Tab 时）
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/Receivables.vue
git commit -m "feat(frontend): Receivables list page with 3 tabs"
```

---

## Phase 2：新增收款/还款

### Task 14: `CreateReceivableChildRequest` DTO

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CreateReceivableChildRequest.java`

- [ ] **Step 1: DTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateReceivableChildRequest {
    private String account;     // 收/还款账户名
    private BigDecimal amount;  // 绝对值（正数）
    private LocalDate date;
    private LocalTime time;
    private String description;
}
```

- [ ] **Step 2: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CreateReceivableChildRequest.java
git commit -m "feat(snapledger): CreateReceivableChildRequest DTO"
```

---

### Task 15: `ReceivableService.addChild()` —— 测试先行

**Files:**
- Modify: `panda-api/src/test/java/com/panda/snapledger/service/receivable/ReceivableServiceTest.java` (create)

- [ ] **Step 1: 写失败测试**

```java
package com.panda.snapledger.service.receivable;

import com.panda.PandaApplication;
import com.panda.snapledger.controller.dto.CreateReceivableChildRequest;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class ReceivableServiceTest {

    @Autowired RecordRepository recordRepository;
    @Autowired ReceivableService receivableService;

    @BeforeEach
    void clean() { recordRepository.deleteAll(); }

    private Record saveParent(String recordType, BigDecimal amount) {
        Record r = new Record();
        r.setAccount("招商银行°");
        r.setName("借给阿芳°");
        r.setRecordType(recordType);
        r.setSubCategory("应收款项".equals(recordType) ? "借出" : "借入");
        r.setAmount(amount);
        r.setDate(LocalDate.now().minusDays(10));
        r.setTime(LocalTime.NOON);
        return recordRepository.save(r);
    }

    @Test
    void addChildToReceivable_childHasNegativeSignOfReceivableIsPositive() {
        Record parent = saveParent("应收款项", new BigDecimal("-5000"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("1500"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        Record child = receivableService.addChild(parent.getId(), req);

        assertThat(child.getParentRecordId()).isEqualTo(parent.getId());
        assertThat(child.getAmount()).isEqualByComparingTo("1500");
        assertThat(child.getRecordType()).isEqualTo("应收款项");
        assertThat(child.getName()).isEqualTo("借给阿芳°");
    }

    @Test
    void addChildToPayable_childAmountIsNegative() {
        Record parent = saveParent("应付款项", new BigDecimal("3500"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("1000"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        Record child = receivableService.addChild(parent.getId(), req);

        assertThat(child.getAmount()).isEqualByComparingTo("-1000");
    }

    @Test
    void rejectOverpayment() {
        Record parent = saveParent("应收款项", new BigDecimal("-100"));

        CreateReceivableChildRequest req = new CreateReceivableChildRequest();
        req.setAccount("招商银行°");
        req.setAmount(new BigDecimal("200"));
        req.setDate(LocalDate.now());
        req.setTime(LocalTime.of(10, 0));

        assertThatThrownBy(() -> receivableService.addChild(parent.getId(), req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd panda-api && mvn test -Dtest=ReceivableServiceTest -q
```

Expected: FAIL —— `addChild` 不存在

- [ ] **Step 3: Commit 测试**

```bash
git add panda-api/src/test/java/com/panda/snapledger/service/receivable/ReceivableServiceTest.java
git commit -m "test(snapledger): addChild behavior"
```

---

### Task 16: `ReceivableService.addChild()` 实现

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java`

- [ ] **Step 1: 实现**

在 `ReceivableService` 类中增加方法：

```java
@Transactional
public Record addChild(Long parentId, CreateReceivableChildRequest req) {
    Record parent = recordRepository.findById(parentId)
            .orElseThrow(() -> new IllegalArgumentException("主记录不存在: " + parentId));

    if (!List.of("应收款项", "应付款项").contains(parent.getRecordType())) {
        throw new IllegalArgumentException("只能对应收应付主记录新增收/还款");
    }
    if (parent.getParentRecordId() != null) {
        throw new IllegalArgumentException("不能向子记录再添加子记录");
    }

    BigDecimal absNew = req.getAmount().abs();
    List<Record> existingChildren = recordRepository.findByParentRecordId(parentId);
    BigDecimal paid = existingChildren.stream()
            .map(c -> c.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal abs = parent.getAmount().abs();
    if (paid.add(absNew).compareTo(abs) > 0) {
        throw new IllegalArgumentException("本次金额超过剩余应收应付金额");
    }

    boolean isReceivable = "应收款项".equals(parent.getRecordType());
    BigDecimal signedAmount = isReceivable ? absNew : absNew.negate();

    Record child = new Record();
    child.setParentRecordId(parentId);
    child.setAccount(req.getAccount() != null ? req.getAccount() : parent.getAccount());
    child.setName(parent.getName());
    child.setRecordType(parent.getRecordType());
    child.setMainCategory(parent.getMainCategory());
    child.setSubCategory(parent.getSubCategory());
    child.setTarget(parent.getTarget());
    child.setAmount(signedAmount);
    child.setDate(req.getDate() != null ? req.getDate() : LocalDate.now());
    child.setTime(req.getTime() != null ? req.getTime() : LocalTime.now());
    child.setDescription(req.getDescription());
    return recordRepository.save(child);
}
```

需要 import `com.panda.snapledger.controller.dto.CreateReceivableChildRequest` 和 `org.springframework.transaction.annotation.Transactional`。

- [ ] **Step 2: 跑测试**

```bash
cd panda-api && mvn test -Dtest=ReceivableServiceTest -q
```

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java
git commit -m "feat(snapledger): ReceivableService.addChild implementation"
```

---

### Task 17: Controller 暴露 addChild + 删除

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/ReceivableController.java`

- [ ] **Step 1: 增加端点**

```java
@PostMapping("/{parentId}/children")
@Operation(summary = "新增收款或还款")
public Record addChild(@PathVariable Long parentId,
                       @RequestBody CreateReceivableChildRequest req) {
    return receivableService.addChild(parentId, req);
}

@DeleteMapping("/{parentId}")
@Operation(summary = "删除主记录（级联删除子记录）")
public void delete(@PathVariable Long parentId) {
    receivableService.deleteParent(parentId);
}

@DeleteMapping("/children/{childId}")
@Operation(summary = "删除子记录")
public void deleteChild(@PathVariable Long childId) {
    receivableService.deleteChild(childId);
}
```

在 `ReceivableService` 加：

```java
@Transactional
public void deleteParent(Long parentId) {
    Record parent = recordRepository.findById(parentId).orElseThrow();
    recordRepository.findByParentRecordId(parentId).forEach(recordRepository::delete);
    recordRepository.delete(parent);
}

@Transactional
public void deleteChild(Long childId) {
    Record child = recordRepository.findById(childId).orElseThrow();
    if (child.getParentRecordId() == null)
        throw new IllegalArgumentException("该记录不是子记录");
    recordRepository.delete(child);
}
```

- [ ] **Step 2: Compile**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
```

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/ReceivableController.java \
        app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java
git commit -m "feat(snapledger): addChild/delete endpoints for receivables"
```

---

### Task 18: `ReceivableChildForm.vue` 新增收/还款表单

**Files:**
- Create: `frontend/src/views/snapledger/ReceivableChildForm.vue`
- Modify: `frontend/src/router.js` —— 加子路由

- [ ] **Step 1: 添加路由**

```js
{
  path: '/snapledger/receivables/:parentId/new-child',
  name: 'ReceivableChildForm',
  component: () => import('@/views/snapledger/ReceivableChildForm.vue'),
  meta: { module: 'snapledger', title: '新增收/还款' }
}
```

- [ ] **Step 2: 组件实现**

```vue
<template>
  <div class="child-form-page" v-if="parent">
    <div class="header">
      <button class="back-btn" @click="close">×</button>
      <div class="title">{{ titleText }}</div>
      <button class="submit-btn" @click="submit" :disabled="!canSubmit">✓</button>
    </div>

    <div class="parent-card">
      <div class="icon" :style="{ background: isReceivable ? '#8fd7c6' : '#d78fa8' }">
        <span>¤</span>
      </div>
      <div class="parent-name">{{ parent.name }}</div>
      <div class="parent-amount" :class="isReceivable ? 'green' : 'red'">
        ¥{{ formatAmount(parent.absAmount) }}
      </div>
    </div>

    <div class="fields">
      <input class="amount-input" v-model="form.amount" type="number" step="0.01" placeholder="金额" />
      <input class="name-input" v-model.lazy="nameDisplay" disabled />
      <select v-model="form.account">
        <option v-for="a in accounts" :key="a.id" :value="a.name">{{ a.name }}</option>
      </select>
      <input type="date" v-model="form.date" />
      <input type="time" v-model="form.time" />
      <textarea v-model="form.description" placeholder="备注"></textarea>
    </div>

    <div class="footer">
      剩余款项：¥{{ formatAmount(remaining) }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReceivables, addReceivableChild } from '@/api/snapledger/receivable'
import { getAccounts } from '@/api'

const route = useRoute()
const router = useRouter()

const parent = ref(null)
const accounts = ref([])
const form = ref({
  amount: '',
  account: '',
  date: new Date().toISOString().substring(0, 10),
  time: new Date().toTimeString().substring(0, 5),
  description: ''
})

const isReceivable = computed(() => parent.value?.recordType === '应收款项')
const titleText = computed(() => isReceivable.value ? '新增收款' : '新增还款')
const nameDisplay = computed(() => parent.value?.name || '')
const remaining = computed(() => {
  const r = Number(parent.value?.remaining) || 0
  const amt = Number(form.value.amount) || 0
  return Math.max(0, r - amt)
})
const canSubmit = computed(() => {
  const a = Number(form.value.amount)
  return a > 0 && a <= Number(parent.value?.remaining || 0)
})

async function load() {
  const parentId = Number(route.params.parentId)
  // 从三个 Tab 中找到 parent（简化：从进行中查）
  const list = await getReceivables('IN_PROGRESS')
  parent.value = list.find(i => i.id === parentId)
  if (!parent.value) {
    alert('主记录不存在或已完成')
    router.back()
    return
  }
  form.value.amount = parent.value.remaining
  accounts.value = (await getAccounts()) || []
  form.value.account = parent.value.account
}

async function submit() {
  if (!canSubmit.value) return
  try {
    await addReceivableChild(parent.value.id, {
      account: form.value.account,
      amount: Number(form.value.amount),
      date: form.value.date,
      time: form.value.time + ':00',
      description: form.value.description
    })
    router.back()
  } catch (e) {
    alert('提交失败：' + (e.message || e))
  }
}

function close() { router.back() }

function formatAmount(n) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(load)
</script>

<style scoped>
.child-form-page { background: #fff; min-height: 100vh; padding-bottom: 60px; }
.header { display: flex; padding: 12px 16px; align-items: center; }
.back-btn, .submit-btn { width: 40px; height: 40px; border-radius: 50%; border: none; background: #f5f5f5; font-size: 20px; }
.submit-btn:disabled { opacity: 0.4; }
.title { flex: 1; text-align: center; font-size: 18px; }
.parent-card { text-align: center; padding: 20px; }
.parent-card .icon { width: 60px; height: 60px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 28px; }
.parent-name { margin-top: 8px; font-size: 14px; }
.parent-amount { font-size: 16px; }
.parent-amount.green { color: #52c41a; }
.parent-amount.red { color: #f56c6c; }
.fields { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.fields input, .fields select, .fields textarea { padding: 12px; border: 1px solid #eee; border-radius: 8px; font-size: 16px; }
.amount-input { font-size: 24px !important; }
.footer { position: fixed; bottom: 0; left: 0; right: 0; padding: 16px; text-align: center; color: #999; border-top: 1px solid #eee; background: #fff; }
</style>
```

- [ ] **Step 3: 前端验证**

```
列表页 → 点行选中 → 点底部"+" → 表单显示主记录信息
金额默认填 remaining → 修改金额 → 提交 → 应回到列表
主记录 paidAmount 应增加、remaining 减少
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/snapledger/ReceivableChildForm.vue frontend/src/router.js
git commit -m "feat(frontend): ReceivableChildForm page for creating receipts/repayments"
```

---

## Phase 3：手动新增主记录 + 筛选

### Task 19: `CreateReceivableRequest` DTO + Service/Controller

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CreateReceivableRequest.java`
- Modify: `ReceivableService`, `ReceivableController`

- [ ] **Step 1: DTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateReceivableRequest {
    private String recordType;   // 应收款项 / 应付款项
    private String subCategory;  // 借出/借入/房贷/...
    private String name;
    private String account;
    private BigDecimal amount;   // 绝对值
    private LocalDate date;
    private LocalTime time;
    private String target;
    private String description;
}
```

- [ ] **Step 2: Service.createParent()**

```java
@Transactional
public Record createParent(CreateReceivableRequest req) {
    if (!List.of("应收款项", "应付款项").contains(req.getRecordType())) {
        throw new IllegalArgumentException("recordType 必须是应收款项或应付款项");
    }
    boolean isReceivable = "应收款项".equals(req.getRecordType());
    BigDecimal abs = req.getAmount().abs();
    BigDecimal signed = isReceivable ? abs.negate() : abs;

    Record r = new Record();
    r.setRecordType(req.getRecordType());
    r.setSubCategory(req.getSubCategory());
    r.setName(req.getName());
    r.setAccount(req.getAccount());
    r.setAmount(signed);
    r.setDate(req.getDate() != null ? req.getDate() : LocalDate.now());
    r.setTime(req.getTime() != null ? req.getTime() : LocalTime.now());
    r.setTarget(req.getTarget());
    r.setDescription(req.getDescription());
    return recordRepository.save(r);
}
```

- [ ] **Step 3: Controller.create**

```java
@PostMapping
@Operation(summary = "手动新增应收应付主记录")
public Record create(@RequestBody CreateReceivableRequest req) {
    return receivableService.createParent(req);
}
```

- [ ] **Step 4: Compile + Commit**

```bash
cd panda-api && mvn -pl ../app-snapledger compile -q
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/CreateReceivableRequest.java \
        app-snapledger/src/main/java/com/panda/snapledger/service/receivable/ReceivableService.java \
        app-snapledger/src/main/java/com/panda/snapledger/controller/ReceivableController.java
git commit -m "feat(snapledger): createParent endpoint for manual receivable entry"
```

---

### Task 20: 前端 `AddRecord.vue` 支持应收/应付子类别

**Files:**
- Modify: `frontend/src/views/snapledger/AddRecord.vue`

- [ ] **Step 1: 调研现有结构**

查看 `AddRecord.vue` 当前如何根据 `recordType` 切换表单字段；理解 tab 切换逻辑。

- [ ] **Step 2: 添加子类别选择**

当 tab 为"应收款项" → 子类别 chip：`借出 / 代付 / 报账`（默认借出）
当 tab 为"应付款项" → 子类别 chip：`借入 / 信贷 / 车贷 / 房贷 / 利息`（默认借入）

提交时调 `createReceivable(payload)`（从 `api/snapledger/receivable.js`），替换原"应收/应付"走 `createRecord` 的路径。

**注意**：保留现有字段（金额、账户、名称、日期、对象、备注）。

- [ ] **Step 3: 前端验证**

```
新增记录 → 切 Tab 到"应收款项" → 选"借出" → 填名称/金额/账户 → 提交
→ 跳到 Receivables.vue 看到新记录显示在"进行中"
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/snapledger/AddRecord.vue
git commit -m "feat(frontend): add subcategory picker for receivables in AddRecord"
```

---

### Task 21: 筛选按钮 + 对象 chip 切换

**Files:**
- Modify: `frontend/src/views/snapledger/Receivables.vue`

- [ ] **Step 1: 顶部筛选按钮激活**

右上角漏斗按钮点击 → 弹底部半屏弹窗（参考 Vant `van-action-sheet`）：
- 子类别多选
- 时间范围（开始、结束日期）

筛选前端过滤（后端 list 已拉回对应 Tab 全量，前端 reactive filter）。

- [ ] **Step 2: "不限定对象" chip 切换**

点击 chip 打开对象选择下拉（从 items 派生 distinct `target`），切换后过滤列表。

- [ ] **Step 3: 前端验证 + Commit**

```
筛选 → 列表应按条件过滤
chip → 切换对象组后列表只显示该对象
```

```bash
git add frontend/src/views/snapledger/Receivables.vue
git commit -m "feat(frontend): receivables filter panel and target chip switching"
```

---

## 验证清单（完工后）

- [ ] **首页虚拟账户金额** ≈ `-37842.18`（允许小幅偏差）
- [ ] **三 Tab 切换** 数量合理：进行中 26、未开始 2、已完成若干
- [ ] **选中某条借出 → 新增收款 500 → 刷新** 该条变成 `已收款 ¥500`、`remaining` 减少
- [ ] **收款达到主金额** 自动进入 `已完成` Tab
- [ ] **手动新增应收 500** 出现在 `进行中` 顶部
- [ ] **单元测试**：`ReceivableLinkingServiceTest` + `ReceivableServiceTest` 全绿
- [ ] **回归**：现有 `MozeCsvImporterTest` 不受影响
- [ ] **Memory 更新**：在 `MEMORY.md` 增加 `feature_receivables.md` 引用，记录这个功能的关键点（见下）

### Memory 条目

完工后在 `memory/` 加一条：

```markdown
---
name: 应收应付款项功能要点
description: Receivables/payables feature — 父子 Record 模型、状态三态、CSV 启发式建链
type: project
---

# 应收应付款项

**数据模型**：`Record.parentRecordId` 自引用字段（V7 migration）。
- 主记录：`recordType ∈ {应收款项, 应付款项} AND parentRecordId IS NULL`
- 子记录：parentRecordId 指向主

**状态判定**（ReceivableService.computeStatus）：
- COMPLETED：子记录金额合计 ≥ 主记录金额
- NOT_STARTED：date > now
- IN_PROGRESS：其他

**CSV 启发式**：MozeCsvImporter 末尾调 ReceivableLinkingService.linkAll()，按 (account, name, recordType) 分组 FIFO 匹配。
子类别 ∈ {房贷, 车贷, 信贷, 利息} 不参与启发式（每期独立）。

**Why**：首页"应收应付款项"虚拟账户金额从硬编码切换为真实计算，对齐 Moze app 二级页面。
```

---

**Plan 完。** 建议用 `superpowers:subagent-driven-development` 执行；每个 Task 结束都 commit。
