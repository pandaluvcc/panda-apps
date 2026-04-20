# 应收应付款项（Receivables & Payables）设计

**日期**：2026-04-20
**状态**：Draft
**涉及模块**：app-snapledger、frontend

## 背景

首页账户总览中的"应收应付款项"虚拟账户余额目前是硬编码 `-37842.18`（`Home.vue:144`）。需要将其改为根据真实记账数据计算，并补齐对齐 Moze app 的二级页面：列出所有借出/借入主记录、支持新增收款/还款、三态筛选。

数据源：Moze CSV 导入的 `recordType ∈ {应收款项, 应付款项}` 记录。

## 核心概念

### 主记录 vs 子记录

| 角色 | 定义 |
|---|---|
| **主记录**（parent） | 一次"借出/借入"动作。`Record.parentRecordId IS NULL` 的 `应收/应付款项` |
| **子记录**（child） | 对主记录的"收款/还款"动作。`parentRecordId` 指向主记录 |

**金额方向**（以账户收支为准，Moze CSV 语义）：

| recordType | 主记录金额 | 子记录金额 |
|---|---|---|
| `应收款项`（借出） | 负值（钱从你账户流出） | 正值（钱流回账户） |
| `应付款项`（借入） | 正值（钱进入你账户） | 负值（还款从账户流出） |

### 子类别（按 Moze 分类体系）

| recordType | 子类别 | 图标/色 |
|---|---|---|
| 应收款项 | 借出 / 代付 / 报账 | 绿色手心钱币 / 绿色握手 / 绿色公文包 |
| 应付款项 | 借入 | 红色手心钱币 |
| 应付款项 | 信贷 | 红色信用卡 |
| 应付款项 | 车贷 | 红色车 |
| 应付款项 | 房贷 | 红色房子 |
| 应付款项 | 利息 | 红色问号 |

### 三态 Tab

| 状态 | 判定规则 |
|---|---|
| **进行中**（默认） | `recordDate ≤ now` 且 `|子记录金额合计| < |主记录金额|` |
| **未开始** | `recordDate > now`（未来日期，如预生成的下月房贷） |
| **已完成** | `|子记录金额合计| >= |主记录金额|` |

### 房贷/车贷/利息的特殊性

这类主记录由 `RecurringEventScheduler` 每月自动生成（`商贷`、`公积金贷款` 等预设周期事件）。它们**天然无子记录**，生命周期就是"未开始 → 到期进入进行中 → 等待手动补标"。在 Moze 的原始 CSV 语义里，每月的应付款项与账户付款是成对记录（`房产°(+) / 中信银行(-)`），但在"应收应付款项"页面只展示 `房产°` 一侧（收款方视角），这已由现有 `RecurringEvent` 机制完成。

## 数据模型

### Record 扩展

```java
// Record.java
private Long parentRecordId;  // 指向主记录；null 表示自身是主记录或非应收应付
```

**索引**：`idx_record_parent (parent_record_id)`，支持按父 id 查询子记录。

**迁移脚本**：`V{N}__add_record_parent_id.sql`
```sql
ALTER TABLE record ADD COLUMN parent_record_id BIGINT NULL;
CREATE INDEX idx_record_parent ON record(parent_record_id);
```

### 不新建表的理由

"应收应付事件"与"周期事件 / 分期事件"不同：后两者是从多条 Record 归并出来的聚合体，需要独立实体存储归并结果；而应收应付的"事件"本身就是一条 Record（主记录），加 `parentRecordId` 自引用即可表达父子关系，零冗余。

## CSV 导入启发式（建立历史父子关系）

`MozeCsvImporter` 结束时追加一步 `ReceivableLinkingService.linkAll()`，对所有 `recordType ∈ {应收款项, 应付款项}` 的 Record 建立父子关系。

### 算法

```
1. 清空旧链接：UPDATE record SET parent_record_id = NULL WHERE recordType IN ('应收款项','应付款项')
2. 按 (account, name, recordType) 分组
3. 每组内按 recordDate + recordTime 升序
4. 维护一个 FIFO 队列 pending[] = 当前未还清的主记录 (主记录, 剩余金额)
5. 遍历每条 Record:
   - 若金额方向 == "主方向"（应收:负 / 应付:正）:
     - 作为主记录，push 到 pending，保留 parentRecordId = NULL
   - 若金额方向 == "子方向"（应收:正 / 应付:负）:
     - 从 pending 队首依次扣减，直到该子记录金额被消化完
     - 若队列为空（子记录没有对应的主）:
       - 兜底策略：自成主记录（parentRecordId = NULL），标记日志警告
```

**幂等性**：每次 CSV 导入都清空并重建。与 `InstallmentDetectionService.detectAll()` 同策略。

**子类别特殊处理**：`子类别 ∈ {房贷, 车贷, 利息, 信贷}` 的主记录**不参与启发式**，仅作为"独立主记录"存在（由 RecurringEvent 或账单分期生成，语义上每月独立，不会有对应"收款"子记录）。如果 CSV 中确实出现了对应的反向金额记录，仍按上述 FIFO 规则处理。

### 单元测试覆盖

- `借给阿芳° 2022/4/26 -5000 → 2022/5/4 +1500` 形成一对父子，主记录剩余 3500
- `借给阿芳° 2022/11/28 -4000 → +2000, +2000` 多子记录全部还清，状态 = 已完成
- 队列不为空时新开主记录 → 验证 FIFO 正确性

## 后端 API

所有接口在 `ReceivableController` 下，前缀 `/api/snapledger/receivables`。

### `GET /api/snapledger/receivables`

**查询参数**：
- `status`: `IN_PROGRESS` | `NOT_STARTED` | `COMPLETED`（必填）
- `target`: 字符串，按"对象"字段过滤；空字符串表示"不限定对象"

**响应**（`List<ReceivableResponse>`）：
```json
[
  {
    "id": 12345,
    "recordType": "应收款项",
    "subCategory": "借出",
    "name": "借给阿芳°",
    "account": "招商银行°",
    "target": "",
    "amount": -5000.00,          // 主记录原始金额（带符号）
    "absAmount": 5000.00,
    "paidAmount": 1500.00,       // 子记录金额合计（绝对值）
    "remaining": 3500.00,        // |amount| - paidAmount
    "recordDate": "2022-04-26",
    "recordTime": "18:26",
    "status": "IN_PROGRESS",
    "children": [
      { "id": 12400, "amount": 1500, "recordDate": "...", "recordTime": "...", "account": "..." }
    ],
    "recurringEventId": null,    // 若主记录来自周期事件
    "periodNumber": null
  }
]
```

按 `recordDate DESC, recordTime DESC` 排序。

### `GET /api/snapledger/receivables/summary`

**响应**：
```json
{
  "netAmount": -37842.18,   // 进行中+未开始的净应付金额（应付为负，应收为正）
  "inProgressCount": 26,
  "notStartedCount": 2,
  "completedCount": 15
}
```

`Home.vue` 虚拟账户的 `balance` 改为读这个接口的 `netAmount`。

### `POST /api/snapledger/receivables/{parentId}/children`

新增子记录（收款或还款）。

**请求体**：
```json
{
  "accountId": 3,          // 收/还款目标账户
  "amount": 1500.00,       // 用户输入的绝对值
  "recordDate": "2022-05-04",
  "recordTime": "10:54",
  "note": "..."
}
```

**后端处理**：
- 查主记录，根据 `recordType` 推断子记录符号（应收→正、应付→负）
- 子记录 `recordType` 继承父的 `recordType`
- 子记录 `name` 继承父的 `name`
- 设置 `parentRecordId = parentId`
- 校验：`Σ|children| + amount <= |parent.amount|`（不允许超额，可后续放开）

### `POST /api/snapledger/receivables`

新增主记录（Phase 3 手动新增入口）。

**请求体**：
```json
{
  "recordType": "应收款项",
  "subCategory": "借出",
  "name": "借给小明",
  "accountId": 3,
  "amount": 500.00,        // 绝对值
  "recordDate": "2026-04-20",
  "recordTime": "11:20",
  "target": "",
  "note": ""
}
```

后端按主记录方向推断金额符号，写入 Record（`parentRecordId = NULL`）。

### `DELETE /api/snapledger/receivables/{parentId}`

级联删除主记录及所有子记录。

### `DELETE /api/snapledger/receivables/children/{childId}`

删除单条子记录（主记录状态自动重算）。

## 前端

### 路由

`frontend/src/router.js`：
```js
{
  path: '/snapledger/receivables',
  name: 'Receivables',
  component: () => import('@/views/snapledger/Receivables.vue'),
  meta: { module: 'snapledger' }
}
```

### 首页入口

`Home.vue` 的虚拟账户 `virtual-receivable-payable`：
- `onMounted` 时调 `getReceivablesSummary()`，用 `netAmount` 替换 `balance` 的硬编码
- 点击跳 `/snapledger/receivables`（现有逻辑 `isVirtual=true` 跳过跳转 → 需改为按 id 路由）

### Receivables.vue（列表页）

**布局**（按图 1-4）：
- 顶部：返回按钮 + 标题"应收应付款项" + 右上筛选按钮（Phase 3 才激活）
- 三 Tab：进行中 / 未开始 / 已完成（默认进行中）
- chip 行：全部 / 不限定对象（Phase 3 才加对象切换）
- 汇总行：`— 不限定对象 (N) -¥X,XXX.XX`（仅"全部"视图显示）
- 列表：按 `recordDate DESC` 展示主记录
- 列表项（`ReceivableRow.vue` 组件）：
  - 左：图标（按 subCategory 映射）+ 圆底色
  - 中：日期时间（`YYYY/MM/DD 周X HH:mm`）、名称、副标题
    - 副标题：普通 = `不限定对象` 或 `target`；周期事件 = `周期 #N / 无限期 · 不限定对象`
  - 右：金额（应收绿、应付红）+ 状态文本（尚未还款/尚未收款/已收款 ¥X/已还款 ¥X/已完成）+ 对象徽章
- 选中态（仅进行中 Tab 可选中）：
  - 点击行 → 勾选圈变蓝，底部浮现操作栏 `新增收款/还款 +¥{remaining}`
  - 再次点击取消选中
- 底部操作栏点击 → 跳 `/snapledger/receivables/:parentId/new-child`

**状态持久化**：当前 Tab 存 `sessionStorage('snap.receivables.activeTab')`。

### ReceivableChildForm.vue（Phase 2）

图 8 样式，新增收款/还款表单：
- 顶部卡片：主记录图标 + 名称 + 主记录金额
- 金额（默认填 `remaining`，可改但前端校验不超过）
- 账户选择（复用现有 AccountPicker）
- 日期+时间（默认 now）
- 备注
- 底部：`剩余款项：¥X.XX`（实时更新）
- 提交 → POST children → 返回列表

### 手动新增入口（Phase 3）

`AddRecord.vue` 的 tab 列表 `recordTypes` 已有 `'应收款项', '应付款项'`（`AddRecord.vue:173`），需补齐表单：
- 应收款项子类别选择：借出 / 代付 / 报账
- 应付款项子类别选择：借入 / 信贷 / 车贷 / 房贷 / 利息
- 复用现有金额输入、账户、名称等字段
- 提交 → POST `/api/snapledger/receivables`（主记录）

### 筛选按钮（Phase 3）

右上漏斗图标点击 → 底部弹窗，支持：
- 按子类别筛选（多选）
- 按时间范围筛选
- 按对象筛选

MVP 先做占位按钮，后续迭代细化。

## 实施顺序

按 Phase 分段，每段完成后可单独验收：

### Phase 1：只读展示 + 首页数字
1. DB migration 加字段
2. `Record.parentRecordId` + repository 查询
3. `ReceivableLinkingService.linkAll()` 启发式
4. `MozeCsvImporter` 末尾调用 linkAll
5. `ReceivableController.list` + `summary`
6. `Home.vue` 虚拟账户读 summary
7. `Receivables.vue` + `ReceivableRow.vue`（三 Tab 展示）
8. 路由挂载 + 点击首页跳转

### Phase 2：新增收款/还款
9. `POST /receivables/{id}/children`
10. `ReceivableChildForm.vue`
11. 列表选中态 + 底部操作栏
12. 提交后刷新列表

### Phase 3：手动新增 + 筛选
13. `POST /receivables` 主记录
14. `AddRecord.vue` 补子类别选择
15. 右上筛选按钮 + 底部筛选弹窗
16. chip 对象切换

## 验证

- Phase 1 完成后：首页虚拟账户金额应接近 `-37842.18`（以算法算出的为准，若偏差大需复核子类别白名单和子记录匹配）
- 单元测试覆盖 `ReceivableLinkingService` 三个典型场景（见启发式章节）
- 前端页面手工验证：三 Tab 切换正确、金额颜色正确、选中态正确、新增收款后主记录 `paidAmount` 正确递增

## 非目标（本期不做）

- 不支持跨账户的应收应付关联（Moze 里"借给阿芳"有时用招商银行、有时用朝朝宝记，分属不同 `(账户, 名称)` 分组，按独立事件处理）
- 不支持子记录再嵌套子记录
- 不做周期事件与应收应付的双向同步（房贷主记录只读，不可挂子记录 — Phase 1 限制，后续可放开）
- 不做多币种
