# Account Management Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现账户管理的后端接口，包括账户 CRUD、余额实时计算、交易明细查询、调整余额、对账功能

**Architecture:** 扩展现有的 Account 和 Record 实体，新增对账状态字段，实现余额计算逻辑和对账逻辑，提供完整的 REST API

**Tech Stack:** Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL

---

## 文件映射

### 需要修改的文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `app-snapledger/src/main/java/com/panda/snapledger/domain/Account.java` | 修改 | 扩展字段支持 Moze 所有字段 |
| `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java` | 修改 | 新增对账状态字段 |
| `app-snapledger/src/main/java/com/panda/snapledger/repository/AccountRepository.java` | 修改 | 新增查询方法 |
| `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java` | 修改 | 新增对账状态查询方法 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AccountDTO.java` | 新建 | 账户传输对象 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionDTO.java` | 新建 | 交易明细传输对象 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionSummaryDTO.java` | 新建 | 周期统计传输对象 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AdjustmentDTO.java` | 新建 | 调整余额传输对象 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReconciliationDTO.java` | 新建 | 对账传输对象 |
| `app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java` | 新建 | 账户服务层 |
| `app-snapledger/src/main/java/com/panda/snapledger/service/AccountBalanceService.java` | 新建 | 余额计算服务 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java` | 修改 | 扩展接口 |

### 需要新增的数据库表

| 表名 | 说明 |
|------|------|
| `sl_record_type` | 记录类型表（新增 adjustment 类型） |

---

### Task 1: 扩展 Account 实体

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/domain/Account.java`

- [ ] **Step 1: 添加所有 Moze 字段到 Account 实体**

```java
package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "account_group", length = 50)
    private String accountGroup;

    @Column(name = "main_currency", length = 10)
    private String mainCurrency = "CNY";

    @Column(name = "balance", precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "initial_balance", precision = 12, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(name = "bill_cycle_start")
    private LocalDate billCycleStart;

    @Column(name = "bill_cycle_end")
    private LocalDate billCycleEnd;

    @Column(name = "is_credit_account")
    private Boolean isCreditAccount = false;

    @Column(name = "is_master_account")
    private Boolean isMasterAccount = false;

    @Column(name = "cashback", precision = 12, scale = 2)
    private BigDecimal cashback = BigDecimal.ZERO;

    @Column(name = "auto_rollover")
    private Boolean autoRollover = false;

    @Column(name = "foreign_transaction_fee")
    private Boolean foreignTransactionFee = false;

    @Column(name = "include_in_total")
    private Boolean includeInTotal = true;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "show_on_widget")
    private Boolean showOnWidget = true;

    @Column(name = "remark", length = 500)
    private String remark;

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

- [ ] **Step 2: 运行测试验证实体编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/Account.java
git commit -m "feat: extend Account entity with Moze fields"
```

---

### Task 2: 扩展 Record 实体

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java`

- [ ] **Step 1: 添加对账状态字段到 Record 实体**

```java
// 在 Record 类中添加以下字段

@Column(name = "reconciliation_status", length = 20)
private String reconciliationStatus = "UNRECONCILED";

@Column(name = "postponed_to_cycle", length = 10)
private String postponedToCycle;

// 常量定义
public static final String RECONCILIATION_UNRECONCILED = "UNRECONCILED";
public static final String RECONCILIATION_CONFIRMED = "CONFIRMED";
public static final String RECONCILIATION_POSTPONED = "POSTPONED";
```

- [ ] **Step 2: 运行测试验证实体编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java
git commit -m "feat: add reconciliation status fields to Record"
```

---

### Task 3: 创建 AccountDTO

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AccountDTO.java`

- [ ] **Step 1: 创建 AccountDTO 类**

```java
package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Account;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户传输对象
 */
@Data
public class AccountDTO {

    private Long id;
    private String icon;
    private String name;
    private String accountGroup;
    private String mainCurrency;
    private BigDecimal balance;
    private BigDecimal initialBalance;
    private LocalDate billCycleStart;
    private LocalDate billCycleEnd;
    private Boolean isCreditAccount;
    private Boolean isMasterAccount;
    private BigDecimal cashback;
    private Boolean autoRollover;
    private Boolean foreignTransactionFee;
    private Boolean includeInTotal;
    private Boolean isArchived;
    private Boolean showOnWidget;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountDTO fromEntity(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setId(account.getId());
        dto.setIcon(account.getIcon());
        dto.setName(account.getName());
        dto.setAccountGroup(account.getAccountGroup());
        dto.setMainCurrency(account.getMainCurrency());
        dto.setBalance(account.getBalance());
        dto.setInitialBalance(account.getInitialBalance());
        dto.setBillCycleStart(account.getBillCycleStart());
        dto.setBillCycleEnd(account.getBillCycleEnd());
        dto.setIsCreditAccount(account.getIsCreditAccount());
        dto.setIsMasterAccount(account.getIsMasterAccount());
        dto.setCashback(account.getCashback());
        dto.setAutoRollover(account.getAutoRollover());
        dto.setForeignTransactionFee(account.getForeignTransactionFee());
        dto.setIncludeInTotal(account.getIncludeInTotal());
        dto.setIsArchived(account.getIsArchived());
        dto.setShowOnWidget(account.getShowOnWidget());
        dto.setRemark(account.getRemark());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }
}
```

- [ ] **Step 2: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AccountDTO.java
git commit -m "feat: add AccountDTO"
```

---

### Task 4: 创建交易明细相关 DTO

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionSummaryDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AdjustmentDTO.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReconciliationDTO.java`

- [ ] **Step 1: 创建 TransactionDTO**

```java
package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 交易明细传输对象
 */
@Data
public class TransactionDTO {

    private Long id;
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
    private String description;
    private String reconciliationStatus;
    private Boolean isPostponed;
    private String postponedToCycle;

    public static TransactionDTO fromEntity(Record record) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(record.getId());
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
        dto.setDescription(record.getDescription());
        dto.setReconciliationStatus(record.getReconciliationStatus());
        dto.setIsPostponed("POSTPONED".equals(record.getReconciliationStatus()));
        dto.setPostponedToCycle(record.getPostponedToCycle());
        return dto;
    }
}
```

- [ ] **Step 2: 创建 TransactionSummaryDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易统计传输对象
 */
@Data
public class TransactionSummaryDTO {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalFee;
    private BigDecimal netAmount;
    private Long recordCount;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
```

- [ ] **Step 3: 创建 AdjustmentDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 调整余额传输对象
 */
@Data
public class AdjustmentDTO {

    private BigDecimal amount;
    private String description;
    private LocalDate adjustmentDate;
}
```

- [ ] **Step 4: 创建 ReconciliationDTO**

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 对账传输对象
 */
@Data
public class ReconciliationDTO {

    private List<Long> recordIds;
    private String action; // CONFIRM | POSTPONE
    private String postponedToCycle; // YYYY-MM 格式，仅 POSTPONE 时需要
}
```

- [ ] **Step 5: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionDTO.java \
       app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionSummaryDTO.java \
       app-snapledger/src/main/java/com/panda/snapledger/controller/dto/AdjustmentDTO.java \
       app-snapledger/src/main/java/com/panda/snapledger/controller/dto/ReconciliationDTO.java
git commit -m "feat: add transaction and reconciliation DTOs"
```

---

### Task 5: 扩展 AccountRepository

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/repository/AccountRepository.java`

- [ ] **Step 1: 添加查询方法**

```java
package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT a.name FROM Account a")
    Set<String> findAllNames();

    // 获取所有未归档且纳入总余额的账户
    List<Account> findByIsArchivedFalseAndIncludeInTotalTrue();

    // 按分组查询
    List<Account> findByIsArchivedFalseAndAccountGroupOrderByAccountGroupName(String group);

    // 获取所有未归档账户
    List<Account> findByIsArchivedFalse();
}
```

- [ ] **Step 2: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/AccountRepository.java
git commit -m "feat: add query methods to AccountRepository"
```

---

### Task 6: 扩展 RecordRepository

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java`

- [ ] **Step 1: 查看现有 RecordRepository**

先读取现有文件内容，了解已有方法

- [ ] **Step 2: 添加对账相关查询方法**

```java
// 在 RecordRepository 中添加以下方法

// 按账户和对账状态查询
List<Record> findByAccountAndReconciliationStatus(String account, String status);

// 按账户和日期范围查询（用于账单周期）
List<Record> findByAccountAndDateBetween(String account, LocalDate startDate, LocalDate endDate);

// 按账户、日期范围和对账状态查询
List<Record> findByAccountAndDateBetweenAndReconciliationStatus(
    String account, LocalDate startDate, LocalDate endDate, String status);

// 获取未对账记录
List<Record> findByReconciliationStatus(String status);

// 获取已延后入账记录
List<Record> findByReconciliationStatusAndPostponedToCycle(String status, String cycle);
```

- [ ] **Step 3: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java
git commit -m "feat: add reconciliation query methods to RecordRepository"
```

---

### Task 7: 创建 AccountBalanceService

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/AccountBalanceService.java`

- [ ] **Step 1: 创建余额计算服务**

```java
package com.panda.snapledger.service;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户余额计算服务
 */
@Service
public class AccountBalanceService {

    private final RecordRepository recordRepository;

    public AccountBalanceService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * 计算账户余额
     * 余额 = 初始余额 + 收入 - 支出
     * 排除延后入账记录
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(String account, BigDecimal initialBalance) {
        List<Record> records = recordRepository.findByAccountAndReconciliationStatusNot(
            account, Record.RECONCILIATION_POSTPONED);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (Record record : records) {
            if ("income".equals(record.getRecordType())) {
                income = income.add(record.getAmount());
            } else if ("expense".equals(record.getRecordType())) {
                expense = expense.add(record.getAmount());
            }
        }

        return initialBalance.add(income).subtract(expense);
    }

    /**
     * 更新账户余额
     */
    @Transactional
    public void updateBalance(String account, BigDecimal newBalance) {
        // 实现逻辑由 AccountService 调用
    }
}
```

- [ ] **Step 2: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/AccountBalanceService.java
git commit -m "feat: add AccountBalanceService"
```

---

### Task 8: 创建 AccountService

**Files:**
- Create: `app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java`

- [ ] **Step 1: 创建账户服务**

```java
package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.TransactionDTO;
import com.panda.snapledger.controller.dto.TransactionSummaryDTO;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 账户服务
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final RecordRepository recordRepository;
    private final AccountBalanceService balanceService;

    public AccountService(AccountRepository accountRepository,
                          RecordRepository recordRepository,
                          AccountBalanceService balanceService) {
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
        this.balanceService = balanceService;
    }

    /**
     * 获取账户列表（排除归档和未纳入总余额的账户）
     */
    public List<AccountDTO> listAccounts() {
        return accountRepository.findByIsArchivedFalseAndIncludeInTotalTrue().stream()
                .map(AccountDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取账户详情
     */
    public AccountDTO getAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));
        return AccountDTO.fromEntity(account);
    }

    /**
     * 创建账户
     */
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        Account account = new Account();
        account.setName(dto.getName());
        account.setAccountGroup(dto.getAccountGroup());
        account.setMainCurrency(dto.getMainCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBillCycleStart(dto.getBillCycleStart());
        account.setBillCycleEnd(dto.getBillCycleEnd());
        account.setIsCreditAccount(dto.getIsCreditAccount());
        account.setIsMasterAccount(dto.getIsMasterAccount());
        account.setCashback(dto.getCashback());
        account.setAutoRollover(dto.getAutoRollover());
        account.setForeignTransactionFee(dto.getForeignTransactionFee());
        account.setIncludeInTotal(dto.getIncludeInTotal());
        account.setShowOnWidget(dto.getShowOnWidget());
        account.setRemark(dto.getRemark());

        Account saved = accountRepository.save(account);
        return AccountDTO.fromEntity(saved);
    }

    /**
     * 更新账户
     */
    @Transactional
    public AccountDTO updateAccount(Long id, AccountDTO dto) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));

        account.setName(dto.getName());
        account.setAccountGroup(dto.getAccountGroup());
        account.setMainCurrency(dto.getMainCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBillCycleStart(dto.getBillCycleStart());
        account.setBillCycleEnd(dto.getBillCycleEnd());
        account.setIsCreditAccount(dto.getIsCreditAccount());
        account.setIsMasterAccount(dto.getIsMasterAccount());
        account.setCashback(dto.getCashback());
        account.setAutoRollover(dto.getAutoRollover());
        account.setForeignTransactionFee(dto.getForeignTransactionFee());
        account.setIncludeInTotal(dto.getIncludeInTotal());
        account.setShowOnWidget(dto.getShowOnWidget());
        account.setRemark(dto.getRemark());

        Account saved = accountRepository.save(account);
        return AccountDTO.fromEntity(saved);
    }

    /**
     * 归档账户
     */
    @Transactional
    public void archiveAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));
        account.setIsArchived(true);
        accountRepository.save(account);
    }

    /**
     * 调整余额
     */
    @Transactional
    public void adjustBalance(Long accountId, AdjustmentDTO dto) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        // 创建调整记录
        Record record = new Record();
        record.setAccount(account.getName());
        record.setRecordType("adjustment");
        record.setAmount(dto.getAmount());
        record.setDescription(dto.getDescription());
        record.setDate(dto.getAdjustmentDate() != null ? dto.getAdjustmentDate() : LocalDate.now());

        recordRepository.save(record);

        // 重新计算余额
        BigDecimal newBalance = balanceService.calculateBalance(account.getName(), account.getInitialBalance());
        account.setBalance(newBalance);
        accountRepository.save(account);
    }

    /**
     * 获取交易明细
     */
    public List<TransactionDTO> getTransactions(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByAccount(account.getName());
        return records.stream()
                .map(TransactionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取周期统计
     */
    public TransactionSummaryDTO getPeriodSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByAccountAndDateBetweenAndReconciliationStatusNot(
            account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;

        for (Record record : records) {
            if ("income".equals(record.getRecordType())) {
                income = income.add(record.getAmount());
            } else if ("expense".equals(record.getRecordType())) {
                expense = expense.add(record.getAmount());
            }
            fee = fee.add(record.getFee());
        }

        TransactionSummaryDTO summary = new TransactionSummaryDTO();
        summary.setTotalIncome(income);
        summary.setTotalExpense(expense);
        summary.setTotalFee(fee);
        summary.setNetAmount(income.subtract(expense));
        summary.setRecordCount((long) records.size());
        summary.setPeriodStart(startDate);
        summary.setPeriodEnd(endDate);

        return summary;
    }

    /**
     * 批量对账
     */
    @Transactional
    public void reconcile(Long accountId, com.panda.snapledger.controller.dto.ReconciliationDTO dto) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByIdIn(dto.getRecordIds());

        for (Record record : records) {
            if (!account.getName().equals(record.getAccount())) {
                throw new RuntimeException("记录不属于该账户：" + record.getId());
            }

            if ("CONFIRM".equals(dto.getAction())) {
                record.setReconciliationStatus(Record.RECONCILIATION_CONFIRMED);
            } else if ("POSTPONE".equals(dto.getAction())) {
                record.setReconciliationStatus(Record.RECONCILIATION_POSTPONED);
                record.setPostponedToCycle(dto.getPostponedToCycle());
            }

            recordRepository.save(record);
        }
    }
}
```

- [ ] **Step 2: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java
git commit -m "feat: add AccountService"
```

---

### Task 9: 扩展 AccountController

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java`

- [ ] **Step 1: 扩展控制器**

```java
package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.ReconciliationDTO;
import com.panda.snapledger.controller.dto.TransactionDTO;
import com.panda.snapledger.controller.dto.TransactionSummaryDTO;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/snapledger/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "获取所有账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取账户列表")
    })
    public List<AccountDTO> getAll() {
        return accountService.listAccounts();
    }

    @PostMapping
    @Operation(summary = "创建账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "成功创建账户"),
        @ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    public AccountDTO create(@RequestBody AccountDTO dto) {
        AccountDTO created = accountService.createAccount(dto);
        return created;
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功更新账户"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public AccountDTO update(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody AccountDTO dto) {
        return accountService.updateAccount(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "归档账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "成功归档账户"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void archive(@Parameter(description = "账户 ID") @PathVariable Long id) {
        accountService.archiveAccount(id);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "获取交易明细")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取交易明细"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public List<TransactionDTO> getTransactions(
            @Parameter(description = "账户 ID") @PathVariable Long id) {
        return accountService.getTransactions(id);
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "获取周期统计")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取统计"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public TransactionSummaryDTO getSummary(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @Parameter(description = "开始日期") @RequestParam LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam LocalDate endDate) {
        return accountService.getPeriodSummary(id, startDate, endDate);
    }

    @PostMapping("/{id}/adjustment")
    @Operation(summary = "调整余额")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功调整余额"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void adjustBalance(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody AdjustmentDTO dto) {
        accountService.adjustBalance(id, dto);
    }

    @PutMapping("/{id}/reconcile")
    @Operation(summary = "批量对账")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功对账"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void reconcile(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody ReconciliationDTO dto) {
        accountService.reconcile(id, dto);
    }
}
```

- [ ] **Step 2: 运行测试验证编译通过**

```bash
cd app-snapledger
./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交代码**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java
git commit -m "feat: extend AccountController with full CRUD and reconciliation APIs"
```

---

### Task 10: 创建数据库迁移脚本

**Files:**
- Create: `app-snapledger/src/main/resources/db/migration/V1__add_account_fields.sql`
- Create: `app-snapledger/src/main/resources/db/migration/V2__add_record_reconciliation.sql`
- Create: `app-snapledger/src/main/resources/db/migration/V3__create_record_type.sql`

- [ ] **Step 1: 创建账户字段迁移脚本**

```sql
-- V1__add_account_fields.sql

ALTER TABLE sl_account ADD COLUMN icon VARCHAR(50);
ALTER TABLE sl_account ADD COLUMN account_group VARCHAR(50);
ALTER TABLE sl_account ADD COLUMN main_currency VARCHAR(10) DEFAULT 'CNY';
ALTER TABLE sl_account ADD COLUMN initial_balance DECIMAL(12, 2) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN bill_cycle_start DATE;
ALTER TABLE sl_account ADD COLUMN bill_cycle_end DATE;
ALTER TABLE sl_account ADD COLUMN is_credit_account TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN is_master_account TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN cashback DECIMAL(12, 2) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN auto_rollover TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN foreign_transaction_fee TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN include_in_total TINYINT(1) DEFAULT 1;
ALTER TABLE sl_account ADD COLUMN is_archived TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN show_on_widget TINYINT(1) DEFAULT 1;
ALTER TABLE sl_account ADD COLUMN remark VARCHAR(500);
ALTER TABLE sl_account ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sl_account ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 更新 name 字段长度
ALTER TABLE sl_account MODIFY COLUMN name VARCHAR(100) NOT NULL UNIQUE;
```

- [ ] **Step 2: 创建对账字段迁移脚本**

```sql
-- V2__add_record_reconciliation.sql

ALTER TABLE sl_record ADD COLUMN reconciliation_status VARCHAR(20) DEFAULT 'UNRECONCILED';
ALTER TABLE sl_record ADD COLUMN postponed_to_cycle VARCHAR(10);
```

- [ ] **Step 3: 创建记录类型表迁移脚本**

```sql
-- V3__create_record_type.sql

CREATE TABLE sl_record_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(20) NOT NULL UNIQUE,
    type_name VARCHAR(50),
    default_main_category VARCHAR(50),
    icon VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化记录类型
INSERT INTO sl_record_type (type_code, type_name) VALUES
('income', '收入'),
('expense', '支出'),
('transfer', '转账'),
('receivable', '应收账款'),
('payable', '应付账款'),
('adjustment', '余额调整');
```

- [ ] **Step 4: 提交数据库脚本**

```bash
git add app-snapledger/src/main/resources/db/migration/
git commit -m "feat: add database migration scripts for account management"
```

---

### Task 11: 运行测试

**Files:**
- Test: 手动测试 API

- [ ] **Step 1: 启动应用**

```bash
cd app-snapledger
./mvnw spring-boot:run
```

Expected: Application started successfully

- [ ] **Step 2: 测试账户创建**

```bash
curl -X POST http://localhost:8080/api/snapledger/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "测试账户",
    "accountGroup": "现金",
    "mainCurrency": "CNY",
    "initialBalance": 1000,
    "billCycleStart": "2026-03-01",
    "billCycleEnd": "2026-03-31"
  }'
```

Expected: 201 Created with account data

- [ ] **Step 3: 测试账户列表**

```bash
curl http://localhost:8080/api/snapledger/accounts
```

Expected: 200 OK with account list

- [ ] **Step 4: 测试交易明细**

```bash
curl http://localhost:8080/api/snapledger/accounts/1/transactions
```

Expected: 200 OK with transaction list

- [ ] **Step 5: 测试调整余额**

```bash
curl -X POST http://localhost:8080/api/snapledger/accounts/1/adjustment \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 500,
    "description": "初始调整",
    "adjustmentDate": "2026-03-01"
  }'
```

Expected: 200 OK

- [ ] **Step 6: 提交测试报告**

```bash
git commit -m "test: verify account management APIs" --allow-empty
```

---

## 计划审查

完成所有任务后，需要运行计划审查流程：

1. 使用 `spec-document-reviewer` subagent 审查设计文档
2. 如果有问题，修复后重新审查
3. 审查通过后，通知用户进行最终确认

---

**计划完成并保存到 `docs/superpowers/plans/2026-03-31-account-management-implementation.md`。准备执行？**
