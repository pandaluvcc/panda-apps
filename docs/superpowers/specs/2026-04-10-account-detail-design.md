# 账户详情页设计文档

**日期：** 2026-04-10  
**模块：** Snap Ledger — 账户详情  
**状态：** 已确认

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
| 返回 | `router.back()` |

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
| 信用卡（isCreditAccount=true） | 以 `billCycleStart` / `billCycleEnd` 月份为基准，前后翻月 | `< 2026/04/03 - 2026/05/02 >` |
| 普通账户 | 自然月（当月1日至最后一天） | `< 2026年04月 >` |

### 3.2 统计区字段

| 字段 | 信用卡 | 普通账户 | 计算方式 |
|------|--------|---------|---------|
| 新增支出 / 本期支出 | ✓ | ✓（标签改为"本期支出"） | 当期 `recordType=支出` 金额绝对值之和 |
| 上期欠款 | ✓ | ✗ | 上一账单周期的"仍需还款"值，无则为 0 |
| 应还账单 | ✓ | ✗ | 上期欠款 + 新增支出 |
| 已还金额 | ✓ | ✗ | 当期 `recordType=转账` 且 `target=本账户名` 的金额之和 |
| 账单分期 | ✓（留位置） | ✗ | 暂不实现，显示 `---` |
| 对账笔数 | ✓ | ✓ | 当期 `reconciliationStatus=CONFIRMED` 的记录数 |
| 仍需还款 | ✓ | ✗ | 应还账单 - 已还金额 |
| 本期收入 | ✗ | ✓ | 当期 `recordType=收入` 金额之和 |

### 3.3 记录分类规则

```
还款记录 = recordType == "转账"
一般记录 = recordType != "转账"（支出、收入、建议等）

两类记录均只取：account == 本账户名 且 date 在当前账单周期内的数据
```

### 3.4 记录行展示格式

**日期分组头：**
```
2025/07/19 周六 01:40
```

**还款记录行：**
```
[子类别图标]  信用卡还款                    ￥2361.51
              招行朝朝宝° → 招商银行信用卡   [还款]
```

- 左：子类别图标
- 中上（1/2）：主类别名称（"信用卡还款"）
- 中下（1/2）：转账方向（`account → target`）
- 右上：金额
- 右下：`[还款]` 小椭圆标签

**一般记录行：**
```
[子类别图标]  早餐              ￥3.80
              喜士多            [生活开支] [招商银行信用卡]
```

- 左：子类别图标
- 中上（1/2）：子类别名称
- 中下（1/2）：商家名称（`merchant` 字段）
- 右上：金额
- 右下：项目标签（`project`）+ 账户名标签（小椭圆）

### 3.5 Section 头部

```
还款记录（N）                    +
一般记录（N）   [排序图标]        +
```

- `+` 按钮 → 跳转到新增记录页，预填当前账户
- 排序图标（一般记录）→ 按日期升序/降序切换
- `N` = 当前周期内该类型记录数量

---

## 4. 账户信息 Tab

- 进入时调用 `GET /api/snapledger/accounts/:id`，回填表单
- 表单字段与新增账户页完全相同：名称、主币种、账户分组、初始余额、账单周期、信用账户 / 自动转存 / 国外手续费 / 纳入总余额开关
- 保存时调用 `PUT /api/snapledger/accounts/:id`
- 表单逻辑从 AddAccount.vue 提取为 `composables/useAccountForm.js` 复用

---

## 5. 前端文件变更

### 新增
| 文件 | 说明 |
|------|------|
| `src/views/snapledger/AccountDetail.vue` | 主页面，包含两个 Tab |
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
| `TransactionSummaryDTO.java` | 新增 `previousDebt`、`billAmount`、`paidAmount`、`remainingDebt`、`confirmedCount` |
| `AccountService.java` | `getPeriodSummary` 扩展：信用卡账户计算上期欠款、应还账单、已还金额、仍需还款；所有账户计算 `confirmedCount` |

### 复用（无需修改）
| 接口 | 用途 |
|------|------|
| `GET /api/snapledger/accounts/:id` | 加载账户信息 Tab 数据 |
| `GET /api/snapledger/accounts/:id/transactions` | 加载全量记录（前端分类） |
| `GET /api/snapledger/accounts/:id/summary?startDate=&endDate=` | 加载统计数据 |
| `PUT /api/snapledger/accounts/:id` | 保存账户信息 Tab 修改 |

---

## 7. 数据流

```
进入详情页
  ├─ GET /accounts/:id            账户基本信息，计算初始周期
  ├─ GET /:id/transactions        全量记录（前端按周期+类型过滤分类）
  └─ GET /:id/summary?start&end   周期统计

切换账单周期
  ├─ 前端重新过滤 transactions
  └─ 重新请求 summary

账户信息 Tab 保存
  └─ PUT /accounts/:id → 成功提示
```

---

## 8. 不在本期范围内

- 账单分期字段逻辑（留位置，显示 `---`）
- 记录行点击进入编辑
- 对账操作（CONFIRM / POSTPONE）入口
