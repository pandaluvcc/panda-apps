# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Panda Apps is a monorepo containing two business applications:
- **Grid Trading (网格交易)**: Automated grid trading strategy for stocks
- **Snap Ledger (快记账)**: Mobile-first expense/income tracking app

## Architecture

### Backend (Java/Spring Boot 3.2)

```
panda-apps/
├── panda-api/          # Main entry point, packages all modules
│   └── src/main/java/com/panda/PandaApplication.java  ← START HERE
├── common/             # Shared infrastructure (OCR, DTOs, AOP, configs)
├── app-gridtrading/    # Grid trading business module
└── app-snapledger/     # Snap ledger business module
```

**模块关系：**
- **panda-api** 是唯一的启动入口，依赖其他三个模块
- **common** 被 app-gridtrading 和 app-snapledger 共同依赖
- **app-gridtrading** 和 **app-snapledger** 是独立的业务模块，互不依赖

**启动命令（重要）：**
```bash
# 从 panda-api 目录启动
cd panda-api
mvn spring-boot:run

# 或从根目录启动（指定父 POM）
cd panda-apps
mvn -pl panda-api spring-boot:run
```

**关键文件：**
- `panda-api/src/main/java/com/panda/PandaApplication.java` - 启动类，扫描所有模块包
- `pom.xml` (根目录) - 父 POM，管理依赖版本和模块
- `panda-api/pom.xml` - 只包含依赖声明，不包含业务逻辑

**Key patterns:**
- JPA + MySQL for data persistence
- `ApiResponse<T>` for unified API responses (code, data, message)
- `@RestController` with `@CrossOrigin(origins = "*")`
- Baidu OCR SDK for image recognition
- Spring Scheduler for periodic tasks

**Database:**
- `gridtrading_db`: Grid trading data (strategies, grid lines, trade records)
- `snapledger_db`: Financial records (accounts, transactions, budgets)
- Character set: `utf8mb4`, Collation: `utf8mb4_unicode_ci`

### Frontend (Vue 3 + Vite)

```
frontend/
├── src/
│   ├── api/            # API clients (gridtrading/, snapledger/)
│   ├── views/          # Page components
│   ├── components/     # Reusable components
│   ├── stores/         # Pinia state management
│   ├── router.js       # Vue Router configuration
│   └── styles/         # Global styles (variables.css, reset.css)
```

### WeChat Remote Control (Node.js)

```
wechat-server/
├── src/
│   ├── index.js            # Main entry point
│   ├── config.js           # Configuration
│   ├── wechat/
│   │   ├── controller.js   # WeChat message controller
│   │   ├── validator.js    # Server signature validation
│   │   └── responder.js    # Message responder
│   └── cli/
│       └── listener.js     # CLI process listener
├── package.json
└── .env
```

**Key patterns:**
- Express.js for HTTP server
- WeChat Official Account API integration
- frp/ngrok for reverse tunneling (expose local service)
- Child process to interact with Claude CLI
- Signature validation for WeChat server verification

**Setup:**
```bash
cd wechat-server
npm install
# Configure .env with WeChat credentials
npm run dev
```

**Architecture:** WeChat → Backend Server (message receive + forward) → frp → Local CLI Listener → Claude CLI

**Key patterns:**
- Element Plus (desktop) + Vant (mobile) UI libraries
- Axios interceptors auto-unpack `ApiResponse` format
- History mode routing with `/panda-apps/` base path
- Page transition animations (fade, slide, scale)

## Development Commands

### Backend

```bash
# Local development (from panda-api/)
mvn spring-boot:run

# Build package (skip tests)
mvn clean package -DskipTests

# Run tests
mvn test

# Compile all modules
mvn compile
```

### Frontend

```bash
cd frontend

# Development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint and format
npm run lint
npm run format
```

### WeChat Remote Control

```bash
cd wechat-server

# Development mode
npm run dev

# Production mode
npm start

# Install PM2 for production
npm install -g pm2
pm2 start src/index.js --name wechat-server
```

### frp (Reverse Proxy)

```bash
# Start frp client
frpc -c wechat-server/frpc.ini
```

### Docker Deployment

```bash
# From project root
docker compose build
docker compose up -d

# Or use deploy script (on server at /data/docker/platform/panda-apps/)
./deploy.sh restart
./deploy.sh logs -f
./deploy.sh status
```

## Database Setup

```bash
# Initialize databases
mysql -u root -p < db/init/01-create-databases.sql
```

Environment variables for applications:
```bash
# Grid Trading DB
GRIDTRADING_DB_URL=jdbc:mysql://localhost:3306/gridtrading_db
GRIDTRADING_DB_USER=gridtrading_user
GRIDTRADING_DB_PASSWORD=your_password

# Snap Ledger DB
SNAPLEDGER_DB_URL=jdbc:mysql://localhost:3306/snapledger_db
SNAPLEDGER_DB_USER=snapledger_user
SNAPLEDGER_DB_PASSWORD=your_password
```

## API Routes

### Grid Trading
- `GET/POST /api/gridtrading/strategy` - Strategy CRUD
- `GET/POST /api/gridtrading/grid` - Grid line management
- `GET/POST /api/gridtrading/trade` - Trade records
- `POST /api/gridtrading/ocr` - OCR upload & recognition
- `GET /api/gridtrading/suggestion` - AI suggestions
- `GET /api/gridtrading/quote` - Stock quotes

### Snap Ledger
- `GET/POST /api/snapledger/record` - Transaction records
- `GET/POST /api/snapledger/account` - Account management
- `GET /api/snapledger/accounts/:id/transactions` - Account transaction list (by period)
- `GET /api/snapledger/accounts/:id/summary` - Period summary statistics
- `PUT /api/snapledger/accounts/:masterId/sub-accounts/batch` - 批量关联/解绑子账户
- `GET /api/snapledger/calendar` - Monthly calendar data
- `GET/POST /api/snapledger/stats` - Statistics
- `GET/POST /api/snapledger/budget` - Budget management
- `POST /api/snapledger/ocr` - OCR receipt recognition
- `POST /api/snapledger/import` - Moze CSV 导入（一次性接口，用于首次导入历史数据）
- `GET/POST/PUT/DELETE /api/snapledger/recurring-events` - 周期事件 CRUD
- `GET /api/snapledger/recurring-events?status=ACTIVE|ENDED` - 周期事件列表
- `GET /api/snapledger/recurring-events/:id` - 周期事件详情（含每期 Record）
- `POST /api/snapledger/recurring-events/:id/end` - 结束周期事件
- `GET /api/snapledger/installment-events?status=ACTIVE|ENDED` - 分期事件列表
- `GET /api/snapledger/installment-events/:id` - 分期事件详情（含每期本金/利息/合计）
- `POST /api/snapledger/installment-events/detect` - 手动重建分期识别（调试用）
- `GET /api/snapledger/receivables?status=IN_PROGRESS|NOT_STARTED|COMPLETED` - 应收应付主记录列表
- `GET /api/snapledger/receivables/summary` - 应收应付汇总（首页虚拟账户金额来源）
- `POST /api/snapledger/receivables` - 手动新增主记录（借出/借入）
- `POST /api/snapledger/receivables/:parentId/children` - 新增收款/还款（显式关联到主记录）
- `POST /api/snapledger/receivables/relink` - 手动重跑启发式建链（调试用）
- `DELETE /api/snapledger/receivables/:parentId` - 删除主记录（级联删除子记录）
- `DELETE /api/snapledger/receivables/children/:childId` - 删除单条子记录

### WeChat Remote Control
- `GET /wechat` - WeChat server verification
- `POST /wechat` - WeChat message handler
- `GET /health` - Health check endpoint

## Key Files to Reference

- `PandaApplication.java` - Main entry, scan base packages
- `ApiResponse.java` - Unified response format
- `router.js` - Frontend route definitions
- `docker-compose.yml` - Local Docker setup (port 9090, 80)
- `deploy/docker-compose.yml` - Production setup (server path: /data/docker/platform/)
- `deploy/default.conf` - Nginx reverse proxy config
- `wechat-server/src/index.js` - WeChat server main entry
- `wechat-server/src/wechat/validator.js` - WeChat signature validation
- `wechat-server/src/cli/listener.js` - CLI process listener

## UI Design System

Global CSS variables in `frontend/src/styles/variables.css`:
- `--primary-color`: #409eff
- `--profit-positive`: #f56c6c (red for gain - Chinese stock style)
- `--profit-negative`: #67c23a (green for loss)
- Page transitions: `page-fade`, `page-slide`, `page-scale`

## Common Tasks

### Adding a new API endpoint
1. Create DTO in `controller/dto/`
2. Add repository method in `repository/`
3. Implement service logic in `service/`
4. Add controller method in `controller/`
5. Create frontend API in `frontend/src/api/`
6. Add Vue component in `frontend/src/views/`

### Database migration
Add SQL files to `app-*/src/main/resources/db/migration/` with version prefix (e.g., `V1__`).

### Adding frontend routes
Edit `frontend/src/router.js` - routes use module metadata for transition handling.

## Snap Ledger Record Type Classification

Moze CSV 导入的记录包含 13 种 `recordType`，系统按以下矩阵分类处理：

| 类别 | recordType 值 | 统计处理 |
|------|-------------|---------|
| 支出类 | `支出`, `手续费`, `利息` | 计入支出 |
| 收入类 | `收入` | 计入收入 |
| 抵扣类 | `退款`, `折扣` | 冲减支出，不计收入 |
| 转账类 | `转出`, `转入`, `转账`, `还款`, `应付款项`, `应收款项`, `分期还款` | 排除出收支统计，显示在还款/转账记录列表 |
| 特殊类 | `余额调整`, `账单分期` | 排除出收支统计 |

**信用卡还款窗口期**：信用卡账户的还款记录不按账单周期 `[startDate, endDate]` 查询，而是按还款窗口期 `[cycleEnd+1, dueDate]` 查询。例如账单周期 12/03-01/02、到期还款日 20 号 → 还款窗口 01/03-01/20。

**Moze CSV 转账格式**：Moze 导出的转账/还款记录使用 `转出`/`转入` 成对记录（各一侧），`对象` 字段为空。手动录入的转账使用 `转账`/`还款` 类型，有 `target` 字段。

**Moze CSV 列映射（16 列）**：`账户、币种、记录类型、主类别、子类别、金额、手续费、折扣、名称、商家、日期、时间、项目、描述、标签、对象`。注意"对象"是第 16 列且常为空；商户分期的利息标记（如 `LibertyKostume・采用固定利息`）实际在第 14 列"描述"中。

## Recurring Events (周期事件)

按固定间隔（日/周/月/年）自动生成记账记录的事件机制，对齐 Moze 的"周期"概念。

### 核心字段（`RecurringEvent`）
- `intervalType`: `DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY`
- `intervalValue`: 间隔数量（默认 1）
- `dayOfMonth` / `dayOfWeek`: 月度/周度日期锚点
- `startDate`: 首期日期
- `totalPeriods`: 总期数（null = 无限期）
- `generatedUntil`: 已预生成到的日期（调度器用）
- `status`: `ACTIVE` / `ENDED`

### Record 关联
- `Record.recurringEventId`: 软关联到周期事件
- `Record.periodNumber`: 第几期

### 预设周期事件
`MozeCsvImporter.ensurePredefinedRecurringEvents()` 在 CSV 导入末尾自动创建并回溯挂接：
- 预缴当月房贷（招行朝朝宝° → 中信银行，每月 19 号 4300）
- 商贷（中信银行，每月 20 号 2985.34，别名"应交当月房贷"）
- 公积金贷款（中信银行，每月 20 号 1200.51）

### 调度与补发
- `RecurringEventScheduler`: 定时任务，按 `intervalType` 提前生成未来若干期 Record
- `RecurringEventService.backfillOrphansForEvent/ByAliases`: 按名称/别名回溯挂接历史同名 Record

### 前端
- `views/snapledger/RecurringEvents.vue` - 列表（进行中/已结束 Tab）
- `views/snapledger/RecurringEventDetail.vue` - 详情（含每期记录，支持修改本期/从某期起/整个事件）
- Moze 转账类事件成对生成 (转出/转入)，详情页按 `periodNumber` 去重显示一条

## Installment Events (分期事件)

从 Moze CSV 启发式归并的只读分期展示，**数据源只有 CSV 导入**，无手动创建入口。

### 识别算法（`InstallmentDetectionService.detectAll`）

CSV 导入末尾自动触发，**清空重建** InstallmentEvent（幂等）。通用规则：

| 环节 | 规则 |
|---|---|
| 候选筛选 | `Account.isCreditAccount=true` + 金额<0 + `recordType ∈ {支出, 分期还款}` + 有精确时间戳 |
| 归并键 | `(account, 名称\|子类别, HH:mm)` —— Moze 分期自动克隆购买时间戳 |
| 判定 | ≥3 期 + 相邻间隔 23-36 天 + 总跨度约 N-1 月 |

### 关联记录
候选本金记录确定后，按日期扩展：
- **利息关联**：
  - 商户分期：利息 Record 的 `description` 含 `<商品名>・` → 匹配同账户下事件
  - 账单分期：利息 Record 同 `(account, date)` → 匹配 BILL_INSTALLMENT 事件
- **折扣关联**：`recordType=折扣` + 同 `(account, date)` 的本金记录 → 挂到事件
- **年利率提取**：从利息记录 `description` 正则 `年利率 X.X%` 提取

### 金额计算
- **每期净本金** = 本金记录绝对值 − 同日折扣
- `principalTotal` = Σ(每期净本金)
- `interestTotal` = Σ(利息绝对值)
- `totalAmount` = principalTotal + interestTotal
- `perPeriodAmount` = 末期净本金 + 末期利息（列表展示用）

### 关键字段
- `InstallmentEvent`: `principalTotal` / `interestTotal` / `totalAmount` / `yearRate`(可空) / `status`
- `Record.installmentEventId` / `Record.installmentPeriodNumber`

### 前端
- `views/snapledger/InstallmentEvents.vue` - 列表（进行中/已结束 Tab）+ **点击弹出详情卡片**（详情非独立路由）
  - Tab 状态通过 `sessionStorage('snap.installment.activeTab')` 持久化
  - 列表按 `lastDate` 倒序
- 详情卡片结构：header（图标/名称/副标题/总金额） + 6 项本金利息总计 + 每期列表（编号胶囊 + 本金·利息副标题 + 年利率徽章）
- `views/snapledger/InstallmentEventDetail.vue` + 路由仍保留作为深链备份

### 未来扩展注意事项
1. 识别阈值 `≥3 期`：2 期分期极少见且易误判，如需支持需增强其他判据
2. 若信用卡上有月度定时自动扣款订阅（连续 ≥3 月），会被误识别为分期
3. Moze CSV 格式变更（列顺序/分隔符/标记字符）会失效识别，需同步调整

## Receivables & Payables (应收应付款项)

跟踪借出/借入事件及其收款/还款动作。首页"应收应付款项"虚拟账户金额由此计算，点击进入二级页面（`/snap/receivables`）支持进行中/未开始/已完成三态查看。

### 数据模型

`Record.parentRecordId` 自引用字段表达父子关系，无独立事件表：
- **主记录**：`recordType ∈ {应收款项, 应付款项} AND parentRecordId IS NULL`
- **子记录**（收/还款）：`parentRecordId` 指向主记录

金额方向规则（以账户收支为准，与 Moze CSV 一致）：

| recordType | 主记录金额 | 子记录金额 |
|---|---|---|
| 应收款项（借出） | 负值（钱从你账户流出） | 正值（钱流回账户） |
| 应付款项（借入） | 正值（钱进入你账户） | 负值（还款从账户流出） |

### 三态判定（`ReceivableService.computeStatus`）

| 状态 | 判定规则 |
|---|---|
| **已完成** | Σ\|子记录金额\| ≥ \|主记录金额\|（还清） |
| **未开始** | 主记录日期 > 当前时间（未来期） |
| **进行中** | 其他（默认） |

### CSV 导入时的启发式建链（`ReceivableLinkingService.linkAll`）

**仅在 CSV 导入时触发一次**，对真实 Moze 数据做到零误差匹配。通用算法：

1. 按 `(subCategory, name, recordType)` 分组 —— 忽略 account，借/还常跨账户
2. 组内按 日期升序、同日主方向优先、时间升序排序
3. 每组跑两种策略择优：
   - **LIFO**（默认）+ 精确等额优先 —— 对应 Moze "还款关联到最近借款" 的常见手工习惯
   - **FIFO + 溢出级联** —— 仅当 FIFO 能完全清空而 LIFO 有余额时启用，处理"一笔大借款 + 多期还款"场景（如 房贷首付款）
4. **Pass 2 兜底**：同 `(subCategory, recordType)` 组内的孤儿子记录，挂到空名称主记录（如 `未还房租° +10640` 吸收多期 `未还房租` 子记录）
5. **Pass 3 兜底**：按 `(subCategory, recordType, date, absAmount)` 跨组匹配，处理一对配对里一方名字为空的场景（如 `房产° +3925.87 1月房贷` 与 `中信银行 -3925.87 空名称`）

**子记录溢出规则**：子金额 > 主剩余时，主标记完成，溢出丢弃（保持 `parentRecordId` 单值语义，DB/内存一致）。

### 未来期生成（`未开始` Tab）

`商贷 / 公积金贷款` 预设周期事件（`MozeCsvImporter.PREDEFINED_RECURRING`）每次 CSV 导入末尾触发 `RecurringEventScheduler.extendInfiniteWindows()`，预生成未来 36 期 Record。

Moze 在"未开始"只显示每事件的下一期，所以 `ReceivableService.filterForStatus` 对 NOT_STARTED 状态**按 recurringEventId 分组只保留日期最早的一条**。其余未来期在 DB 里备用，日期到了自动进"进行中"。

### 首页金额（`/receivables/summary`）

`netAmount` **只计进行中**（未开始是预期事件，不算当前负债），对齐 Moze 首页行为。

### 前端

- `views/snapledger/Receivables.vue` - 列表页，三 Tab + 顶部筛选按钮（占位）+ "不限定对象" chip
  - Tab 状态持久化：`sessionStorage('snap.receivables.activeTab')`
  - 列表项选中（仅进行中 Tab）→ 底部浮现"新增收款/还款"操作栏
  - "已完成" Tab 汇总行不显示金额；进行中/未开始 汇总金额不上红绿色
- `views/snapledger/ReceivableChildForm.vue` - 新增收/还款表单，金额默认填 `remaining`
- `components/snapledger/ReceivableRow.vue` - 行组件
  - 图标按 `subCategory` 映射：借出/代付/报账=绿色、借入/信贷/车贷/房贷/利息=红色
  - 主金额显示 `remaining`（已部分还款时显示剩余，非原始金额），旁边灰字显示"已收款/已还款 ¥X"
- `AddRecord.vue` - 顶部 tab 选"应收款项"/"应付款项" 时，提交走 `createReceivable` 而非普通 createRecord

### 自动化边界

日常操作（新增借出/借入/还款）走显式 CRUD 接口，不依赖启发式算法，永远正确：
- `POST /receivables` 新增主记录
- `POST /receivables/{id}/children` 新增收/还款（显式关联父 id）
- 状态/剩余金额/首页总额都实时从 DB 重算

**注意**：`CSV 重导入会调 linkAll() 清空所有父子关系重跑算法**。CSV 导入定位为"首次历史数据迁移"的一次性操作，之后所有新增通过 app UI 完成。如果以后需要再次批量导入，需新增"保留手工关联"开关。

### 硬编码部分（后续维护点）

1. `PREDEFINED_RECURRING`（商贷/公积金贷款金额+名称）：利率变动或新增房贷需要手工改代码或通过 `/snap/events/recurring/:id` PUT 修改金额
2. 跨账户子类别白名单：`房贷/车贷/信贷/利息` 在算法里分组忽略 account，若 Moze 新增此类子类别需同步

## Master-Sub Account Management

账户支持主子账户层级关系，用于资金归集和分组管理。

### 核心字段

- `isMasterAccount` (boolean): 标记账户是否为主账户
- `masterAccountName` (string): 子账户所属主账户的名称（空表示独立账户）

### 分组级联规则

1. **子账户关联时**：子账户的 `accountGroup` 自动跟随主账户的分组
2. **主账户分组变更时**：所有子账户的分组自动同步到主账户的新分组
3. **信用卡分组特殊限制**：信用卡分组的主账户只能选择信用卡分组内的账户作为子账户（前端筛选限制）

### 余额统计规则

- **主账户余额** = 自身余额 + 所有直接子账户余额之和
- **分组余额** = 该分组下所有主账户余额（已包含其子账户）之和 + 独立账户余额之和（子账户不重复计入）

### 归档/删除规则

- 归档或删除主账户时，自动解绑所有子账户
- 子账户变为独立账户，不受影响

### 相关 API

`PUT /api/snapledger/accounts/{masterId}/sub-accounts/batch`
- 批量关联/解绑子账户
- 请求体: `{ action: "LINK"|"UNLINK", subAccountIds: number[] }`

### 前端组件

- `MasterAccountPicker`: 主账户选择器（支持"无"、现有主账户列表、快速创建）
- `SubAccountManager`: 子账户批量管理（多选、批量解绑、快速跳转创建）

## Code Style Guidelines

### Java

- **Lombok**: Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` to reduce boilerplate
- **Exception Handling**: Use global exception handler with `@RestControllerAdvice`, return `ApiResponse<T>` format
- **API Response**: All controllers must return `ApiResponse<T>` with code, data, message fields
- **Entity Naming**: Use plural for repository names (e.g., `StrategyRepository`), singular for entities
- **DTO Pattern**: Separate DTOs from entities, use MapStruct or manual mapping
- **Transaction Management**: Use `@Transactional` at service layer, specify rollbackFor = Exception

### Frontend (Vue 3)

- **Composition API**: Use `<script setup>` with `ref`, `reactive`, `computed`, `watch`
- **State Management**: Use Pinia stores for shared state (not Vuex)
- **TypeScript**: Prefer TypeScript over JavaScript, define interfaces for props and API responses
- **Component Structure**: Single-file components with `template` | `script setup` | `style` order
- **API Clients**: Auto-unpack `ApiResponse.data` via axios interceptors
- **Styling**: Use CSS variables from `variables.css`, avoid inline styles
