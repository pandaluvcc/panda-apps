# 记录类型完整分类与统计修正

## 背景

系统基于 Moze CSV 导入的 7425 条真实记录构建，但当前只正确处理了 `支出`/`收入`/`转出`/`转入` 4 种记录类型。实际数据包含 **13 种记录类型**，导致统计不准确、记录分类不完整。

## 记录类型分类矩阵

| 类别 | recordType 值 | 数量 | 金额特征 | 统计处理 |
|------|-------------|------|---------|---------|
| 支出类 | `支出`, `手续费`, `利息` | 5031 | 负数 | 计入支出 |
| 收入类 | `收入` | 401 | 正数 | 计入收入 |
| 抵扣类 | `退款`, `折扣` | 28 | 正数 | 冲减支出，不计收入 |
| 转账类 | `转出`, `转入`, `转账`, `还款`, `应付款项`, `应收款项`, `分期还款` | 1702 | 成对出现 | 排除出收支统计 |
| 特殊类 | `余额调整`, `账单分期` | 222 | 无规律 | 排除出收支统计 |

## P0：统计数据修正

### 问题

`AccountService.getPeriodSummary` 只用 `"收入".equals()` / `"支出".equals()` 判断收支：
- 手续费(17)、利息(9) 未计入支出
- 退款(27) 正金额可能被误处理
- 余额调整(221)、应付/应收(209)、分期还款(6) 未排除，污染统计

### 修复

定义常量集合统一管理：

```java
// 计入支出的 recordType
Set<String> EXPENSE_TYPES = Set.of("支出", "手续费", "利息");
// 计入收入的 recordType
Set<String> INCOME_TYPES = Set.of("收入");
// 抵扣类（冲减支出）
Set<String> OFFSET_TYPES = Set.of("退款", "折扣");
// 转账类（排除出收支）
Set<String> TRANSFER_TYPES = Set.of("转账", "还款", "转出", "转入", "应付款项", "应收款项", "分期还款");
```

**信用卡统计公式：**
```
新增支出    = sum(EXPENSE_TYPES 记录的 |amount|)
退款/折扣   = sum(OFFSET_TYPES 记录的 amount)
应还账单    = 上期欠款 + 新增支出 - 退款折扣
已还金额    = (转入本账户的转账类记录的 |amount|)
仍需还款    = 应还账单 - 已还金额
```

**普通账户统计公式：**
```
本期支出    = sum(EXPENSE_TYPES 记录的 |amount|)
本期收入    = sum(INCOME_TYPES 记录的 |amount|)
（退款显示为单独统计行，不计入收入）
```

### DTO 变更

`TransactionSummaryDTO` 新增字段：
- `refundAmount` (BigDecimal) — 退款/折扣总额

### 前端统计区

信用卡账户 stats 新增"退款/折扣"行（在"新增支出"之后）。

## P1：记录列表分类

### 问题

`RecordRepository` 的转账查询 IN 列表缺少 `应付款项`、`应收款项`、`分期还款`，导致这些成对记录出现在一般记录列表。

### 修复

所有涉及转账类型判断的 SQL 和代码统一使用 TRANSFER_TYPES 集合：

**RecordRepository.java** — 3 处 SQL：
```sql
r.recordType IN ('转账','还款','转出','转入','应付款项','应收款项','分期还款')
```

**AccountDetail.vue** — 前端分类：
```js
const TRANSFER_TYPES = new Set([
  '转账','还款','转出','转入','应付款项','应收款项','分期还款'
])
```

## P2：CSV 导入修复

### 问题

4 条脏数据：描述字段包含换行符，CSV 解析器将其拆分为独立行，产生无效记录。

### 修复

`MozeCsvImporter` 的 CSVFormat 配置启用引号内换行支持：
```java
CSVFormat.DEFAULT
  .withFirstRecordAsHeader()
  .withTrim()
  .withQuote('"')           // 支持引号内换行
  .withIgnoreEmptyLines(true)
```

## 涉及文件

| 文件 | 改动 |
|------|------|
| `AccountService.java` | getPeriodSummary 统计逻辑基于分类矩阵重写 |
| `RecordRepository.java` | 3 处 SQL IN 列表扩展至完整 TRANSFER_TYPES |
| `TransactionSummaryDTO.java` | 新增 refundAmount 字段 |
| `AccountDetail.vue` | TRANSFER_TYPES 扩展 + stats 区新增退款/折扣行 |
| `MozeCsvImporter.java` | CSV 引号换行处理 |
