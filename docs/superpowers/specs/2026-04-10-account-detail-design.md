# 账户详情页设计文档

**日期：** 2026-04-10  
**模块：** Snap Ledger — 账户详情  
**状态：** 已确认（v2）

---

## 1. 需求概述

账户总览页中每个账户支持点击进入详情页。详情页分两个 Tab：

- **交易明细 Tab**：按账单周期展示记账记录及统计汇总
- **账户信息 Tab**：可编辑的账户配置表单

---

## 2. 路由与页面结构

| 项目 | 值 |
|------|-----|
| 路由 | `/snap/account/:id` |
| 组件 | `frontend/src/views/snapledger/AccountDetail.vue` |
| 入口 | Home.vue 账户行点击 → `router.push('/snap/account/' + acc.id)` |
| 返回 | `router.back()`，若账户信息 Tab 有未保存改动则弹确认框 |

页面布局：
```
┌─────────────────────────────┐
│  ← 账户名称                  │  顶部导航栏
├─────────────────────────────┤
│  [ 交易明细 ]  [ 账户信息 ]   │  Tab 切换
├─────────────────────────────┤
│  Tab 内容区（可滚动）          │
└─────────────────────────────┘
```

---

## 3. 交易明细 Tab

### 3.1 账单周期导航

| 账户类型 | 周期规则 | 导航格式 |
|---------|---------|---------|
| 信用卡（isCreditAccount=true） | 以 `billCycleStart` / `billCycleEnd` 对应的日（day-of-month）为基准，每次翻一个自然月 | `< 2026/04/03 - 2026/05/02 >` |
| 普通账户 | 自然月（当月1日至最后一天） | `< 2026年04月 >` |

**周期计算规则（信用卡）：**
- `billCycleStart` 存储为 `LocalDate`，取其 day-of-month（如 3）
- 当前周期 start = 本月第3天，end = 下月第2天
- 向前/向后翻页 = start 月份 -1 / +1，end 随之联动
- 跨年正常处理（如 12 月 28 日 → 1 月 27 日）
- 不限制导航到未来周期，未来周期数据为空时显示空态

**默认周期：**
- 进入页面时默认显示**包含今天的周期**

### 3.2 统计区字段

| 字段 | 信用卡 | 普通账户 | 计算方式 |
|------|--------|---------|---------|
| 新增支出 / 本期支出 | ✓ | ✓（标签"本期支出"） | 当期内 `recordType=支出` 金额绝对值之和 |
| 上期欠款 | ✓ | ✗ | 对上一周期调用 summary 取其 `remainingDebt`；首个周期为 0 |
| 应还账单 | ✓ | ✗ | 上期欠款 + 新增支出 |
| 已还金额 | ✓ | ✗ | 当期内 `recordType=转账` 且 `target=本账户名` 的金额绝对值之和 |
| 账单分期 | ✓（留位置） | ✗ | 暂不实现，显示 `---` |
| 对账笔数 | ✓ | ✓ | 当期内 `reconciliationStatus=CONFIRMED` 的记录数 |
| 仍需还款 | ✓ | ✗ | 应还账单 - 已还金额（最小为 0） |
| 本期收入 | ✗ | ✓ | 当期内 `recordType=收入` 金额之和 |

**上期欠款获取方式：**  
前端将上一周期的 `startDate/endDate` 传给 summary 接口，从返回的 `remainingDebt` 字段取值。首次查询若接口返回 0 或无数据则上期欠款为 0。

**统计排除规则：** 所有统计字段均排除 `reconciliationStatus=POSTPONED` 的记录（延后入账记录不计入当期）。

**recordType 约定：** 系统全程使用中文类型值：`"收入"`、`"支出"`、`"转账"`，后端判断逻辑统一使用中文。

### 3.3 记录查询与分类规则

**查询范围（按周期过滤，传 startDate/endDate 给后端）：**
```
转账记录查询条件：
  (account == 本账户名 OR target == 本账户名)
  AND date BETWEEN startDate AND endDate
  AND recordType == "转账"

一般记录查询条件：
  account == 本账户名
  AND date BETWEEN startDate AND endDate
  AND recordType != "转账"
```

> 转账记录需双向展示：从本账户转出（account=本账户）和转入本账户（target=本账户）都应显示，前端根据方向显示不同样式。

**前端分类：**
```
转账记录（含还款）= recordType == "转账"
一般记录          = recordType != "转账"
```

### 3.4 记录行展示格式

**日期分组头：**
```
2025/07/19 周六 01:40
```

**转账记录行（含还款）：**
```
[子类别图标]  信用卡还款                    ￥2361.51
              招行朝朝宝° → 招商银行信用卡   [还款]
```

- 左：子类别图标
- 中上（1/2）：主类别名称（如"信用卡还款"）
- 中下（1/2）：转账方向（`account → target`）
- 右上：金额（绝对值）
- 右下：`[还款]` 小椭圆标签

**一般记录行：**
```
[子类别图标]  早餐              ￥3.80
              喜士多            [生活开支] [招商银行信用卡]
```

- 左：子类别图标
- 中上（1/2）：子类别名称
- 中下（1/2）：商家名称（`merchant` 字段，无则留空）
- 右上：金额
- 右下：项目标签（`project`）+ 账户名标签（小椭圆，无则不显示）

**空态：** 当前周期无记录时，显示"本周期暂无记录"提示。

### 3.5 Section 头部

```
转账记录（N）                    +
一般记录（N）   [排序图标]        +
```

- `+` 按钮 → 跳转到新增记录页，URL 携带 `?accountId=:id` 预填账户
- 排序图标（一般记录）→ 按日期升序/降序切换，默认倒序
- `N` = 当前周期该类型记录数量

---

## 4. 账户信息 Tab

- 进入时调用 `GET /api/snapledger/accounts/:id`，回填表单
- 表单字段与新增账户页完全相同：名称、主币种、账户分组、初始余额、账单周期、信用账户 / 自动转存 / 国外手续费 / 纳入总余额开关
- 保存时调用 `PUT /api/snapledger/accounts/:id`，成功 toast 提示，失败保留编辑内容并提示错误
- 有未保存改动时点返回，弹出确认框（复用现有 showConfirmDialog 模式）
- 表单逻辑从 AddAccount.vue 提取为 `composables/useAccountForm.js` 复用

---

## 5. 前端文件变更

### 新增
| 文件 | 说明 |
|------|------|
| `src/views/snapledger/AccountDetail.vue` | 主页面，两个 Tab |
| `src/composables/useAccountForm.js` | 账户表单逻辑（从 AddAccount 提取） |

### 修改
| 文件 | 变更内容 |
|------|---------|
| `src/api/snapledger/account.js` | 新增 `getAccount`、`updateAccount`、`getAccountSummary`、`getAccountTransactions` |
| `src/api.js` | re-export 新增 API 方法 |
| `src/views/snapledger/Home.vue` | 账户行绑定点击 → `router.push` |
| `src/views/snapledger/AddAccount.vue` | 表单逻辑抽取，引用 `useAccountForm` |
| `src/router.js` | 新增 `/snap/account/:id` 路由 |

---

## 6. 后端变更

### 修改
| 文件 | 变更内容 |
|------|---------|
| `TransactionDTO.java` | 新增 `account`（来源账户名）、`target`（目标账户名）、`project`（项目标签）三个字段 |
| `TransactionSummaryDTO.java` | 新增 `previousDebt`、`billAmount`、`paidAmount`、`remainingDebt`、`confirmedCount` |
| `AccountService.java` | `getPeriodSummary` 扩展信用卡统计（recordType 使用中文"收入"/"支出"/"转账"）；`getTransactions` 增加 startDate/endDate 参数和双向转账查询 |
| `RecordRepository.java` | 新增按周期双向查询转账记录的方法：`(account=X OR target=X) AND date BETWEEN start AND end` |

### 接口清单
| 接口 | 说明 | 变更 |
|------|------|------|
| `GET /accounts/:id` | 账户基本信息 | 无 |
| `GET /accounts/:id/transactions?startDate=&endDate=` | 按周期查询记录（含双向转账） | 新增日期参数 |
| `GET /accounts/:id/summary?startDate=&endDate=` | 周期统计（扩展信用卡字段） | 扩展返回字段 |
| `PUT /accounts/:id` | 保存账户信息修改 | 无 |

---

## 7. 数据流

```
进入详情页
  ├─ GET /accounts/:id            账户信息，计算默认周期（含今日的周期）
  ├─ GET /:id/transactions?start&end   按周期查询记录（前端分类）
  └─ GET /:id/summary?start&end        当期统计（含上期欠款需额外一次请求上期 summary）

切换账单周期
  ├─ GET /:id/transactions?新start&end
  └─ GET /:id/summary?新start&end（含上期 summary 请求）

账户信息 Tab 保存
  └─ PUT /accounts/:id → toast 成功/失败
```

---

## 8. 不在本期范围内

- 账单分期字段逻辑（留位置，显示 `---`）
- 记录行点击进入编辑
- 对账操作（CONFIRM / POSTPONE）入口
- 下拉刷新
