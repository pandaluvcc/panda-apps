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
      <!-- 交易明细 tab -->
      <template v-if="activeTab === '交易明细'">
        <!-- Period navigation -->
        <div class="period-nav">
          <button class="period-arrow" @click.stop="shiftPeriod(-1)">‹</button>
          <span class="period-label period-label--clickable" @click="openPeriodPicker">{{ periodLabel }}</span>
          <button class="period-arrow" @click.stop="shiftPeriod(1)">›</button>
        </div>

        <!-- Stats section -->
        <div v-if="summary" class="stats-card">
          <template v-if="account?.isCreditAccount">
            <div class="stat-row">
              <span class="stat-label">新增支出</span>
              <span class="stat-value">-￥{{ fmt(summary.newExpense) }}</span>
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
              <span class="stat-value">{{ (summary.confirmedCount ?? 0) }} / {{ totalRecordCount }}</span>
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
              <span class="stat-value">{{ (summary.confirmedCount ?? 0) }} / {{ totalRecordCount }}</span>
            </div>
          </template>
        </div>
        <div v-else-if="statsLoading" class="stats-card stats-loading">
          <van-loading color="#999" size="18" />
        </div>

        <!-- Repayment records -->
        <div class="section-card">
          <div class="section-header">
            <span class="section-title">还款记录（{{ transfers.length }}）</span>
            <button class="add-btn" @click="goAddRecord('转账')">+</button>
          </div>
          <div v-if="transfers.length === 0" class="empty-tip">本周期暂无还款记录</div>
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
                <div class="record-date">{{ fmtDate(tx.date, tx.time) }}</div>
                <div class="record-amount">￥{{ fmt(tx.amount) }}</div>
                <span class="tag tag-transfer">{{ tx.target === account?.name ? '还款' : '转出' }}</span>
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
                <div class="record-date">{{ fmtDate(tx.date, tx.time) }}</div>
                <div class="record-amount" :class="tx.recordType === '收入' ? 'amount-income' : ''">
                  {{ tx.recordType === '收入' ? '+' : '' }}￥{{ fmt(tx.amount) }}
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>

      <!-- 账户信息 tab -->
      <template v-else-if="activeTab === '账户信息'">
        <div class="info-form">
          <!-- 基本信息 -->
          <div class="form-section">
            <div class="form-item">
              <span class="form-label">名称</span>
              <input v-model="infoForm.name" class="form-input" placeholder="请输入账户名称" @blur="autoSave" />
            </div>
            <div class="form-item">
              <span class="form-label">主币种</span>
              <span class="form-value">{{ infoForm.mainCurrency }}</span>
            </div>
            <div class="form-item form-item--picker" @click="showGroupPicker = true">
              <span class="form-label">账户分组</span>
              <div class="form-picker-value">
                <span>{{ infoForm.accountGroup || '未分组' }}</span>
                <span class="picker-arrow">›</span>
              </div>
            </div>
            <div class="form-item">
              <span class="form-label">初始余额</span>
              <input v-model.number="infoForm.initialBalance" class="form-input" type="number" placeholder="0" @blur="autoSave" />
            </div>
            <div class="form-item form-item--picker" @click="openBillCyclePicker">
              <span class="form-label">账单周期</span>
              <div class="form-picker-value">
                <span>{{ billCycleInfoDisplay }}</span>
                <span class="picker-arrow">›</span>
              </div>
            </div>
          </div>

          <!-- 信用账户及子字段 -->
          <div class="form-section">
            <div class="form-item form-item--switch">
              <span class="form-label">信用账户</span>
              <van-switch v-model="infoForm.isCreditAccount" size="22" @update:modelValue="autoSave" />
            </div>
            <template v-if="infoForm.isCreditAccount">
              <div class="form-item form-item--sub form-item--picker" @click="openCreditDueDatePicker">
                <span class="form-label">到期还款日</span>
                <div class="form-picker-value">
                  <span>{{ infoForm.creditDueDate ? `每月第${parseInt(infoForm.creditDueDate.split('-')[2], 10)}日` : '未设置' }}</span>
                  <span class="picker-arrow">›</span>
                </div>
              </div>
              <div class="form-item form-item--sub">
                <span class="form-label">信用额度</span>
                <input v-model.number="infoForm.creditLimit" class="form-input" type="number" placeholder="未设置" @blur="autoSave" />
              </div>
              <div class="form-item form-item--sub">
                <span class="form-label">额度共享</span>
                <input v-model="infoForm.creditLimitSharing" class="form-input" placeholder="无" @blur="autoSave" />
              </div>
              <div class="form-item form-item--sub">
                <span class="form-label">主账户</span>
                <input v-model="infoForm.masterAccountName" class="form-input" placeholder="无" @blur="autoSave" />
              </div>
              <div class="form-item form-item--sub">
                <span class="form-label">自动扣缴</span>
                <input v-model="infoForm.autoDebitAccount" class="form-input" placeholder="无" @blur="autoSave" />
              </div>
              <div class="form-item form-item--sub form-item--switch">
                <span class="form-label">账单折抵</span>
                <van-switch v-model="infoForm.billDiscount" size="22" @update:modelValue="autoSave" />
              </div>
              <div class="form-item form-item--sub form-item--switch">
                <span class="form-label">免息期推荐</span>
                <van-switch v-model="infoForm.interestFreeRecommend" size="22" @update:modelValue="autoSave" />
              </div>
              <div v-if="interestFreeDays !== null" class="form-item form-item--sub">
                <span class="form-label">目前免息天数</span>
                <span class="form-value">{{ interestFreeDays }}天</span>
              </div>
            </template>
          </div>

          <!-- 其他设置 -->
          <div class="form-section">
            <div class="form-item">
              <span class="form-label">返利回馈</span>
              <input v-model="infoForm.cashbackInfo" class="form-input" placeholder="无" @blur="autoSave" />
            </div>
            <div class="form-item form-item--switch">
              <span class="form-label">自动转存</span>
              <van-switch v-model="infoForm.autoRollover" size="22" @update:modelValue="autoSave" />
            </div>
            <div class="form-item form-item--switch">
              <span class="form-label">纳入总余额</span>
              <van-switch v-model="infoForm.includeInTotal" size="22" @update:modelValue="autoSave" />
            </div>
          </div>

          <!-- 备注 -->
          <div class="form-section">
            <div class="form-item">
              <span class="form-label">备注</span>
              <input v-model="infoForm.remark" class="form-input" placeholder="可选" @blur="autoSave" />
            </div>
          </div>

          <div v-if="autoSaving" class="autosave-tip">保存中...</div>
        </div>
      </template>
    </div>
  </div>

  <!-- 账单周期跳转选择器 -->
  <van-popup v-model:show="showPeriodPicker" position="bottom" round>
    <van-picker
      v-model="periodPickerValue"
      :columns="periodPickerColumns"
      title="选择账单周期"
      @confirm="onPeriodPickerConfirm"
      @cancel="showPeriodPicker = false"
    />
  </van-popup>

  <!-- 账单周期选择器（居中滑块） -->
  <van-popup v-model:show="showBillCyclePicker" position="center" :style="{ width: '85%', borderRadius: '16px' }">
    <div class="slide-picker">
      <div class="slide-picker-title">账单周期</div>
      <div class="slide-picker-preview">{{ previewBillCycleRange }}</div>
      <div class="slide-picker-track">
        <input
          type="range" min="1" max="28"
          v-model.number="tempBillCycleDay"
          class="cycle-slider"
          :style="sliderFillStyle"
        />
      </div>
      <div class="slide-picker-label">账单日：每月第 {{ tempBillCycleDay }} 日</div>
      <div class="slide-picker-footer">
        <button class="slide-btn" @click="showBillCyclePicker = false">取消</button>
        <button class="slide-btn" @click="confirmBillCycleDay">确定</button>
      </div>
    </div>
  </van-popup>

  <!-- 到期还款日选择器（每月第几日） -->
  <van-popup v-model:show="showCreditDueDatePicker" position="bottom" round>
    <van-picker
      v-model="creditDueDatePickerVal"
      :columns="creditDueDayColumns"
      title="到期还款日"
      @confirm="onCreditDueDateConfirm"
      @cancel="showCreditDueDatePicker = false"
    />
  </van-popup>

  <!-- 账户分组选择器 -->
  <AccountGroupPicker
    v-model:show="showGroupPicker"
    :modelValue="infoForm.accountGroup"
    @update:modelValue="val => { infoForm.accountGroup = val; autoSave() }"
  />
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAccountForm } from '@/composables/useAccountForm'
import { getAccount, updateAccount, getAccountSummary, getAccountTransactions } from '@/api'
import AccountGroupPicker from '@/components/snapledger/AccountGroupPicker.vue'

const route = useRoute()
const router = useRouter()

// Account data
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

// Parse day-of-month from ISO string without UTC timezone shift (new Date("2024-01-15") → UTC midnight)
function parseCycleDay(dateStr) {
  return parseInt(dateStr.split('-')[2], 10)
}

// Build billing cycle start/end dates, clamping cycleDay to the last valid day if the
// target month is shorter (e.g., cycleDay=31 in February → Feb 28/29)
function cyclePeriodDates(year, month, cycleDay) {
  const daysInStart = new Date(year, month + 1, 0).getDate()
  const start = new Date(year, month, Math.min(cycleDay, daysInStart))
  const endRaw = cycleDay - 1
  const end = endRaw <= 0
    ? new Date(year, month + 1, 0)   // cycleDay=1 → last day of this month
    : new Date(year, month + 1, Math.min(endRaw, new Date(year, month + 2, 0).getDate()))
  return { start: formatDate(start), end: formatDate(end) }
}

function computeDefaultPeriod(acc) {
  const today = new Date()
  if (acc.isCreditAccount && acc.billCycleStart) {
    const cycleDay = parseCycleDay(acc.billCycleStart)
    let y = today.getFullYear()
    let m = today.getMonth()
    if (today.getDate() < cycleDay) {
      m -= 1
      if (m < 0) { m = 11; y -= 1 }
    }
    return cyclePeriodDates(y, m, cycleDay)
  } else {
    const start = new Date(today.getFullYear(), today.getMonth(), 1)
    const end = new Date(today.getFullYear(), today.getMonth() + 1, 0)
    return { start: formatDate(start), end: formatDate(end) }
  }
}

function shiftPeriod(dir) {
  if (account.value?.isCreditAccount && account.value?.billCycleStart) {
    const cycleDay = parseCycleDay(account.value.billCycleStart)
    const s = new Date(periodStart.value)
    let newYear = s.getFullYear()
    let newMonth = s.getMonth() + dir
    if (newMonth < 0) { newMonth += 12; newYear -= 1 }
    if (newMonth > 11) { newMonth -= 12; newYear += 1 }
    const { start, end } = cyclePeriodDates(newYear, newMonth, cycleDay)
    periodStart.value = start
    periodEnd.value = end
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
  return `${periodStart.value.replace(/-/g, '/')} - ${periodEnd.value.replace(/-/g, '/')}`
})

// Stats
const summary = ref(null)
const prevSummary = ref(null)
const statsLoading = ref(false)

function fmt(val) {
  if (val == null) return '0.00'
  return Number(val).toFixed(2)
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
function fmtDate(dateStr, timeStr) {
  if (!dateStr) return ''
  const parts = String(dateStr).split('-')
  if (parts.length < 3) return dateStr
  const [y, m, d] = parts.map(Number)
  const week = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  const ds = `${y}/${String(m).padStart(2, '0')}/${String(d).padStart(2, '0')}`
  const ts = timeStr ? ' ' + String(timeStr).slice(0, 5) : ''
  return `${ds} ${week}${ts}`
}

function getPrevPeriodDates() {
  if (account.value?.isCreditAccount && account.value?.billCycleStart) {
    const cycleDay = parseCycleDay(account.value.billCycleStart)
    const s = new Date(periodStart.value)
    let prevYear = s.getFullYear()
    let prevMonth = s.getMonth() - 1
    if (prevMonth < 0) { prevMonth = 11; prevYear -= 1 }
    return cyclePeriodDates(prevYear, prevMonth, cycleDay)
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
  summary.value = null
  prevSummary.value = null
  statsLoading.value = true
  try {
    const id = route.params.id
    summary.value = await getAccountSummary(id, periodStart.value, periodEnd.value)
    if (account.value.isCreditAccount) {
      const prev = getPrevPeriodDates()
      prevSummary.value = await getAccountSummary(id, prev.start, prev.end)
    }
  } catch {
    showToast('统计数据加载失败')
  } finally {
    statsLoading.value = false
  }
}

// Transactions
const transfers = ref([])
const nonTransfers = ref([])
const sortDesc = ref(true)

const sortedNonTransfers = computed(() => {
  const list = [...nonTransfers.value]
  if (!sortDesc.value) list.reverse()
  return list
})

// 转账/还款类型：手动录入的 '转账'/'还款' + Moze 导入的 '转出'/'转入'
const TRANSFER_TYPES = new Set(['转账', '还款', '转出', '转入'])

async function loadTransactions() {
  if (!account.value || !periodStart.value) return
  try {
    const all = await getAccountTransactions(route.params.id, periodStart.value, periodEnd.value)
    transfers.value = all.filter(r => TRANSFER_TYPES.has(r.recordType))
    nonTransfers.value = all.filter(r => !TRANSFER_TYPES.has(r.recordType))
  } catch {
    showToast('记录加载失败')
  }
}

const totalRecordCount = computed(() => transfers.value.length + nonTransfers.value.length)

function goAddRecord(type) {
  router.push({ path: '/snap/add', query: { accountId: route.params.id, type } })
}

// Reload on period change
watch([periodStart, periodEnd], () => {
  loadStats()
  loadTransactions()
})

// 账单周期选择器（点击日期区间弹出）
const showPeriodPicker = ref(false)
const periodPickerValue = ref([])

const periodPickerColumns = computed(() => {
  if (!account.value || !periodStart.value) return []
  const periods = []
  const isCreditWithCycle = account.value.isCreditAccount && account.value.billCycleStart
  const cycleDay = isCreditWithCycle ? parseCycleDay(account.value.billCycleStart) : null
  const base = new Date(periodStart.value)

  // 生成从 +11 到 -24 月的区间（newest first）
  for (let offset = 11; offset >= -24; offset--) {
    let start, end
    if (isCreditWithCycle) {
      let y = base.getFullYear()
      let m = base.getMonth() + offset
      while (m < 0) { m += 12; y -= 1 }
      while (m > 11) { m -= 12; y += 1 }
      const d = cyclePeriodDates(y, m, cycleDay)
      start = d.start; end = d.end
    } else {
      const d = new Date(base.getFullYear(), base.getMonth() + offset, 1)
      start = formatDate(new Date(d.getFullYear(), d.getMonth(), 1))
      end = formatDate(new Date(d.getFullYear(), d.getMonth() + 1, 0))
    }
    periods.push({
      text: `${start.replace(/-/g, '/')} - ${end.replace(/-/g, '/')}`,
      value: `${start}|${end}`
    })
  }
  return periods
})

function openPeriodPicker() {
  periodPickerValue.value = [`${periodStart.value}|${periodEnd.value}`]
  showPeriodPicker.value = true
}

function onPeriodPickerConfirm({ selectedValues }) {
  const [start, end] = selectedValues[0].split('|')
  periodStart.value = start
  periodEnd.value = end
  showPeriodPicker.value = false
}

// Account info form
const { form: infoForm, loadFromAccount, toPayload, validate } = useAccountForm()
const autoSaving = ref(false)

// 账户分组选择器
const showGroupPicker = ref(false)

// 账单周期选择器（滑块）
const showBillCyclePicker = ref(false)
const tempBillCycleDay = ref(1)

// 到期还款日选择器（每月第几日）
const showCreditDueDatePicker = ref(false)
const creditDueDatePickerVal = ref(['20'])

const creditDueDayColumns = Array.from({ length: 28 }, (_, i) => ({
  text: `每月第${i + 1}日`,
  value: String(i + 1).padStart(2, '0')
}))

function openCreditDueDatePicker() {
  if (infoForm.creditDueDate) {
    creditDueDatePickerVal.value = [infoForm.creditDueDate.split('-')[2]]
  } else {
    creditDueDatePickerVal.value = ['20']
  }
  showCreditDueDatePicker.value = true
}

function onCreditDueDateConfirm({ selectedValues }) {
  const day = parseInt(selectedValues[0], 10)
  const now = new Date()
  const pad = d => String(d).padStart(2, '0')
  infoForm.creditDueDate = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(day)}`
  showCreditDueDatePicker.value = false
  autoSave()
}

// 目前免息天数：今天到本月（或下月）到期还款日的剩余天数
const interestFreeDays = computed(() => {
  if (!infoForm.creditDueDate) return null
  const dueDay = parseInt(infoForm.creditDueDate.split('-')[2], 10)
  const today = new Date()
  const todayDay = today.getDate()
  const y = today.getFullYear()
  const m = today.getMonth()
  const dueDate = todayDay <= dueDay
    ? new Date(y, m, dueDay)
    : new Date(y, m + 1, dueDay)
  const todayMidnight = new Date(y, m, todayDay)
  return Math.max(0, Math.round((dueDate - todayMidnight) / 86400000))
})

// 从 billCycleStart 字符串或 Date 中提取日（天）
function getCycleDayFromForm() {
  const raw = infoForm.billCycleStart
  if (!raw) return null
  const str = raw instanceof Date ? raw.toISOString().slice(0, 10) : raw
  return parseInt(str.split('-')[2], 10)
}

const billCycleInfoDisplay = computed(() => {
  const day = getCycleDayFromForm()
  if (!day) return '未设置'
  const endDay = day <= 1 ? 28 : day - 1
  return `每月${day}日 — 次月${endDay}日`
})

// 账单周期预览（随滑块实时更新）
const previewBillCycleRange = computed(() => {
  const day = tempBillCycleDay.value
  const today = new Date()
  let y = today.getFullYear()
  let m = today.getMonth()
  if (today.getDate() < day) {
    m -= 1
    if (m < 0) { m = 11; y -= 1 }
  }
  const { start, end } = cyclePeriodDates(y, m, day)
  return `${start.replace(/-/g, '/')} — ${end.replace(/-/g, '/')}`
})

const sliderFillStyle = computed(() => {
  const pct = ((tempBillCycleDay.value - 1) / 27 * 100).toFixed(1)
  return { background: `linear-gradient(to right, #1989fa ${pct}%, #e5e5e5 ${pct}%)` }
})

function openBillCyclePicker() {
  tempBillCycleDay.value = getCycleDayFromForm() || 1
  showBillCyclePicker.value = true
}

function confirmBillCycleDay() {
  const day = tempBillCycleDay.value
  const now = new Date()
  const pad = d => String(d).padStart(2, '0')
  infoForm.billCycleStart = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(day)}`
  const endDay = day <= 1 ? 28 : day - 1
  let endMonth = now.getMonth() + 2
  let endYear = now.getFullYear()
  if (endMonth > 12) { endMonth = 1; endYear += 1 }
  infoForm.billCycleEnd = `${endYear}-${pad(endMonth)}-${pad(endDay)}`
  showBillCyclePicker.value = false
  autoSave()
}

watch(account, (acc) => {
  if (acc) loadFromAccount(acc)
}, { immediate: true })

async function autoSave() {
  const err = validate()
  if (err) { showToast(err); return }
  autoSaving.value = true
  try {
    const updated = await updateAccount(route.params.id, toPayload())
    account.value = updated
    loadFromAccount(updated)
    // Re-compute period in case isCreditAccount or billCycleStart changed
    const { start, end } = computeDefaultPeriod(updated)
    periodStart.value = start
    periodEnd.value = end
  } catch (e) {
    showToast('保存失败')
  } finally {
    autoSaving.value = false
  }
}

function handleBack() {
  router.back()
}

onMounted(async () => {
  try {
    account.value = await getAccount(route.params.id)
    const { start, end } = computeDefaultPeriod(account.value)
    periodStart.value = start
    periodEnd.value = end
    await Promise.all([loadStats(), loadTransactions()])
  } catch {
    showToast('加载失败，请返回重试')
  }
})
</script>

<style scoped>
.account-detail {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  z-index: 10;
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
  position: sticky;
  top: 64px;
  z-index: 10;
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
.period-label--clickable { cursor: pointer; color: #1989fa; }
.stats-card {
  background: #fff; margin: 0 12px 12px; border-radius: 12px; padding: 4px 0;
}
.stats-loading { padding: 16px; display: flex; justify-content: center; }
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
.record-right { text-align: right; flex-shrink: 0; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.record-date { font-size: 11px; color: #bbb; }
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
/* Account info form */
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
.form-item--picker { cursor: pointer; }
.form-picker-value {
  display: flex; align-items: center; gap: 4px;
  font-size: 14px; color: #666;
}
.picker-arrow { color: #bbb; font-size: 16px; }
.form-item--sub { padding-left: 32px; background: #fafafa; }
.autosave-tip {
  text-align: center; font-size: 12px; color: #999; padding: 8px 0 16px;
}
/* 账单周期滑块选择器 */
.slide-picker { padding: 24px 24px 20px; }
.slide-picker-title { text-align: center; font-size: 17px; font-weight: 600; color: #1a1a1a; margin-bottom: 20px; }
.slide-picker-preview { text-align: center; font-size: 16px; font-weight: 500; color: #1a1a1a; margin-bottom: 14px; }
.slide-picker-track { padding: 0 4px; margin-bottom: 10px; }
.cycle-slider {
  width: 100%; height: 4px; -webkit-appearance: none; appearance: none;
  border-radius: 2px; outline: none; cursor: pointer;
}
.cycle-slider::-webkit-slider-thumb {
  -webkit-appearance: none; width: 26px; height: 26px;
  border-radius: 50%; background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.25); cursor: pointer;
}
.cycle-slider::-moz-range-thumb {
  width: 26px; height: 26px; border-radius: 50%; background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.25); border: none; cursor: pointer;
}
.slide-picker-label { text-align: center; font-size: 14px; color: #888; margin-bottom: 24px; }
.slide-picker-footer { display: flex; gap: 12px; }
.slide-btn {
  flex: 1; height: 48px; border-radius: 10px; font-size: 16px;
  border: none; cursor: pointer; background: #f2f2f2; color: #333;
}
</style>
