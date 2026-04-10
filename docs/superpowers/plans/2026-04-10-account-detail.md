# Account Detail Page Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Account Detail page (`/snap/account/:id`) with two tabs — Transaction Details and Account Info — supporting billing-cycle navigation, credit card stats, and an editable account form.

**Architecture:** Backend extends existing DTOs and service methods to support date-ranged queries and credit card stats. Frontend adds a new route, a shared composable for account form logic, and the AccountDetail page component.

**Tech Stack:** Spring Boot 3.2 / JPA (backend), Vue 3 Composition API + Vant (frontend)

**Spec:** `docs/superpowers/specs/2026-04-10-account-detail-design.md`

---

## File Map

### New
| File | Purpose |
|------|---------|
| `frontend/src/views/snapledger/AccountDetail.vue` | Main page: two tabs, period nav, stats, record lists |
| `frontend/src/composables/useAccountForm.js` | Shared account form state & validation |

### Modified
| File | Change |
|------|--------|
| `app-snapledger/.../dto/TransactionDTO.java` | Add `account`, `target`, `project` fields |
| `app-snapledger/.../dto/TransactionSummaryDTO.java` | Add `newExpense`, `paidAmount`, `confirmedCount`, `remainingDebt` (spec lists `previousDebt`/`billAmount` too, but those are frontend-computed — see Task 1 note) |
| `app-snapledger/.../repository/RecordRepository.java` | Add bidirectional transfer query + incoming transfer query |
| `app-snapledger/.../service/AccountService.java` | Fix English type bug, add date params to `getTransactions`, extend `getPeriodSummary` |
| `app-snapledger/.../controller/AccountController.java` | Add `startDate`/`endDate` params to `getTransactions` |
| `frontend/src/api/snapledger/account.js` | Add `getAccount`, `updateAccount`, `getAccountSummary`, `getAccountTransactions` |
| `frontend/src/api.js` | Re-export new account methods |
| `frontend/src/router.js` | Add `/snap/account/:id` route |
| `frontend/src/views/snapledger/Home.vue` | Add click handler on account rows |
| `frontend/src/views/snapledger/AddAccount.vue` | Use `useAccountForm` composable |

---

## Task 1: Extend TransactionDTO and TransactionSummaryDTO

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionDTO.java`
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionSummaryDTO.java`

- [ ] **Step 1: Add fields to TransactionDTO**

Replace the class body in `TransactionDTO.java`. Add three fields after `postponedToCycle` and update `fromEntity()`:

```java
// Add after postponedToCycle field:
private String account;   // 来源账户名
private String target;    // 目标账户名（转账）
private String project;   // 项目标签

// In fromEntity(), add after setPostponedToCycle():
dto.setAccount(record.getAccount());
dto.setTarget(record.getTarget());
dto.setProject(record.getProject());
```

- [ ] **Step 2: Add fields to TransactionSummaryDTO**

> **Note on spec deviation:** The spec's DTO table lists `previousDebt` and `billAmount` as backend fields. This plan intentionally omits them from the backend: `previousDebt` would require the backend to recursively call its own summary API, and `billAmount = previousDebt + newExpense` is trivially computed on the frontend. The frontend fetches the previous period's summary to obtain `previousDebt` separately. This is architecturally cleaner.

Add four fields to `TransactionSummaryDTO.java` after `periodEnd`:

```java
private BigDecimal newExpense;     // 新增支出（支出类记录绝对值之和，排除 POSTPONED）
private BigDecimal paidAmount;     // 已还金额（转入本账户的转账绝对值之和，排除 POSTPONED）
private Long confirmedCount;       // 对账笔数（CONFIRMED 状态的记录数）
private BigDecimal remainingDebt;  // max(0, newExpense - paidAmount)，前端用于"上期欠款"输入
```

- [ ] **Step 3: Compile check**

```bash
cd D:/01-develop/02-project/panda/panda-apps
mvn compile -pl app-snapledger -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionDTO.java
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/TransactionSummaryDTO.java
git commit -m "feat(snapledger): extend TransactionDTO and TransactionSummaryDTO for account detail"
```

---

## Task 2: Add RecordRepository Queries

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java`

- [ ] **Step 1: Add three new query methods**

Append to `RecordRepository.java` before the closing brace:

```java
// 双向查询转账记录（转出 + 转入），按日期时间倒序
@Query("SELECT r FROM Record r WHERE (r.account = :account OR r.target = :account) " +
       "AND r.date BETWEEN :start AND :end AND r.recordType = '转账' " +
       "ORDER BY r.date DESC, r.time DESC")
List<Record> findTransfersByAccountOrTargetAndDateBetween(
        @Param("account") String account,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);

// 查询非转账记录（按账户和日期范围）
@Query("SELECT r FROM Record r WHERE r.account = :account " +
       "AND r.date BETWEEN :start AND :end AND r.recordType != '转账' " +
       "ORDER BY r.date DESC, r.time DESC")
List<Record> findNonTransfersByAccountAndDateBetween(
        @Param("account") String account,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);

// 查询收到的转账（target=本账户），排除 POSTPONED，用于计算已还金额
@Query("SELECT r FROM Record r WHERE r.target = :account " +
       "AND r.date BETWEEN :start AND :end AND r.recordType = '转账' " +
       "AND r.reconciliationStatus != :status " +
       "ORDER BY r.date DESC, r.time DESC")
List<Record> findIncomingTransfersByTargetAndDateBetweenAndStatusNot(
        @Param("account") String account,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("status") String status);
```

- [ ] **Step 2: Compile check**

```bash
mvn compile -pl app-snapledger -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/repository/RecordRepository.java
git commit -m "feat(snapledger): add bidirectional transfer and incoming transfer repository queries"
```

---

## Task 3: Update AccountService

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java`

Changes:
1. Fix the English type bug in `getPeriodSummary` (uses `"income"`/`"expense"` instead of `"收入"`/`"支出"`)
2. Add date params to `getTransactions`
3. Extend `getPeriodSummary` with credit card stats

- [ ] **Step 1: Fix `getTransactions` — add date params and bidirectional transfers**

Replace the entire `getTransactions` method:

```java
/**
 * 获取账户交易明细（按周期过滤）
 * 转账记录双向展示；非转账记录只查本账户
 */
public List<TransactionDTO> getTransactions(Long accountId, LocalDate startDate, LocalDate endDate) {
    Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

    List<Record> transfers = recordRepository.findTransfersByAccountOrTargetAndDateBetween(
            account.getName(), startDate, endDate);

    List<Record> nonTransfers = recordRepository.findNonTransfersByAccountAndDateBetween(
            account.getName(), startDate, endDate);

    List<Record> all = new java.util.ArrayList<>();
    all.addAll(transfers);
    all.addAll(nonTransfers);
    all.sort(java.util.Comparator
            .comparing(Record::getDate).reversed()
            .thenComparing(java.util.Comparator.comparing(
                    Record::getTime, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))));

    return all.stream().map(TransactionDTO::fromEntity).collect(Collectors.toList());
}
```

- [ ] **Step 2: Fix `getPeriodSummary` — fix English bug + add credit card stats**

Replace the entire `getPeriodSummary` method:

```java
/**
 * 获取周期统计
 * - 普通账户：本期收入、本期支出、对账笔数
 * - 信用卡：新增支出、已还金额、对账笔数、remainingDebt（仍需还款，不含上期欠款）
 * 全部排除 POSTPONED 记录
 */
public TransactionSummaryDTO getPeriodSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
    Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

    // 非转账记录（排除 POSTPONED）
    List<Record> nonTransfers = recordRepository.findByAccountAndDateBetweenAndReconciliationStatusNot(
            account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED);

    BigDecimal income = BigDecimal.ZERO;
    BigDecimal expense = BigDecimal.ZERO;
    BigDecimal fee = BigDecimal.ZERO;
    long confirmedCount = 0;

    for (Record record : nonTransfers) {
        if ("收入".equals(record.getRecordType())) {
            income = income.add(record.getAmount().abs());
        } else if ("支出".equals(record.getRecordType())) {
            expense = expense.add(record.getAmount().abs());
        }
        if (record.getFee() != null) {
            fee = fee.add(record.getFee());
        }
        if (Record.RECONCILIATION_CONFIRMED.equals(record.getReconciliationStatus())) {
            confirmedCount++;
        }
    }

    // 收到的转账（转入本账户），用于已还金额
    List<Record> incomingTransfers = recordRepository.findIncomingTransfersByTargetAndDateBetweenAndStatusNot(
            account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED);
    BigDecimal paidAmount = incomingTransfers.stream()
            .map(r -> r.getAmount().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // remainingDebt = max(0, newExpense - paidAmount)（不含上期欠款，上期欠款由前端额外请求）
    BigDecimal remainingDebt = expense.subtract(paidAmount).max(BigDecimal.ZERO);

    TransactionSummaryDTO summary = new TransactionSummaryDTO();
    summary.setTotalIncome(income);
    summary.setTotalExpense(expense);
    summary.setTotalFee(fee);
    summary.setNetAmount(income.subtract(expense));
    summary.setRecordCount((long) nonTransfers.size());
    summary.setPeriodStart(startDate);
    summary.setPeriodEnd(endDate);
    summary.setNewExpense(expense);
    summary.setPaidAmount(paidAmount);
    summary.setConfirmedCount(confirmedCount);
    summary.setRemainingDebt(remainingDebt);

    return summary;
}
```

- [ ] **Step 3: Compile check**

```bash
mvn compile -pl app-snapledger -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java
git commit -m "fix(snapledger): fix recordType English bug in getPeriodSummary; add date params and credit card stats"
```

---

## Task 4: Update AccountController

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java`

- [ ] **Step 1: Expose `GET /{id}` single account endpoint**

The controller is missing a `GET /{id}` endpoint even though `AccountService.getAccount(Long id)` already exists. The frontend calls `getAccount(id)` → `GET /snapledger/accounts/{id}` — without this, the page will 404 on load.

Add this endpoint after the `getAll()` method:

```java
@GetMapping("/{id}")
@Operation(summary = "获取账户详情")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "成功获取账户"),
    @ApiResponse(responseCode = "404", description = "账户不存在")
})
public AccountDTO getById(@Parameter(description = "账户 ID") @PathVariable Long id) {
    return accountService.getAccount(id);
}
```

- [ ] **Step 2: Add `startDate`/`endDate` params to `getTransactions`**

Replace the `getTransactions` endpoint:

```java
@GetMapping("/{id}/transactions")
@Operation(summary = "获取交易明细")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "成功获取交易明细"),
    @ApiResponse(responseCode = "404", description = "账户不存在")
})
public List<TransactionDTO> getTransactions(
        @Parameter(description = "账户 ID") @PathVariable Long id,
        @Parameter(description = "开始日期") @RequestParam LocalDate startDate,
        @Parameter(description = "结束日期") @RequestParam LocalDate endDate) {
    return accountService.getTransactions(id, startDate, endDate);
}
```

- [ ] **Step 3: Compile check**

```bash
mvn compile -pl app-snapledger -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java
git commit -m "feat(snapledger): expose GET /accounts/{id}; add date range params to getTransactions"
```

---

## Task 5: Frontend API Layer

**Files:**
- Modify: `frontend/src/api/snapledger/account.js`
- Modify: `frontend/src/api.js`

- [ ] **Step 1: Add new API methods to account.js**

Replace the entire file:

```javascript
import api from '../index'

export function getAccounts() {
  return api.get('/snapledger/accounts')
}

export function createAccount(data) {
  return api.post('/snapledger/accounts', data)
}

export function getAccount(id) {
  return api.get(`/snapledger/accounts/${id}`)
}

export function updateAccount(id, data) {
  return api.put(`/snapledger/accounts/${id}`, data)
}

export function getAccountTransactions(id, startDate, endDate) {
  return api.get(`/snapledger/accounts/${id}/transactions`, {
    params: { startDate, endDate }
  })
}

export function getAccountSummary(id, startDate, endDate) {
  return api.get(`/snapledger/accounts/${id}/summary`, {
    params: { startDate, endDate }
  })
}
```

- [ ] **Step 2: Re-export new methods in api.js**

Find the line `export * from '@/api/snapledger/account'` in `frontend/src/api.js` — it's already there. The new named exports from `account.js` are automatically included.

Verify by checking `frontend/src/api.js` contains:
```javascript
export * from '@/api/snapledger/account'
```

If it's already there, no change needed. If missing, add it alongside the other snapledger exports.

- [ ] **Step 3: Commit**

```bash
cd D:/01-develop/02-project/panda/panda-apps
git add frontend/src/api/snapledger/account.js
git commit -m "feat(frontend): add getAccount, updateAccount, getAccountTransactions, getAccountSummary APIs"
```

---

## Task 6: Router and Home.vue Click Handler

**Files:**
- Modify: `frontend/src/router.js`
- Modify: `frontend/src/views/snapledger/Home.vue`

- [ ] **Step 1: Add route to router.js**

The project uses eager imports for all existing routes. Follow the same pattern.

**First**, add a top-level import after the existing snap imports:

```javascript
import SnapAccountDetail from '@/views/snapledger/AccountDetail.vue'
```

**Then**, add the route after the `SnapAddAccount` route in the `routes` array:

```javascript
{
  path: '/snap/account/:id',
  name: 'SnapAccountDetail',
  component: SnapAccountDetail,
  meta: { module: 'snapledger', transition: 'page-slide' }
},
```

Important: the static `/snap/account/add` route must remain BEFORE this dynamic route to avoid `add` being matched as an `:id`. Vue Router 4 matches static routes first regardless of registration order, but keeping static routes before dynamic ones is clearer.

- [ ] **Step 2: Add click handler to account rows in Home.vue**

In `Home.vue`, find the account row `div` (around line 73):

```html
<div
  v-for="acc in group.accounts"
  :key="acc.id"
  class="account-row"
>
```

Add `@click` and `cursor: pointer` styling:

```html
<div
  v-for="acc in group.accounts"
  :key="acc.id"
  class="account-row"
  style="cursor: pointer"
  @click="$router.push('/snap/account/' + acc.id)"
>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/router.js frontend/src/views/snapledger/Home.vue
git commit -m "feat(frontend): add AccountDetail route and account row click navigation"
```

---

## Task 7: useAccountForm Composable

**Files:**
- Create: `frontend/src/composables/useAccountForm.js`
- Modify: `frontend/src/views/snapledger/AddAccount.vue`

The composable encapsulates: form state, dirty-state tracking, `loadFromAccount(data)` to populate from an existing account, and `toPayload()` to build the API request body.

- [ ] **Step 1: Create useAccountForm.js**

```javascript
// frontend/src/composables/useAccountForm.js
import { reactive, ref, computed } from 'vue'

export function useAccountForm() {
  const form = reactive({
    name: '',
    mainCurrency: 'CNY',
    accountGroup: '第三方支付',
    initialBalance: 0,
    billCycleStart: null,
    billCycleEnd: null,
    isCreditAccount: false,
    autoRollover: false,
    foreignTransactionFee: false,
    includeInTotal: true,
    remark: ''
  })

  // Track original values to detect unsaved changes
  const originalJson = ref('')
  const isDirty = computed(() => JSON.stringify(form) !== originalJson.value)

  function snapshot() {
    originalJson.value = JSON.stringify(form)
  }

  function loadFromAccount(account) {
    form.name = account.name || ''
    form.mainCurrency = account.mainCurrency || 'CNY'
    form.accountGroup = account.accountGroup || '第三方支付'
    form.initialBalance = account.initialBalance ?? 0
    form.billCycleStart = account.billCycleStart || null
    form.billCycleEnd = account.billCycleEnd || null
    form.isCreditAccount = account.isCreditAccount || false
    form.autoRollover = account.autoRollover || false
    form.foreignTransactionFee = account.foreignTransactionFee || false
    form.includeInTotal = account.includeInTotal !== false
    form.remark = account.remark || ''
    snapshot()
  }

  function toPayload() {
    return {
      name: form.name,
      mainCurrency: form.mainCurrency,
      accountGroup: form.accountGroup,
      initialBalance: Number(form.initialBalance),
      billCycleStart: form.billCycleStart,
      billCycleEnd: form.billCycleEnd,
      isCreditAccount: form.isCreditAccount,
      autoRollover: form.autoRollover,
      foreignTransactionFee: form.foreignTransactionFee,
      includeInTotal: form.includeInTotal,
      remark: form.remark
    }
  }

  function validate() {
    if (!form.name || !form.name.trim()) {
      return '账户名称不能为空'
    }
    return null
  }

  return { form, isDirty, snapshot, loadFromAccount, toPayload, validate }
}
```

- [ ] **Step 2: Refactor AddAccount.vue to use composable**

In `AddAccount.vue`:
1. Add import: `import { useAccountForm } from '@/composables/useAccountForm'`
2. Replace the inline `form` reactive object with `const { form, isDirty, toPayload, validate } = useAccountForm()`
3. In the save function, call `validate()` instead of the inline name check
4. In the payload construction, call `toPayload()` instead of building the object manually
5. In the back/cancel guard, replace the dirty-check condition with `isDirty.value`

Note: AddAccount.vue passes `billCycleStart`/`billCycleEnd` as ISO date strings. Ensure `toPayload()` matches the existing format (the current code converts date picker values to ISO strings — keep that conversion in the component's save handler, not in the composable).

- [ ] **Step 3: Verify AddAccount still works**

Manually test: open `/snap/account/add`, fill form, save. Verify account is created.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/composables/useAccountForm.js frontend/src/views/snapledger/AddAccount.vue
git commit -m "refactor(frontend): extract useAccountForm composable from AddAccount.vue"
```

---

## Task 8: AccountDetail.vue — Main Page

**Files:**
- Create: `frontend/src/views/snapledger/AccountDetail.vue`

This task builds the full page. We'll build it section by section and test after each.

### 8a: Page Shell, Period Navigation Logic

- [ ] **Step 1: Create AccountDetail.vue with shell structure**

```vue
<template>
  <div class="account-detail">
    <!-- Top nav -->
    <div class="nav-bar">
      <button class="nav-icon-btn" @click="handleBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 18l-6-6 6-6"/>
        </svg>
      </button>
      <span class="nav-title">{{ account?.name || '账户详情' }}</span>
      <div style="width: 40px"/>
    </div>

    <!-- Tab bar -->
    <div class="tab-bar">
      <button
        v-for="tab in ['交易明细', '账户信息']"
        :key="tab"
        :class="['tab-btn', { active: activeTab === tab }]"
        @click="activeTab = tab"
      >{{ tab }}</button>
    </div>

    <!-- Tab content -->
    <div class="tab-content">
      <template v-if="activeTab === '交易明细'">
        <!-- Period navigation -->
        <div class="period-nav">
          <button class="period-arrow" @click="shiftPeriod(-1)">‹</button>
          <span class="period-label">{{ periodLabel }}</span>
          <button class="period-arrow" @click="shiftPeriod(1)">›</button>
        </div>
      </template>

      <template v-if="activeTab === '账户信息'">
        <div style="padding: 16px; color: #999; text-align: center">账户信息 tab（待实现）</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccount } from '@/api'

const route = useRoute()
const router = useRouter()
const account = ref(null)
const activeTab = ref('交易明细')

// Period state
const periodStart = ref('')
const periodEnd = ref('')

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function computeDefaultPeriod(acc) {
  const today = new Date()
  if (acc.isCreditAccount && acc.billCycleStart) {
    const cycleDay = new Date(acc.billCycleStart).getDate()
    let y = today.getFullYear()
    let m = today.getMonth()
    if (today.getDate() < cycleDay) {
      m -= 1
      if (m < 0) { m = 11; y -= 1 }
    }
    const start = new Date(y, m, cycleDay)
    // end = cycleDay - 1 of next month
    const endRaw = new Date(y, m + 1, cycleDay - 1)
    return { start: formatDate(start), end: formatDate(endRaw) }
  } else {
    const start = new Date(today.getFullYear(), today.getMonth(), 1)
    const end = new Date(today.getFullYear(), today.getMonth() + 1, 0)
    return { start: formatDate(start), end: formatDate(end) }
  }
}

function shiftPeriod(dir) {
  if (account.value?.isCreditAccount) {
    // Reconstruct from cycleDay to avoid month-overflow (e.g., Jan 31 + 1 month ≠ Feb 31)
    const cycleDay = new Date(account.value.billCycleStart).getDate()
    const s = new Date(periodStart.value)
    let newYear = s.getFullYear()
    let newMonth = s.getMonth() + dir
    if (newMonth < 0) { newMonth += 12; newYear -= 1 }
    if (newMonth > 11) { newMonth -= 12; newYear += 1 }
    const newStart = new Date(newYear, newMonth, cycleDay)
    // end = (cycleDay - 1) of the following month; JS handles day 0 = last day of prev month
    const newEnd = new Date(newYear, newMonth + 1, cycleDay - 1)
    periodStart.value = formatDate(newStart)
    periodEnd.value = formatDate(newEnd)
  } else {
    const s = new Date(periodStart.value)
    s.setMonth(s.getMonth() + dir)
    const newStart = new Date(s.getFullYear(), s.getMonth(), 1)
    const newEnd = new Date(s.getFullYear(), s.getMonth() + 1, 0)
    periodStart.value = formatDate(newStart)
    periodEnd.value = formatDate(newEnd)
  }
}

const periodLabel = computed(() => {
  if (!periodStart.value) return ''
  if (account.value?.isCreditAccount) {
    return `${periodStart.value.replace(/-/g, '/')} - ${periodEnd.value.replace(/-/g, '/')}`
  } else {
    const d = new Date(periodStart.value)
    return `${d.getFullYear()}年${String(d.getMonth() + 1).padStart(2, '0')}月`
  }
})

function handleBack() {
  router.back()
}

onMounted(async () => {
  account.value = await getAccount(route.params.id)
  const { start, end } = computeDefaultPeriod(account.value)
  periodStart.value = start
  periodEnd.value = end
})
</script>

<style scoped>
.account-detail {
  min-height: 100vh;
  background: #f7f8fa;
}
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.nav-title { font-size: 17px; font-weight: 600; }
.nav-icon-btn {
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: none; border: none; cursor: pointer; color: #333;
}
.nav-icon-btn svg { width: 22px; height: 22px; }
.tab-bar {
  display: flex;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.tab-btn {
  flex: 1; padding: 12px;
  background: none; border: none;
  font-size: 14px; color: #666; cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color .2s, border-color .2s;
}
.tab-btn.active { color: #1989fa; border-bottom-color: #1989fa; font-weight: 600; }
.tab-content { overflow-y: auto; }
.period-nav {
  display: flex; align-items: center; justify-content: center;
  gap: 24px; padding: 16px;
  background: #fff; margin-bottom: 12px;
}
.period-arrow {
  font-size: 24px; color: #666;
  background: none; border: none; cursor: pointer; padding: 4px 8px;
}
.period-label { font-size: 15px; font-weight: 500; min-width: 160px; text-align: center; }
</style>
```

- [ ] **Step 2: Verify page shell navigates and shows period**

Start frontend dev server, navigate to an account row on Home page, verify:
- Page opens
- Back button works
- Tab switching works
- Period label shows correctly for current month

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(frontend): AccountDetail page shell with period navigation"
```

---

### 8b: Stats Section

- [ ] **Step 1: Add stats data loading and display**

Inside the `交易明细` template section, after the period-nav div, add:

```html
<!-- Stats section -->
<div v-if="summary" class="stats-card">
  <template v-if="account?.isCreditAccount">
    <div class="stat-row">
      <span class="stat-label">新增支出</span>
      <span class="stat-value">￥{{ fmt(summary.newExpense) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">上期欠款</span>
      <span class="stat-value">-￥{{ fmt(previousDebt) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">应还账单</span>
      <span class="stat-value">-￥{{ fmt(billAmount) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">已还金额</span>
      <span class="stat-value">￥{{ fmt(summary.paidAmount) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">账单分期</span>
      <span class="stat-value stat-na">---</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">对账笔数</span>
      <span class="stat-value">{{ summary.confirmedCount ?? 0 }}</span>
    </div>
    <div class="stat-row stat-row--highlight">
      <span class="stat-label">仍需还款</span>
      <span class="stat-value stat-debt">-￥{{ fmt(remainingDebt) }}</span>
    </div>
  </template>

  <template v-else>
    <div class="stat-row">
      <span class="stat-label">本期支出</span>
      <span class="stat-value">￥{{ fmt(summary.totalExpense) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">本期收入</span>
      <span class="stat-value">￥{{ fmt(summary.totalIncome) }}</span>
    </div>
    <div class="stat-row">
      <span class="stat-label">对账笔数</span>
      <span class="stat-value">{{ summary.confirmedCount ?? 0 }}</span>
    </div>
  </template>
</div>
<div v-else-if="statsLoading" class="stats-card">
  <van-loading color="#999" size="18" />
</div>
```

Add to `<script setup>`:

```javascript
import { ref, computed, onMounted, watch } from 'vue'
import { getAccount, getAccountSummary } from '@/api'

const summary = ref(null)
const prevSummary = ref(null)
const statsLoading = ref(false)

function fmt(val) {
  if (val == null) return '0.00'
  return Number(val).toFixed(2)
}

// Compute previous period dates (same reconstruction logic as shiftPeriod)
function getPrevPeriodDates() {
  if (account.value?.isCreditAccount) {
    const cycleDay = new Date(account.value.billCycleStart).getDate()
    const s = new Date(periodStart.value)
    let prevYear = s.getFullYear()
    let prevMonth = s.getMonth() - 1
    if (prevMonth < 0) { prevMonth = 11; prevYear -= 1 }
    const prevStart = new Date(prevYear, prevMonth, cycleDay)
    const prevEnd = new Date(prevYear, prevMonth + 1, cycleDay - 1)
    return { start: formatDate(prevStart), end: formatDate(prevEnd) }
  } else {
    const s = new Date(periodStart.value)
    const prevStart = new Date(s.getFullYear(), s.getMonth() - 1, 1)
    const prevEnd = new Date(s.getFullYear(), s.getMonth(), 0)
    return { start: formatDate(prevStart), end: formatDate(prevEnd) }
  }
}

const previousDebt = computed(() => prevSummary.value?.remainingDebt ?? 0)
const billAmount = computed(() => {
  if (!summary.value) return 0
  return Number(previousDebt.value) + Number(summary.value.newExpense ?? 0)
})
const remainingDebt = computed(() => {
  const debt = billAmount.value - Number(summary.value?.paidAmount ?? 0)
  return Math.max(0, debt)
})

async function loadStats() {
  if (!account.value || !periodStart.value) return
  statsLoading.value = true
  try {
    const id = route.params.id
    // Fetch current period summary
    summary.value = await getAccountSummary(id, periodStart.value, periodEnd.value)
    // Fetch previous period for 上期欠款
    if (account.value.isCreditAccount) {
      const prev = getPrevPeriodDates()
      prevSummary.value = await getAccountSummary(id, prev.start, prev.end)
    }
  } finally {
    statsLoading.value = false
  }
}

// Reload stats when period changes
watch([periodStart, periodEnd], loadStats)
```

Also call `loadStats()` at the end of `onMounted`, after setting the period.

Add to `<style scoped>`:

```css
.stats-card {
  background: #fff; margin: 0 12px 12px; border-radius: 12px; padding: 4px 0;
}
.stat-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #f5f5f5;
}
.stat-row:last-child { border-bottom: none; }
.stat-label { font-size: 14px; color: #666; }
.stat-value { font-size: 14px; font-weight: 500; color: #333; }
.stat-na { color: #bbb; }
.stat-debt { color: #f56c6c; }
.stat-row--highlight { background: #fafafa; }
```

- [ ] **Step 2: Test stats display**

Verify stats section renders for both credit and regular accounts. Check network tab shows two summary requests for credit accounts (one for current period, one for previous).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(frontend): AccountDetail stats section with credit card and regular account fields"
```

---

### 8c: Record Lists

- [ ] **Step 1: Add transaction data loading**

Add to `<script setup>`:

```javascript
import { getAccountTransactions } from '@/api'

const transfers = ref([])
const nonTransfers = ref([])
const txLoading = ref(false)
const sortDesc = ref(true) // default: date descending

async function loadTransactions() {
  if (!account.value || !periodStart.value) return
  txLoading.value = true
  try {
    const all = await getAccountTransactions(route.params.id, periodStart.value, periodEnd.value)
    transfers.value = all.filter(r => r.recordType === '转账')
    nonTransfers.value = all.filter(r => r.recordType !== '转账')
  } finally {
    txLoading.value = false
  }
}

const sortedNonTransfers = computed(() => {
  const list = [...nonTransfers.value]
  if (!sortDesc.value) list.reverse()
  return list
})

// Reload transactions when period changes
watch([periodStart, periodEnd], loadTransactions)
```

Also call `loadTransactions()` in `onMounted`.

- [ ] **Step 2: Add record list UI after stats section**

After the stats-card div, add:

```html
<!-- Transfer records -->
<div class="section-card">
  <div class="section-header">
    <span class="section-title">转账记录（{{ transfers.length }}）</span>
    <button class="add-btn" @click="goAddRecord('转账')">+</button>
  </div>
  <div v-if="transfers.length === 0" class="empty-tip">本周期暂无转账记录</div>
  <template v-else>
    <div v-for="tx in transfers" :key="tx.id" class="record-row">
      <div class="record-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="1.5">
          <path d="M8 7h12M8 12h12M8 17h12M4 7v.01M4 12v.01M4 17v.01"/>
        </svg>
      </div>
      <div class="record-mid">
        <div class="record-name">{{ tx.name || tx.mainCategory }}</div>
        <div class="record-sub">{{ tx.account }} → {{ tx.target }}</div>
      </div>
      <div class="record-right">
        <div class="record-amount">￥{{ fmt(tx.amount) }}</div>
        <span class="tag tag-transfer">还款</span>
      </div>
    </div>
  </template>
</div>

<!-- Non-transfer records -->
<div class="section-card">
  <div class="section-header">
    <span class="section-title">一般记录（{{ nonTransfers.length }}）</span>
    <div class="section-actions">
      <button class="sort-btn" @click="sortDesc = !sortDesc" :title="sortDesc ? '当前：倒序' : '当前：正序'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path v-if="sortDesc" d="M3 4h13M3 8h9M3 12h5M15 12l4 4 4-4M19 8v8"/>
          <path v-else d="M3 4h13M3 8h9M3 12h5M15 12l4-4 4 4M19 8v8"/>
        </svg>
      </button>
      <button class="add-btn" @click="goAddRecord()">+</button>
    </div>
  </div>
  <div v-if="sortedNonTransfers.length === 0" class="empty-tip">本周期暂无记录</div>
  <template v-else>
    <div v-for="tx in sortedNonTransfers" :key="tx.id" class="record-row">
      <div class="record-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="#999" stroke-width="1.5">
          <circle cx="12" cy="12" r="9"/>
          <path d="M12 8v4l3 3"/>
        </svg>
      </div>
      <div class="record-mid">
        <div class="record-name">{{ tx.subCategory || tx.mainCategory }}</div>
        <div class="record-sub">
          <span v-if="tx.merchant">{{ tx.merchant }}</span>
          <span v-if="tx.project" class="tag tag-project">{{ tx.project }}</span>
          <span class="tag tag-account">{{ tx.account }}</span>
        </div>
      </div>
      <div class="record-right">
        <div class="record-amount" :class="tx.recordType === '收入' ? 'amount-income' : ''">
          {{ tx.recordType === '收入' ? '+' : '' }}￥{{ fmt(tx.amount) }}
        </div>
      </div>
    </div>
  </template>
</div>
```

Add to `<script setup>`:

```javascript
function goAddRecord(type) {
  router.push({ path: '/snap/add', query: { accountId: route.params.id, type } })
}
```

Add to `<style scoped>`:

```css
.section-card {
  background: #fff; margin: 0 12px 12px; border-radius: 12px; overflow: hidden;
}
.section-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid #f5f5f5;
}
.section-title { font-size: 14px; font-weight: 600; color: #333; }
.section-actions { display: flex; align-items: center; gap: 8px; }
.add-btn {
  width: 28px; height: 28px; border-radius: 50%;
  background: #1989fa; color: #fff; border: none; cursor: pointer;
  font-size: 18px; line-height: 1; display: flex; align-items: center; justify-content: center;
}
.sort-btn {
  width: 28px; height: 28px; background: none; border: none;
  cursor: pointer; color: #666; display: flex; align-items: center; justify-content: center;
}
.sort-btn svg { width: 18px; height: 18px; }
.record-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-bottom: 1px solid #f8f8f8;
}
.record-row:last-child { border-bottom: none; }
.record-icon { width: 36px; height: 36px; flex-shrink: 0; }
.record-icon svg { width: 36px; height: 36px; }
.record-mid { flex: 1; min-width: 0; }
.record-name { font-size: 14px; color: #333; font-weight: 500; }
.record-sub { font-size: 12px; color: #999; margin-top: 2px; display: flex; gap: 4px; flex-wrap: wrap; }
.record-right { text-align: right; flex-shrink: 0; }
.record-amount { font-size: 15px; font-weight: 500; color: #333; }
.amount-income { color: #67c23a; }
.tag {
  display: inline-block; padding: 1px 6px; border-radius: 10px;
  font-size: 11px; line-height: 1.6;
}
.tag-transfer { background: #e8f4ff; color: #1989fa; }
.tag-project { background: #f0f9ff; color: #409eff; }
.tag-account { background: #f5f5f5; color: #999; }
.empty-tip { padding: 24px; text-align: center; color: #bbb; font-size: 13px; }
```

- [ ] **Step 3: Test record lists**

Verify records load, transfers show bidirectional direction, sort toggle works, + button navigates to add-record with accountId in query.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(frontend): AccountDetail transaction record lists with sort and add button"
```

---

### 8d: Account Info Tab

- [ ] **Step 1: Add account info tab content**

Import composable and add to `<script setup>`:

```javascript
import { useAccountForm } from '@/composables/useAccountForm'
import { updateAccount } from '@/api'
import { showConfirmDialog, showToast } from 'vant'

const { form: infoForm, isDirty, loadFromAccount, toPayload, validate } = useAccountForm()
const saving = ref(false)

// Load account data into form when account is fetched
watch(account, (acc) => {
  if (acc) loadFromAccount(acc)
}, { immediate: true })

async function saveAccountInfo() {
  const err = validate()
  if (err) { showToast(err); return }
  saving.value = true
  try {
    await updateAccount(route.params.id, toPayload())
    showToast('保存成功')
    // Reload account to sync changes
    account.value = await getAccount(route.params.id)
    loadFromAccount(account.value) // reset dirty state
  } catch (e) {
    showToast('保存失败: ' + (e.message || e))
  } finally {
    saving.value = false
  }
}

async function handleBack() {
  if (activeTab.value === '账户信息' && isDirty.value) {
    try {
      await showConfirmDialog({ title: '有未保存的更改', message: '确定放弃更改并返回？', confirmButtonText: '放弃' })
    } catch {
      return // user cancelled
    }
  }
  router.back()
}
```

Replace the existing `handleBack` function with the above.

Add account info tab template (replace the placeholder):

```html
<template v-if="activeTab === '账户信息'">
  <div class="info-form">
    <!-- Name -->
    <div class="form-section">
      <div class="form-item">
        <span class="form-label">账户名称</span>
        <input v-model="infoForm.name" class="form-input" placeholder="请输入账户名称" />
      </div>
      <div class="form-item">
        <span class="form-label">主币种</span>
        <span class="form-value">{{ infoForm.mainCurrency }}</span>
      </div>
      <div class="form-item">
        <span class="form-label">账户分组</span>
        <span class="form-value">{{ infoForm.accountGroup }}</span>
      </div>
      <div class="form-item">
        <span class="form-label">初始余额</span>
        <input v-model.number="infoForm.initialBalance" class="form-input" type="number" placeholder="0" />
      </div>
    </div>

    <div class="form-section">
      <div class="form-item form-item--switch">
        <span class="form-label">信用账户</span>
        <van-switch v-model="infoForm.isCreditAccount" size="22" />
      </div>
      <div class="form-item form-item--switch">
        <span class="form-label">自动转存</span>
        <van-switch v-model="infoForm.autoRollover" size="22" />
      </div>
      <div class="form-item form-item--switch">
        <span class="form-label">国外手续费</span>
        <van-switch v-model="infoForm.foreignTransactionFee" size="22" />
      </div>
      <div class="form-item form-item--switch">
        <span class="form-label">纳入总余额</span>
        <van-switch v-model="infoForm.includeInTotal" size="22" />
      </div>
    </div>

    <div class="form-section">
      <div class="form-item">
        <span class="form-label">备注</span>
        <input v-model="infoForm.remark" class="form-input" placeholder="可选" />
      </div>
    </div>

    <div class="save-area">
      <van-button
        type="primary" block round
        :loading="saving"
        :disabled="!isDirty"
        @click="saveAccountInfo"
      >保存</van-button>
    </div>
  </div>
</template>
```

Add to `<style scoped>`:

```css
.info-form { padding: 12px; }
.form-section {
  background: #fff; border-radius: 12px; margin-bottom: 12px; overflow: hidden;
}
.form-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid #f5f5f5;
}
.form-item:last-child { border-bottom: none; }
.form-item--switch { min-height: 52px; }
.form-label { font-size: 14px; color: #333; flex-shrink: 0; }
.form-value { font-size: 14px; color: #666; }
.form-input {
  flex: 1; text-align: right; border: none; outline: none;
  font-size: 14px; color: #333; background: transparent;
}
.save-area { padding: 8px 0 24px; }
```

- [ ] **Step 2: Test account info tab**

Verify:
- Form pre-fills with current account data
- Editing a field enables the Save button
- Save calls PUT endpoint and shows toast
- Back with unsaved changes shows confirmation dialog

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(frontend): AccountDetail account info tab with form edit and save"
```

---

## Task 9: Backend Build and Smoke Test

- [ ] **Step 1: Full backend build**

```bash
cd D:/01-develop/02-project/panda/panda-apps
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Smoke test endpoints (after restarting the backend)**

Test with any HTTP client (browser, curl, or Postman):

```
GET /api/snapledger/accounts                              → list of accounts
GET /api/snapledger/accounts/{id}                         → single account
GET /api/snapledger/accounts/{id}/transactions?startDate=2026-04-03&endDate=2026-05-02
GET /api/snapledger/accounts/{id}/summary?startDate=2026-04-03&endDate=2026-05-02
PUT /api/snapledger/accounts/{id}  (body: updated AccountDTO)
```

Verify:
- Transactions response includes `account`, `target`, `project` fields
- Summary response includes `newExpense`, `paidAmount`, `confirmedCount`, `remainingDebt`
- Transfer records appear in transactions response for both the source and target account

- [ ] **Step 3: Final commit and tag**

```bash
git add .
git commit -m "feat: Account Detail page — transaction details + account info tabs"
git tag v1.4.0
git push && git push --tags
```

---

## Quick Reference: Period Logic

```
Credit card (isCreditAccount=true):
  cycleDay = billCycleStart.getDate()  // e.g., 3
  if today.getDate() < cycleDay:
    start = (month-1, cycleDay)
  else:
    start = (month, cycleDay)
  end = (start.month+1, cycleDay-1)

  Example: billCycleStart=3, today=Apr 9
    start = Apr 3, end = May 2

  Shift by 1: start month ±1, end month ±1

Regular account:
  start = first day of current month
  end = last day of current month
  Shift: month ±1, recompute first/last day
```

## Quick Reference: Stats Logic

```
Backend (getPeriodSummary):
  nonTransfers = account records (POSTPONED excluded)
  income      = sum of 收入 records
  expense     = sum of 支出 records  (= newExpense)
  incomingTx  = transfer records WHERE target = account (POSTPONED excluded)
  paidAmount  = sum of incomingTx amounts
  remainingDebt = max(0, expense - paidAmount)
  confirmedCount = count of CONFIRMED records

Frontend (credit card only):
  previousDebt = prevPeriodSummary.remainingDebt  (extra API call)
  billAmount   = previousDebt + summary.newExpense
  仍需还款     = max(0, billAmount - summary.paidAmount)
```
