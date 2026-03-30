# SnapLedger 账户管理功能设计文档

## 概述

实现 Moze 应用的账户管理后端接口，支持账户增删改查、余额实时计算、交易明细查询、对账模式等功能。

## 技术方案

| 模块 | 技术 | 说明 |
|------|------|------|
| 后端 | Java 17 + Spring Boot 3.2 | `app-snapledger` 模块 |
| 数据库 | MySQL | 扩展 `sl_account` 和 `sl_record` 表 |
| ORM | MyBatis-Plus | 复用现有仓库层 |

## 数据模型

### Account 表扩展

在现有 `sl_account` 表基础上扩展：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| icon | VARCHAR(50) | 图标 |
| balance | DECIMAL(12,2) | 当前余额（实时计算） |
| remark | VARCHAR(500) | 备注 |
| name | VARCHAR(100) | 账户名称（唯一） |
| main_currency | VARCHAR(10) | 主币种（默认 CNY） |
| account_group | VARCHAR(50) | 账户分组（可自定义） |
| initial_balance | DECIMAL(12,2) | 初始余额 |
| bill_cycle_start | DATE | 账单周期起始日 |
| bill_cycle_end | DATE | 账单周期结束日 |
| is_credit_account | TINYINT(1) | 是否为信用账户 |
| is_master_account | TINYINT(1) | 主账户 |
| cashback | DECIMAL(12,2) | 返利回馈 |
| auto_rollover | TINYINT(1) | 是否自动转存 |
| foreign_transaction_fee | TINYINT(1) | 是否国外交易手续费 |
| include_in_total | TINYINT(1) | 是否纳入总余额（默认 1） |
| is_archived | TINYINT(1) | 是否归档账户（默认 0） |
| show_on_widget | TINYINT(1) | 是否显示在 Widget/Watch |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### Record 表扩展

在现有 `sl_record` 表基础上新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| reconciliation_status | VARCHAR(20) | 对账状态：UNRECONCILED/CONFIRMED/POSTPONED |
| postponed_to_cycle | VARCHAR(10) | 延后入账的目标周期（格式：YYYY-MM） |

### RecordType 表

新增记录类型表：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| type_code | VARCHAR(20) | 类型代码：income/expense/transfer/receivable/payable/adjustment |
| type_name | VARCHAR(50) | 类型名称 |
| default_main_category | VARCHAR(50) | 默认主类别 |
| icon | VARCHAR(50) | 图标 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 核心逻辑

### 1. 余额计算

```
账户余额 = 初始余额 + Σ(收入记录金额) - Σ(支出记录金额)
```

计算规则：
- 排除 `reconciliation_status = POSTPONED` 的记录
- 仅计算该账户下的记录
- 余额字段实时计算，不持久化存储

### 2. 账单周期计算

```java
// 当期周期
当期开始 = bill_cycle_start
当期结束 = bill_cycle_end

// 下期周期
下期开始 = 当期结束 + 1 天
下期结束 = 下期开始 + 周期长度
```

周期长度计算：
- 日周期：30 天
- 周周期：30 天
- 月周期：30 天
- 年周期：365 天

### 3. 延后入账逻辑

- 更新记录 `reconciliation_status = POSTPONED`
- 设置 `postponed_to_cycle = 下期周期（YYYY-MM 格式）`
- 该记录不计入当期余额统计

### 4. 调整余额逻辑

- 创建类型为 `adjustment` 的记账记录
- 金额为用户输入的调整值（正数增加余额，负数减少余额）
- 显示在交易明细中，标记为"余额调整"

### 5. 总余额计算

```
总余额 = Σ(所有账户余额)
```

过滤条件：
- 排除 `is_archived = 1` 的账户
- 排除 `include_in_total = 0` 的账户

## API 设计

### 账户管理

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/snapledger/accounts` | GET | 获取账户列表 | 公开 |
| `/api/snapledger/accounts` | POST | 创建账户 | 公开 |
| `/api/snapledger/accounts/{id}` | PUT | 编辑账户 | 公开 |
| `/api/snapledger/accounts/{id}` | DELETE | 归档账户 | 公开 |
| `/api/snapledger/accounts/{id}/stats` | GET | 获取账户统计 | 公开 |

### 交易明细

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/snapledger/accounts/{id}/transactions` | GET | 获取交易明细（按账单周期） | 公开 |
| `/api/snapledger/accounts/{id}/transactions/summary` | GET | 获取周期统计汇总 | 公开 |

### 调整余额

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/snapledger/accounts/{id}/adjustment` | POST | 调整账户余额 | 公开 |

### 对账管理

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/snapledger/accounts/{id}/reconcile` | PUT | 批量对账（确认/延后） | 公开 |
| `/api/snapledger/accounts/{id}/reconcile/batch` | POST | 批量操作多账户 | 公开 |

## DTO 设计

### AccountDTO

```java
public class AccountDTO {
    private Long id;
    private String icon;
    private String name;
    private String accountGroup;
    private BigDecimal balance;
    private BigDecimal initialBalance;
    private String mainCurrency;
    private Date billCycleStart;
    private Date billCycleEnd;
    private Boolean isCreditAccount;
    private Boolean isMasterAccount;
    private BigDecimal cashback;
    private Boolean autoRollover;
    private Boolean foreignTransactionFee;
    private Boolean includeInTotal;
    private Boolean isArchived;
    private Boolean showOnWidget;
    private String remark;
    private Date createdAt;
    private Date updatedAt;
}
```

### TransactionDTO

```java
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
    private Date date;
    private Time time;
    private String description;
    private String reconciliationStatus;
    private Boolean isPostponed;
    private String postponedToCycle;
}
```

### TransactionSummaryDTO

```java
public class TransactionSummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalFee;
    private BigDecimal netAmount;
    private Long recordCount;
    private Date periodStart;
    private Date periodEnd;
}
```

### AdjustmentDTO

```java
public class AdjustmentDTO {
    private BigDecimal amount;
    private String description;
    private Date adjustmentDate;
}
```

### ReconciliationDTO

```java
public class ReconciliationDTO {
    private List<Long> recordIds;
    private String action; // CONFIRM | POSTPONE
    private String postponedToCycle; // YYYY-MM 格式，仅 POSTPONE 时需要
}
```

## 错误处理

| 错误码 | 说明 |
|--------|------|
| ACCOUNT_NOT_FOUND | 账户不存在 |
| ACCOUNT_ARCHIVED | 账户已归档 |
| INVALID_CYCLE | 账单周期设置无效 |
| INVALID_ADJUSTMENT | 调整金额无效 |
| TRANSACTION_NOT_FOUND | 交易记录不存在 |
| ALREADY_RECONCILED | 记录已对账 |

## 实现优先级

### P0 - 核心功能
1. Account CRUD 接口
2. 交易明细查询接口
3. 余额实时计算逻辑

### P1 - 扩展功能
1. 调整余额接口
2. 周期统计汇总接口
3. 对账接口

### P2 - 优化功能
1. 批量对账接口
2. 账户统计接口
3. 性能优化（索引、缓存）

## 数据库变更

### 新增字段

```sql
-- Account 表扩展
ALTER TABLE sl_account ADD COLUMN icon VARCHAR(50);
ALTER TABLE sl_account ADD COLUMN account_group VARCHAR(50);
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

-- Record 表扩展
ALTER TABLE sl_record ADD COLUMN reconciliation_status VARCHAR(20) DEFAULT 'UNRECONCILED';
ALTER TABLE sl_record ADD COLUMN postponed_to_cycle VARCHAR(10);

-- 新增 RecordType 表
CREATE TABLE sl_record_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(20) NOT NULL UNIQUE,
    type_name VARCHAR(50),
    default_main_category VARCHAR(50),
    icon VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 初始化数据

```sql
-- 初始化记录类型
INSERT INTO sl_record_type (type_code, type_name) VALUES
('income', '收入'),
('expense', '支出'),
('transfer', '转账'),
('receivable', '应收账款'),
('payable', '应付账款'),
('adjustment', '余额调整');
```

## 注意事项

1. **余额计算性能** - 对于大量记录，考虑使用缓存或物化视图
2. **账单周期边界** - 需要处理跨月、跨年的周期计算
3. **数据一致性** - 调整余额时需要记录操作日志
4. **归档逻辑** - 归档账户不应删除，只是标记为不可见
