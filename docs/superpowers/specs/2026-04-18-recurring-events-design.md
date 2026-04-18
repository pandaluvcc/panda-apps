# 周期事件 (Recurring Events) 设计

**日期**: 2026-04-18
**状态**: Approved (pending spec review)
**模块**: app-snapledger

## 背景与目标

记账应用需要支持"周期事件"——用户反复发生的、规律性的记账记录（房贷、订阅、工资等）。参考 Moze App 的实现形态：

- 新增记录页面通过"高级设置"切换 单次 / 周期 / 分期
- 周期事件可无限期或指定次数
- 系统自动预生成未来的记账记录
- 事件可在"进行中 / 已结束"两种状态间转换

当前 `frontend/src/views/snapledger/RecurringEvents.vue` 只是占位页面，本次要实现完整功能。

**说明**：本次仅设计周期事件本身。CSV 导入侧后续会调用本功能的创建接口来硬编码几个固定事件，但这是 CSV 导入的独立工作，不影响本设计。

## 范围

**纳入**：
- 周期事件实体与 CRUD
- 新增记录页面的"高级设置"弹窗（单次 / 周期 Tab；分期 Tab 留待后续）
- 周期事件列表页（进行中 / 已结束）
- 周期事件详情页
- 长按 record 操作卡片（编辑三选项 + 删除）
- 按名称回溯挂接历史同名记录
- Spring Scheduler 每日滚动窗口扩展 / 事件自动结束

**不纳入**：
- 分期事件 (`InstallmentEvents.vue`) — 后续独立设计
- record 的"复制"与"退款"操作 — 仅按钮占位，点击提示"功能开发中"
- 入账方式字段 — 只做"立即入账"
- 手动确认 / 待确认状态

## 数据模型

### 新表 `recurring_event`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK auto_increment | |
| `name` | varchar(100) not null | 事件名称；同时作为历史 record 回溯匹配依据 |
| `record_type` | varchar(20) not null | `支出` / `收入` / `转账` / `应收款项` / `应付款项` |
| `amount` | decimal(14,2) not null | 模板金额 |
| `category_id` | bigint nullable | 分类（支出/收入时） |
| `sub_category_id` | bigint nullable | 子分类 |
| `account_id` | bigint not null | 主账户（转账时为转出账户） |
| `target_account_id` | bigint nullable | 转账目标账户 |
| `interval_type` | varchar(10) not null | `DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| `interval_value` | int not null default 1 | 间隔倍数（每 N 个单位） |
| `day_of_month` | int nullable | 月内指定日期 (1-31)；`MONTHLY` 使用 |
| `day_of_week` | int nullable | 周内指定日期 (1-7)；`WEEKLY` 使用 |
| `start_date` | date not null | 第 1 期发生日期 |
| `total_periods` | int nullable | `null` = 无限期；否则为有限次数 |
| `generated_until` | date not null | 已生成到哪一期的日期 |
| `status` | varchar(10) not null | `ACTIVE` / `ENDED` |
| `ended_at` | timestamp nullable | 结束时间 |
| `note` | text nullable | 备注 |
| `created_at`, `updated_at` | timestamp | 审计字段 |

索引：`idx_name`, `idx_status`, `idx_generated_until`（用于定时任务扫描）

### 现有 `record` 表新增两列

| 字段 | 类型 | 说明 |
|---|---|---|
| `recurring_event_id` | bigint nullable | 关联的周期事件；`null` 表示非周期记录 |
| `period_number` | int nullable | 第几期（从 1 开始） |

索引：`idx_recurring_event_id`

## 核心逻辑

### 记录生成策略

**有限次数事件 (`total_periods != null`)**：
- 创建事件时一次性生成全部 N 条 record
- `generated_until` 设为第 N 期日期
- 当所有期均已过 `today` 时，定时任务将事件置为 `ENDED`

**无限期事件 (`total_periods == null`)**：
- 采用滚动窗口预生成 **36 期**
- 定时任务每天检查：若 `(generated_until - today) < 6 个月`，向后续生成到 36 期窗口
- 只能手动结束

### 期数日期计算

以 `start_date` 为基准，按 `interval_type` + `interval_value` 递推：
- `MONTHLY`：`addMonths(n * interval_value)`，日期取 `day_of_month`（月内无此日取月末）
- `WEEKLY`：`addWeeks(n * interval_value)`
- `YEARLY`：`addYears(n * interval_value)`
- `DAILY`：`addDays(n * interval_value)`

### 创建事件时的历史回溯

创建事件后，按 `name` 精确匹配扫描 `record` 表中 `recurring_event_id IS NULL` 的同名记录：
- 回填 `recurring_event_id`
- 根据日期推算并回填 `period_number`（若与系统生成的期数冲突，保留历史 record 并跳过对应期数的生成）

### 修改事件的三种模式

| 模式 | 范围 | 实现 |
|---|---|---|
| 修改此记录 | 单条 record | 复用 `PUT /api/snapledger/record/{id}`，不改模板 |
| 修改整个周期事件 | 模板 + 所有未来未发生记录 | `PUT /api/snapledger/recurring-events/{id}` |
| 修改连同未来周期 | 从第 n 期（含）往后的记录 + 模板 | `PUT /api/snapledger/recurring-events/{id}/from-period/{n}` |

"未发生记录"判定：`record.event_date > today`

### 删除 vs 结束

- **结束事件** (`POST .../end`)：删除未来未到期记录；历史 record 保留（仍关联事件）；`status = ENDED`
- **删除事件** (`DELETE ...`)：删除所有未到期记录；历史 record 解绑（`recurring_event_id = null`）但保留
- **删除单条 record**：不影响事件；`record.period_number` 释放，下次窗口扩展时不会重新生成该期

## API

所有接口返回 `ApiResponse<T>` 格式。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/snapledger/recurring-events` | 创建事件 |
| GET | `/api/snapledger/recurring-events?status=ACTIVE\|ENDED` | 列表 |
| GET | `/api/snapledger/recurring-events/{id}` | 详情 + 关联记录列表 |
| PUT | `/api/snapledger/recurring-events/{id}` | 修改整个周期事件 |
| PUT | `/api/snapledger/recurring-events/{id}/from-period/{n}` | 修改连同未来周期 |
| POST | `/api/snapledger/recurring-events/{id}/end` | 结束事件 |
| DELETE | `/api/snapledger/recurring-events/{id}` | 删除事件 |

## 前端

### 1. 新增记录页 — 高级设置弹窗

现有新增记录页新增 "高级设置" 入口（按钮默认显示"单次"），点击弹出 Bottom Sheet：

- Tabs：**单次** / **周期** / **分期（占位）**
- 周期 Tab 字段：
  - 区间：每日 / 每周 / 每月 / 每年
  - 指定日期（月/周时显示）
  - 次数：无限期 / 指定 N 次
- 确定后新增记录按周期事件创建（调用 `POST /recurring-events`）

### 2. 周期事件列表页 (`RecurringEvents.vue` 改造)

- 顶部 Tab：进行中 (N) / 已结束 (N)
- 卡片：图标、名称、副标题（转账路径或 `#期数 / 期限 / 分类`）、金额、下次发生日期
- 点击卡片进入详情

### 3. 周期事件详情页（新建 `RecurringEventDetail.vue`）

- 顶部：事件元信息 + 操作按钮（结束 / 删除）
- 中部：期数概览（总期数/已发生/剩余）
- 底部：关联 record 列表，按日期倒序
- 长按 record 弹出操作卡片：
  - **编辑** → 二级弹窗三选项（修改此记录 / 修改整个周期事件 / 修改连同未来周期）
  - **删除**
  - **复制** / **退款** → 按钮占位，点击 Toast

### 4. 路由

在 `router.js` 新增：
- `/snapledger/recurring-events/:id` → `RecurringEventDetail.vue`

## 后台任务

Spring Scheduler，每天凌晨 03:00 触发一次：

1. 扫描 `status=ACTIVE` 且 `total_periods IS NULL` 的事件：若 `generated_until - today < 180 天`，向后生成至 36 期窗口
2. 扫描 `status=ACTIVE` 且 `total_periods IS NOT NULL` 的事件：若最后一期 `event_date < today`，置 `status=ENDED`、记录 `ended_at`

## 代码组织

**后端** (`app-snapledger`)：
```
com.panda.snapledger.recurring/
├── entity/RecurringEvent.java
├── repository/RecurringEventRepository.java
├── service/
│   ├── RecurringEventService.java       # CRUD + 修改三模式
│   ├── RecurringEventGenerator.java     # 期数日期计算 + record 生成
│   └── RecurringEventScheduler.java     # 定时任务
├── controller/RecurringEventController.java
└── dto/
    ├── RecurringEventRequest.java
    ├── RecurringEventResponse.java
    └── RecurringEventDetailResponse.java
```

`Record` 实体新增两个字段；`RecordService` 的查询/统计逻辑保持不变（周期产生的 record 和普通 record 一视同仁）。

**前端** (`frontend/src/`)：
```
views/snapledger/
├── RecurringEvents.vue           # 改造：列表
├── RecurringEventDetail.vue      # 新建：详情
└── AddRecord.vue                 # 改造：加"高级设置"入口
components/snapledger/
├── AdvancedSettingsSheet.vue     # 新建：高级设置弹窗
├── RecurringEventCard.vue        # 新建：列表卡片
└── RecordActionSheet.vue         # 新建：长按操作卡片
api/snapledger/
└── recurringEvent.js             # 新建：API 客户端
```

## 测试

- `RecurringEventGeneratorTest`：期数日期计算（月末边界、闰年、每周每日）
- `RecurringEventServiceTest`：创建 + 历史回溯挂接、三种修改模式、结束/删除
- `RecurringEventSchedulerTest`：滚动窗口扩展、自动结束
- 集成测试：通过 API 端到端验证创建 → 生成 → 修改 → 结束流程

## 简化与取舍

- **不引入 record `status` 字段**：生成的 record 就是正式 record，到日期自然参与统计。未到期 record 仍会出现在该日期的列表中（符合"预生成未来期"的语义）
- **不做"入账方式"字段**：全部按"立即入账"处理，避免状态机复杂度
- **不做"从历史自动发现周期事件"**：YAGNI，需要时再扩展
- **复制 / 退款按钮占位不实现**：保留 UI 一致性但不耗费实现成本
