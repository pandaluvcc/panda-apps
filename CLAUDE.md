# CLAUDE.md

Guidance for Claude Code working in this repo. This file is auto-loaded into every session, so only put here what **can't be derived from the code**. Detailed feature specs live in `docs/superpowers/specs/`.

## Overview

Panda Apps monorepo:
- **Grid Trading (网格交易)** — stock grid strategy
- **Snap Ledger (快记账)** — mobile expense/income tracker
- **WeChat Remote Control** — bridge 公众号 → local Claude CLI via frp

## Backend (Spring Boot 3.2 / Java 17 / MySQL)

```
panda-api/         ← only entry point, packages the other modules
common/            ← shared OCR/DTO/AOP, depended on by both apps
app-gridtrading/   ← grid trading business module
app-snapledger/    ← snap ledger business module
```

- **Start**: `cd panda-api && mvn spring-boot:run` (or `mvn -pl panda-api spring-boot:run` from root)
- **Entry class**: `panda-api/src/main/java/com/panda/PandaApplication.java`
- Controllers return `ApiResponse<T>` (`code`/`data`/`message`), use `@CrossOrigin(origins = "*")`
- DBs: `gridtrading_db`, `snapledger_db` (charset `utf8mb4`)
- Migrations: `app-*/src/main/resources/db/migration/V{N}__*.sql` (Flyway)
- Integration tests live in `panda-api/src/test/java/` (only module that sees the full classpath); use `@SpringBootTest(classes = PandaApplication.class)`

## Frontend (Vue 3 + Vite)

- Vant (mobile) + Element Plus (desktop)
- Composition API (`<script setup>`) + Pinia
- **History mode with `/panda-apps/` base path** (matters for nginx rewriting)
- Axios interceptors auto-unpack `ApiResponse.data`
- Global CSS vars in `frontend/src/styles/variables.css`:
  - `--profit-positive: #f56c6c` (**red = 涨/gain**, Chinese stock convention)
  - `--profit-negative: #67c23a` (**green = 跌/loss**)
- Page transitions via route meta: `page-fade`, `page-slide`, `page-scale`

## WeChat Remote Control

Flow: WeChat 公众号 → `wechat-server` (Express) → frp tunnel → local CLI listener → Claude CLI. Key files: `wechat-server/src/{index.js, wechat/validator.js, cli/listener.js}`. Start with `npm run dev` (or `pm2` in prod).

## Key Files

- `PandaApplication.java` — backend entry, package scan
- `frontend/src/router.js` — all frontend routes
- `docker-compose.yml` — local Docker (9090 api, 80 nginx)
- `deploy/` — production setup (`/data/docker/platform/` on server) + `default.conf` nginx
- `db/init/01-create-databases.sql` — DB bootstrap

## API Conventions

Base paths: `/api/gridtrading/*`, `/api/snapledger/*`, `/wechat`. For the exact endpoint list, `Grep "@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)"` — routes change often and are not mirrored here.

`POST /api/snapledger/import` is a **one-shot** endpoint for first-time history migration; see the "Receivables" section for why it shouldn't be re-run casually.

---

## Snap Ledger · recordType Classification

Moze CSV 有 13 种 `recordType`，下表影响大量 service 查询与余额计算，改前先读 `docs/superpowers/specs/2026-04-11-record-type-classification-design.md`。

| 类别 | recordType | 统计处理 |
|---|---|---|
| 支出类 | `支出`, `手续费`, `利息` | 计入支出 |
| 收入类 | `收入` | 计入收入 |
| 抵扣类 | `退款`, `折扣` | 冲减支出，不计收入 |
| 转账类 | `转出`, `转入`, `转账`, `还款`, `应付款项`, `应收款项`, `分期还款` | 排除收支统计，进还款/转账列表 |
| 特殊类 | `余额调整`, `账单分期` | 排除收支统计 |

### Moze CSV 格式

- **16 列顺序**：账户, 币种, 记录类型, 主类别, 子类别, 金额, 手续费, 折扣, 名称, 商家, 日期, 时间, 项目, 描述, 标签, 对象
- "对象"（第 16 列）常为空
- 商户分期的利息标记 (`LibertyKostume・采用固定利息`) 在第 14 列 **描述**（不在名称里）
- **转账/还款双边成对记录**：Moze 导出用 `转出/转入` 各一行、`对象` 字段空；手工录入用 `转账/还款` + `target` 字段

### 信用卡还款窗口期

信用卡账户的**还款**按窗口期 `[cycleEnd+1, dueDate]` 查询，不按账单周期 `[startDate, endDate]`。例：账单周期 12/03-01/02、还款日每月 20 号 → 还款窗口 01/03-01/20。

---

## Snap Ledger · Recurring Events (周期事件)

详见 `docs/superpowers/specs/2026-04-18-recurring-events-design.md`。

- 自动按日/周/月/年生成 Record，`RecurringEventScheduler` 每日扩展窗口（36 期，阈值 180 天）
- 软关联：`Record.recurringEventId` + `Record.periodNumber`
- **预设事件**（`MozeCsvImporter.PREDEFINED_RECURRING` 硬编码，CSV 导入时自动创建+回溯挂接）：
  | 名称 | 账户 / 跨账户 | 每月 N 号 | 金额 | recordType | 备注 |
  |---|---|---|---|---|---|
  | 预缴当月房贷 | 招行朝朝宝° → 中信银行 | 19 | 4300.00 | 转账 | |
  | 商贷 | 房产° | 20 | 2985.34 | 应付款项/房贷 | 别名：应交当月房贷 |
  | 公积金贷款 | 房产° | 20 | 1200.51 | 应付款项/房贷 | |
- 利率/月供变动 → 改预设代码重发版，或 `PUT /api/snapledger/recurring-events/:id` 修改金额
- `backfillOrphansByAliases` 做历史记录的别名回溯挂接

---

## Snap Ledger · Installment Events (分期事件)

从 CSV **启发式归并**的只读展示（无手动创建入口）。`InstallmentDetectionService.detectAll` 在 CSV 导入末尾清空重建（幂等）。

- **识别条件**（同时满足）：`Account.isCreditAccount=true` + 金额<0 + `recordType ∈ {支出, 分期还款}` + 有精确时间戳 + ≥3 期 + 相邻间隔 23-36 天
- **归并键**：`(account, 名称|子类别, HH:mm)` —— 利用"Moze 分期自动克隆购买时间戳"这一特征
- **利息关联**：商户分期按 `description` 含 `<商品名>・` 匹配；账单分期按 `(account, date)` 匹配
- **折扣抵本金**：`recordType=折扣` + 同日 → 冲减当期本金
- **年利率**：从利息 `description` 正则 `年利率 X.X%` 提取
- **关键字段**：`InstallmentEvent.{principalTotal, interestTotal, totalAmount, yearRate}`；`Record.installmentEventId` + `installmentPeriodNumber`

**已知误判 / 限制**：
- 信用卡上连续 ≥3 月等额的月度订阅会被识别为分期
- Moze CSV 格式变更会失效
- 2 期分期不识别（易误判）

前端入口：`InstallmentEvents.vue`（列表 + 点击弹详情卡片，非独立路由）。

---

## Snap Ledger · Receivables & Payables (应收应付款项)

详见 `docs/superpowers/specs/2026-04-20-receivables-payables-design.md`。

### 数据模型（无独立事件表）

- `Record.parentRecordId` 自引用
- **主记录**：`recordType ∈ {应收款项, 应付款项} AND parentRecordId IS NULL`
- **子记录**（收款/还款）：`parentRecordId` 指向主

### 金额方向

| recordType | 主（借出/借入） | 子（收/还款） |
|---|---|---|
| 应收款项 | 负 | 正 |
| 应付款项 | 正 | 负 |

### 三态

- **已完成**：`Σ|子| ≥ |父|`
- **未开始**：`父.date > now`
- **进行中**：其他

### 行为约定

- **未开始 Tab**：每个 `recurringEventId` 只显示日期最早的下一期（Moze 语义）；其余未来期留在 DB 等日期到达自动迁入进行中
- **首页 `netAmount` 只算进行中**，不含未开始（与 Moze 首页一致）
- 已完成 Tab 汇总不显示金额；进行中/未开始 汇总金额不着色（仅下方明细行着色）

### CSV 启发式建链（`ReceivableLinkingService.linkAll`，仅 CSV 导入时一次）

1. 按 `(subCategory, name, recordType)` 分组（**忽略 account**，借/还常跨账户）
2. 组内排序：日期升序 → 同日主方向优先 → 时间升序
3. 每组跑 LIFO / FIFO 两种策略择优；子金额 > 主剩余 → 主标完成、溢出丢弃（保持单一 `parentRecordId`）
4. 兜底：空名称主记录吸收同 `(subCategory, recordType)` 的孤儿子 + 跨组同日同额匹配

### 自动化边界 ⚠️

日常新增借出/借入/还款都走**显式 CRUD**（`POST /receivables`、`POST /receivables/:id/children`），不依赖启发式，永远正确。状态与首页总额实时从 DB 重算。

**CSV 重导入会调 `linkAll()` 清空所有 `parentRecordId` 重跑算法** —— 会覆盖 app 里手工建立的关联。CSV 导入定位为"首次历史数据迁移"的一次性操作；需再次批量导入时要新增"保留手工关联"开关。

### 硬编码维护点

`ReceivableLinkingService.CROSS_ACCOUNT_SUB_CATEGORIES = {房贷, 车贷, 信贷, 利息}` —— 这几类子类别的借款侧和还款侧分别记在虚拟债务账户与实际支付账户（如 `房产°+` / `中信银行-`），启发式分组忽略 account。Moze 新增此类子类别需同步。

---

## Snap Ledger · Master-Sub Account

详见 `docs/superpowers/specs/2026-04-15-master-sub-account-management-design.md`。

- `Account.isMasterAccount` + `Account.masterAccountName` 表达层级
- 子账户 `accountGroup` 跟随主；主改分组时子同步
- 信用卡分组限制：信用卡主账户只能接收信用卡分组内的子账户（前端筛选）
- **余额公式**（避免重复计入）：
  - 主账户显示余额 = 自身 + 所有直接子之和
  - 分组余额 = 该组内主账户余额（已含子） + 独立账户余额
- 归档/删除主账户 → 自动解绑所有子；子变独立，不受影响

---

## Code Conventions

- **Lombok** (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) on entities/DTOs
- **@Transactional** at service layer; 默认 `Exception` 回滚
- **Global error handler** with `@RestControllerAdvice` → `ApiResponse<T>`
- **Vue**: `<script setup>`, Pinia for shared state, 避免内联样式（用 `variables.css`）

## Workflow

- 新功能/重构先写 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
- 实施计划写 `docs/superpowers/plans/YYYY-MM-DD-<topic>-plan.md`
- Code 和 spec/plan 同提交
