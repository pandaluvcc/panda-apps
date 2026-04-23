# app-snapledger

Snap Ledger 后端业务模块（Spring Boot 3.2 / Java 17 / JPA + MySQL）。负责账户、记录、预算、分类、OCR、CSV 导入、周期事件、分期事件、应收应付款项等全部记账相关的数据与服务。

> 本 README 面向在本模块开发的人。业务规则、跨模块约定、陷阱速查见仓库根目录 `CLAUDE.md`；每个功能的设计决策见 `docs/superpowers/specs/`。

---

## 启动与构建

本模块**不能独立启动**。唯一入口是 `panda-api`（依赖 `common` + `app-gridtrading` + `app-snapledger`）。

```bash
# 启动（开发）
cd panda-api && mvn spring-boot:run
# 或从仓库根：
mvn -pl panda-api spring-boot:run

# 单模块编译（调试编译错误最快）
mvn -pl app-snapledger -am compile

# 单模块打包
mvn -pl app-snapledger -am package -DskipTests
```

**测试放在 `panda-api/src/test/java/`**，不是本模块的 `src/test/`，因为只有 `panda-api` 看得到完整 classpath。用 `@SpringBootTest(classes = com.panda.PandaApplication.class)` 启动上下文。

```bash
# 跑本模块相关测试
mvn -pl panda-api -am test -Dtest='ReceivableLinkingServiceTest,ReceivableServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
```

---

## 目录结构

```
app-snapledger/src/main/
├── java/com/panda/snapledger/
│   ├── config/                  # Spring 配置（调度器开关等）
│   ├── domain/                  # JPA 实体
│   │   ├── Account.java         # 账户（支持主子层级、信用卡字段）
│   │   ├── AccountGroup.java    # 账户分组
│   │   ├── Category.java        # 分类
│   │   ├── Record.java          # 记账记录（含 parentRecordId / recurringEventId / installmentEventId）
│   │   ├── RecurringEvent.java  # 周期事件
│   │   ├── InstallmentEvent.java # 分期事件（只读聚合）
│   │   └── Budget.java          # 预算
│   ├── repository/              # Spring Data JPA
│   ├── service/                 # 业务
│   │   ├── AccountService.java          # 余额重算、主子账户管理
│   │   ├── AccountBalanceService.java   # 余额公式
│   │   ├── RecordService.java
│   │   ├── StatsService.java            # 收支统计、日历数据
│   │   ├── BudgetService.java
│   │   ├── OcrService.java              # 百度 OCR 集成
│   │   ├── PaymentScreenshotParser.java # 截图文本解析
│   │   ├── csvimport/
│   │   │   └── MozeCsvImporter.java     # 16 列 Moze CSV 解析 + 一次性历史迁移
│   │   ├── recurring/
│   │   │   ├── RecurringEventScheduler.java  # 每日 3 点扩展窗口
│   │   │   ├── RecurringEventGenerator.java  # 按期生成 Record
│   │   │   └── PeriodDateCalculator.java     # 日/周/月/年日期推算
│   │   ├── installment/
│   │   │   └── InstallmentDetectionService.java  # CSV 导入后启发式归并分期
│   │   └── receivable/
│   │       ├── ReceivableLinkingService.java  # 启发式建立父子链
│   │       └── ReceivableService.java         # 三态、汇总、增删改子
│   └── controller/              # 11 个 REST 控制器
│       ├── RecordController          # /api/snapledger/record
│       ├── AccountController         # /api/snapledger/accounts (含主子批量接口)
│       ├── AccountGroupController    # /api/snapledger/account-groups
│       ├── CategoryController        # /api/snapledger/category
│       ├── BudgetController          # /api/snapledger/budget
│       ├── StatsController           # /api/snapledger/stats
│       ├── CalendarController        # /api/snapledger/calendar
│       ├── OcrController             # /api/snapledger/ocr
│       ├── ImportController          # /api/snapledger/import
│       ├── RecurringEventController  # /api/snapledger/recurring-events
│       ├── InstallmentEventController # /api/snapledger/installment-events
│       └── ReceivableController      # /api/snapledger/receivables
└── resources/
    └── db/migration/            # Flyway V1..V7
```

---

## 核心数据流

### CSV 首次历史迁移

```
POST /api/snapledger/import  (multipart/form-data)
└─ MozeCsvImporter.importFromCsv
   ├─ 1. 解析 CSV（GBK/UTF-8 自动检测）
   ├─ 2. 去重（按 date|time|account|amount|recordType 指纹）
   ├─ 3. 批量保存账户 + 分类 + 记录
   ├─ 4. ensurePredefinedRecurringEvents  → 创建/修复预设周期事件，回溯挂接同名记录
   ├─ 5. RecurringEventScheduler.extendInfiniteWindows  → 生成未来 36 期
   ├─ 6. InstallmentDetectionService.detectAll  → 清空重建分期（幂等）
   └─ 7. ReceivableLinkingService.linkAll  → 清空重建父子链（幂等）
```

**⚠️ CSV 导入定位为一次性迁移**：步骤 6/7 都会清空重建，**会覆盖 app 里手工建立的分期和父子关系**。日常不要重复调。

### 日常新增记录

- 普通收支：`POST /api/snapledger/record` → `RecordService.save` → 触发相关账户余额重算
- 应收应付借出/借入：`POST /api/snapledger/receivables` → `ReceivableService.createParent`
- 应收应付收/还款：`POST /api/snapledger/receivables/{parentId}/children` → `ReceivableService.addChild`（显式关联，**不走启发式**，永远正确）

### 周期事件

- 日程：`RecurringEventScheduler @Scheduled(cron = "0 0 3 * * *")` 每天 3 点
  - 无限期事件：距离 `generatedUntil` < 180 天时扩展到 +36 期
  - 有限期事件：最后一期日期已过则自动 `ENDED`
- CSV 导入末尾也会触发一次，确保新配置立即生效

---

## Schema 关键字段

### Record（`sl_record` 表）

- `recordType`（13 种，见 CLAUDE.md 分类矩阵）
- `recurringEventId` + `periodNumber` — 软关联周期事件
- `installmentEventId` + `installmentPeriodNumber` — 软关联分期事件
- `parentRecordId` — 自引用，支撑应收应付父子关系（V7 migration）
- `reconciliationStatus` ∈ {`UNRECONCILED`, `CONFIRMED`, `POSTPONED`} + `postponedToCycle`

### Account

- `isMasterAccount` + `masterAccountName` — 主子账户层级
- `isCreditAccount` + `billingCycleDay` + `dueDay` — 信用卡字段，`dueDay` 用于计算还款窗口
- `includeInTotal` + `isArchived` — 首页汇总开关
- `sortOrder` + `accountGroup` — 分组与排序

### 外部账户常量（`MozeCsvImporter.ENSURE_ACCOUNTS`）

CSV 导入时保证存在的账户：`支付宝`, `微信`, `且慢`, `雪球基金`, `华宝证券`。根据 `ACCOUNT_CATALOG` 自动分类 group/sortOrder。

---

## 易踩的坑

1. **Controller 必须用 `ApiResponse<T>`**（`common` 模块定义）。前端 axios 拦截器会自动解包 `.data`，如果返回裸对象前端会拿不到数据。

2. **`@CrossOrigin(origins = "*")` 必须写在每个 Controller**，不是 common 配置。

3. **JPA 关系用软关联（long id）而非 `@ManyToOne`**。历史原因，所有实体间关联都是存 id + 查 repository，不走 Hibernate 关系。不要改这个约定——改一个就要改全部。

4. **CSV 导入会同步触发 4 个后置步骤**（预设事件、窗口扩展、分期识别、应收应付建链）。单独跑其中某一步用对应的调试接口（`POST /receivables/relink`、`POST /installment-events/detect`）。

5. **信用卡还款查询用窗口期不是账单周期**。`AccountBalanceService` 里涉及两套日期区间，改前先看 CLAUDE.md 的信用卡窗口期说明。

6. **`RecurringEventGenerator` 去重靠 `periodNumber`**，不是日期。如果你手工改了已生成记录的 `periodNumber`，调度器下次会再生成一条同日期但新 periodNumber 的记录。

7. **测试不能放在 `app-snapledger/src/test/`**。这里只能测纯单元逻辑（不 @SpringBootTest 的），需要上下文的集成测试必须放在 `panda-api/src/test/`。

---

## 开发流程

1. 新功能先写 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
2. 实施计划写 `docs/superpowers/plans/YYYY-MM-DD-<topic>-plan.md`
3. Schema 改动：新建 `src/main/resources/db/migration/V{N+1}__*.sql`（Flyway 会自动应用）
4. 实体 + 仓储 → Service → DTO → Controller → 前端 API → 前端页面
5. 集成测试放 `panda-api/src/test/java/com/panda/snapledger/<package>/`
6. Code 与 spec/plan 同提交

---

## 功能清单

| 功能 | 状态 | 关键类 | Spec |
|---|---|---|---|
| 账户 CRUD + 余额重算 | ✅ | `AccountService`, `AccountBalanceService` | — |
| 主子账户层级 | ✅ | `AccountService` 主子相关方法 | `2026-04-15-master-sub-account-management-design.md` |
| 账户分组 | ✅ | `AccountGroupController` | — |
| 记账 CRUD | ✅ | `RecordService`, `RecordController` | — |
| recordType 13 分类 | ✅ | `RecordRepository` 查询 | `2026-04-11-record-type-classification-design.md` |
| 分类 CRUD | ✅ | `CategoryController` | `2026-03-23-category-picker-design.md` |
| 记录表单/详情 | ✅ | — | `2026-03-24-record-form-redesign.md` |
| 账户详情（账单周期） | ✅ | `AccountBalanceService` | `2026-04-10-account-detail-design.md` |
| OCR 识图记账 | ✅ | `OcrService`, `PaymentScreenshotParser` | — |
| Moze CSV 一次性导入 | ✅ | `MozeCsvImporter` | — |
| 周期事件 | ✅ | `service/recurring/*` | `2026-04-18-recurring-events-design.md` |
| 分期事件（只读） | ✅ | `service/installment/*` | — |
| 应收应付款项 | ✅ | `service/receivable/*` | `2026-04-20-receivables-payables-design.md` |
| 预算管理 | ✅ | `BudgetService` | — |
| 日历 + 月度统计 | ✅ | `CalendarController`, `StatsController` | — |
