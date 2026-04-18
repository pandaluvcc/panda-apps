<template>
  <div class="detail-page">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">事件详情</span>
      <van-icon name="ellipsis" class="menu-btn" @click="showMoreMenu = true" />
    </div>

    <div v-if="event" class="content">
      <!-- 事件概要 -->
      <div class="summary-card">
        <div class="summary-title">{{ event.name }}</div>
        <div class="summary-amount" :class="amountClass(event.recordType)">
          {{ amountSign(event.recordType) }}￥{{ fmtAmount(event.amount) }}
        </div>
        <div class="summary-meta">
          <div><span>账户：</span>{{ event.account }}{{ event.targetAccount ? ` → ${event.targetAccount}` : '' }}</div>
          <div><span>周期：</span>{{ intervalLabel }}</div>
          <div><span>起始：</span>{{ event.startDate }}</div>
          <div><span>期限：</span>{{ event.totalPeriods ? `${event.totalPeriods} 期` : '无限期' }}</div>
          <div v-if="event.status === 'ENDED'"><span>结束于：</span>{{ fmtDate(event.endedAt) }}</div>
        </div>
      </div>

      <!-- 期数概览 -->
      <div class="stats-row">
        <div class="stat">
          <div class="stat-label">总期数</div>
          <div class="stat-value">{{ event.totalCount }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">已发生</div>
          <div class="stat-value">{{ event.elapsedCount }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">剩余</div>
          <div class="stat-value">{{ event.remainingCount }}</div>
        </div>
      </div>

      <!-- 期数列表 -->
      <div class="records">
        <div class="records-title">期数明细</div>
        <div
          v-for="r in sortedRecords"
          :key="r.id"
          class="record-item"
          @touchstart="onTouchStart(r, $event)"
          @touchend="onTouchEnd"
          @contextmenu.prevent="onLongPress(r)"
        >
          <div class="record-left">
            <div class="record-period">#{{ r.periodNumber || '-' }}</div>
            <div class="record-date">{{ r.date }}</div>
          </div>
          <div class="record-right">
            <div class="record-amount" :class="amountClass(r.recordType)">
              {{ amountSign(r.recordType) }}￥{{ fmtAmount(r.amount) }}
            </div>
            <div class="record-status">{{ recordStatusLabel(r) }}</div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="loading">加载中...</div>

    <!-- 事件操作菜单 -->
    <van-action-sheet
      v-model:show="showMoreMenu"
      :actions="eventActions"
      cancel-text="取消"
      close-on-click-action
      @select="onEventAction"
    />

    <!-- 长按 record 菜单 -->
    <RecordActionSheet
      v-model:visible="showRecordAction"
      @edit="onRecordEdit"
      @delete="onRecordDelete"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  getRecurringEvent,
  endRecurringEvent,
  deleteRecurringEvent,
  updateEntireRecurringEvent,
  updateFromPeriod
} from '@/api/snapledger/recurringEvent'
import { deleteRecord } from '@/api'
import RecordActionSheet from '@/components/snapledger/RecordActionSheet.vue'

const route = useRoute()
const router = useRouter()

const event = ref(null)
const showMoreMenu = ref(false)
const showRecordAction = ref(false)
const selectedRecord = ref(null)

const eventActions = computed(() => {
  const actions = []
  if (event.value?.status === 'ACTIVE') {
    actions.push({ name: '结束事件', color: '#333' })
  }
  actions.push({ name: '删除事件', color: '#f56c6c' })
  return actions
})

const EXPENSE_TYPES = ['支出', '手续费', '利息']

function amountClass(type) {
  return EXPENSE_TYPES.includes(type) ? 'amount-expense' : 'amount-income'
}
function amountSign(type) {
  if (EXPENSE_TYPES.includes(type)) return '-'
  if (type === '收入') return '+'
  return ''
}
function fmtAmount(v) {
  return (Math.abs(Number(v) || 0)).toFixed(2)
}
function fmtDate(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}

const intervalLabel = computed(() => {
  if (!event.value) return ''
  const map = { DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月', YEARLY: '每年' }
  const base = map[event.value.intervalType] || event.value.intervalType
  if (event.value.intervalType === 'MONTHLY' && event.value.dayOfMonth) {
    return `${base} ${event.value.dayOfMonth}号`
  }
  return base
})

const sortedRecords = computed(() => {
  if (!event.value?.records) return []
  return [...event.value.records].sort((a, b) => {
    if (a.date < b.date) return 1
    if (a.date > b.date) return -1
    return (b.periodNumber || 0) - (a.periodNumber || 0)
  })
})

function recordStatusLabel(r) {
  const today = new Date().toISOString().slice(0, 10)
  if (r.date > today) return '未到期'
  return ''
}

// 长按检测：移动端 touchstart 600ms，PC 用 contextmenu
let pressTimer = null
function onTouchStart(r, evt) {
  selectedRecord.value = r
  pressTimer = setTimeout(() => {
    showRecordAction.value = true
  }, 600)
}
function onTouchEnd() {
  if (pressTimer) {
    clearTimeout(pressTimer)
    pressTimer = null
  }
}
function onLongPress(r) {
  selectedRecord.value = r
  showRecordAction.value = true
}

async function onEventAction(action) {
  if (action.name === '结束事件') {
    try {
      await showConfirmDialog({
        title: '结束事件',
        message: '结束后将删除所有未来期记录，历史记录保留。'
      })
      await endRecurringEvent(event.value.id)
      showToast('已结束')
      router.back()
    } catch (e) {
      if (e !== 'cancel') showToast('操作失败')
    }
  } else if (action.name === '删除事件') {
    try {
      await showConfirmDialog({
        title: '删除事件',
        message: '将删除事件本身和所有未来期记录，历史记录解绑保留。'
      })
      await deleteRecurringEvent(event.value.id)
      showToast('已删除')
      router.back()
    } catch (e) {
      if (e !== 'cancel') showToast('操作失败')
    }
  }
}

async function onRecordEdit(mode) {
  const r = selectedRecord.value
  if (!r) return
  if (mode === 'single') {
    router.push(`/snap/edit/${r.id}`)
    return
  }
  // entire / future：弹出输入框修改金额（MVP 支持金额修改）
  const label = mode === 'entire' ? '修改整个周期事件金额' : `从第 ${r.periodNumber} 期起修改金额`
  try {
    const value = await promptAmount(label, event.value.amount)
    if (value == null) return
    const payload = buildUpdatePayload(event.value, { amount: value })
    if (mode === 'entire') {
      await updateEntireRecurringEvent(event.value.id, payload)
    } else {
      await updateFromPeriod(event.value.id, r.periodNumber, payload)
    }
    showToast('已更新')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
      showToast('更新失败')
    }
  }
}

async function promptAmount(title, current) {
  const input = window.prompt(`${title}\n当前金额：${current}\n请输入新的金额：`, current)
  if (input == null) return null
  const n = Number(input)
  if (!Number.isFinite(n) || n < 0) {
    showToast('金额无效')
    return null
  }
  return n
}

function buildUpdatePayload(e, overrides = {}) {
  return {
    name: e.name,
    recordType: e.recordType,
    amount: overrides.amount != null ? overrides.amount : e.amount,
    mainCategory: e.mainCategory,
    subCategory: e.subCategory,
    account: e.account,
    targetAccount: e.targetAccount,
    intervalType: e.intervalType,
    intervalValue: e.intervalValue,
    dayOfMonth: e.dayOfMonth,
    dayOfWeek: e.dayOfWeek,
    startDate: e.startDate,
    totalPeriods: e.totalPeriods,
    note: e.note
  }
}

async function onRecordDelete() {
  const r = selectedRecord.value
  if (!r) return
  try {
    await showConfirmDialog({
      title: '删除记录',
      message: `确定删除第 ${r.periodNumber} 期（${r.date}）？`
    })
    await deleteRecord(r.id)
    showToast('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') showToast('删除失败')
  }
}

async function load() {
  try {
    event.value = await getRecurringEvent(route.params.id)
  } catch (e) {
    console.error('Failed to load event:', e)
    showToast('加载失败')
  }
}

onMounted(load)
</script>

<style scoped>
.detail-page { min-height: 100vh; background: #f5f6f8; padding-bottom: 32px; }
.page-header {
  background: #fff; padding: 12px 16px; border-bottom: 1px solid #ebedf0;
  display: grid; grid-template-columns: 32px 1fr 32px; align-items: center;
}
.back-btn, .menu-btn { font-size: 20px; color: #333; cursor: pointer; }
.page-title { font-size: 17px; font-weight: 600; color: #1a1a1a; text-align: center; }
.loading { padding: 40px; text-align: center; color: #969799; }
.content { padding: 12px; }

.summary-card {
  background: #fff; border-radius: 12px; padding: 20px 16px; margin-bottom: 12px;
}
.summary-title { font-size: 18px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; }
.summary-amount { font-size: 26px; font-weight: 600; margin-bottom: 14px; }
.summary-meta { font-size: 13px; color: #666; line-height: 1.8; }
.summary-meta span { color: #999; }

.stats-row {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
  background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 12px;
}
.stat { text-align: center; }
.stat-label { font-size: 12px; color: #969799; margin-bottom: 4px; }
.stat-value { font-size: 20px; font-weight: 600; color: #1a1a1a; }

.records { background: #fff; border-radius: 12px; padding: 12px 16px; }
.records-title { font-size: 14px; color: #666; margin-bottom: 8px; }
.record-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 0; border-bottom: 1px solid #f2f3f5;
  user-select: none;
}
.record-item:last-child { border-bottom: none; }
.record-period { font-size: 14px; color: #1a1a1a; font-weight: 500; }
.record-date { font-size: 12px; color: #999; margin-top: 2px; }
.record-right { text-align: right; }
.record-amount { font-size: 15px; font-weight: 500; }
.record-status { font-size: 11px; color: #bbb; margin-top: 2px; }

.amount-expense { color: #f56c6c; }
.amount-income { color: #67c23a; }
</style>
