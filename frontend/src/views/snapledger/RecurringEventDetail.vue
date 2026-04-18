<template>
  <div class="overlay" @click.self="$router.back()">
    <div v-if="event" class="event-card" @click.stop>
      <!-- 事件 header（固定） -->
      <div class="card-header">
        <div class="header-icon" :style="{ backgroundColor: iconColor }">
          <van-icon :name="iconName" color="#fff" size="26" />
        </div>
        <div class="header-main">
          <div class="header-top">
            <div class="name">{{ event.name }}</div>
            <div class="amount" :class="amountClass(event.recordType)">
              ￥{{ fmtAmount(totalAmount) }}
            </div>
          </div>
          <div class="header-sub">{{ subtitleText }}</div>
        </div>
      </div>
      <div class="divider"></div>

      <!-- 期数列表（内部滚动） -->
      <div class="periods-scroll" ref="scrollBox">
        <div
          v-for="r in sortedRecords"
          :key="r.id"
          :ref="el => registerRow(r, el)"
          class="period-row"
          :class="{ future: isFuture(r) }"
          @click="openRecord(r)"
        >
          <div class="period-chip" :class="{ future: isFuture(r) }">
            {{ r.periodNumber || '-' }}
          </div>
          <div class="period-date">{{ fmtPeriodDate(r.date) }}</div>
          <div class="period-amount" :class="amountClass(r.recordType)">
            ￥{{ fmtAmount(r.amount) }}
          </div>
        </div>
      </div>
    </div>
    <div v-else class="loading">加载中...</div>

    <!-- 点击 record 弹卡片 -->
    <RecordActionSheet
      v-model:visible="showRecordAction"
      :record="selectedRecord"
      :event-info="eventInfoText"
      @edit="onRecordEdit"
      @delete="onRecordDelete"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  getRecurringEvent,
  updateEntireRecurringEvent,
  updateFromPeriod
} from '@/api/snapledger/recurringEvent'
import { deleteRecord } from '@/api'
import RecordActionSheet from '@/components/snapledger/RecordActionSheet.vue'

const route = useRoute()
const router = useRouter()

const event = ref(null)
const showRecordAction = ref(false)
const selectedRecord = ref(null)
const scrollBox = ref(null)

const TRANSFER_TYPES = ['转账', '还款', '转出', '转入', '应付款项', '应收款项', '分期还款']

function amountClass(type) {
  return TRANSFER_TYPES.includes(type) ? 'amount-red' : 'amount-green'
}
function fmtAmount(v) {
  return (Math.abs(Number(v) || 0)).toFixed(2)
}
function fmtPeriodDate(v) {
  if (!v) return ''
  return String(v).replaceAll('-', '/')
}
function isFuture(r) {
  return r.date > new Date().toISOString().slice(0, 10)
}

const iconName = computed(() => {
  if (!event.value) return 'home-o'
  if (TRANSFER_TYPES.includes(event.value.recordType)) return 'exchange'
  if (event.value.recordType === '收入') return 'cash-o'
  return 'home-o'
})
const iconColor = computed(() => {
  if (!event.value) return '#999'
  if (TRANSFER_TYPES.includes(event.value.recordType)) return '#D8944B'
  if (event.value.recordType === '收入') return '#67c23a'
  return '#C97789'
})

const intervalLabel = computed(() => {
  if (!event.value) return ''
  const map = { DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月', YEARLY: '每年' }
  return map[event.value.intervalType] || event.value.intervalType
})

const subtitleText = computed(() => {
  if (!event.value) return ''
  const parts = []
  parts.push(event.value.totalPeriods ? `${event.value.totalPeriods}期` : '无限期')
  parts.push(intervalLabel.value)
  if (event.value.intervalType === 'MONTHLY' && event.value.dayOfMonth) {
    parts.push(`${event.value.dayOfMonth}号`)
  }
  return parts.join(' · ')
})

/** 总金额 = 当前所有期记录金额之和（已按 period 去重）。 */
const totalAmount = computed(() => {
  if (!event.value?.records) return 0
  return event.value.records.reduce((sum, r) => sum + (Number(r.amount) || 0), 0)
})

const sortedRecords = computed(() => {
  if (!event.value?.records) return []
  return [...event.value.records].sort((a, b) => {
    if (a.date < b.date) return -1
    if (a.date > b.date) return 1
    return (a.periodNumber || 0) - (b.periodNumber || 0)
  })
})

const eventInfoText = computed(() => {
  if (!event.value) return ''
  return `${event.value.totalPeriods ? event.value.totalPeriods + '期' : '无限期'}（${intervalLabel.value}）`
})

function openRecord(r) {
  selectedRecord.value = r
  showRecordAction.value = true
}

// 保存每行 DOM 引用，打开后滚动到最新已发生期
const rowRefs = new Map()
function registerRow(r, el) {
  if (el) rowRefs.set(r.id, el)
  else rowRefs.delete(r.id)
}

function scrollToLatestElapsed() {
  const records = sortedRecords.value
  if (records.length === 0) return
  const today = new Date().toISOString().slice(0, 10)
  let target = null
  for (const r of records) {
    if (r.date <= today) target = r
    else break
  }
  if (!target) target = records[0]
  const el = rowRefs.get(target.id)
  const box = scrollBox.value
  if (!el || !box) return
  // 滚动内部容器让目标行居中
  const top = el.offsetTop - (box.clientHeight / 2) + (el.clientHeight / 2)
  box.scrollTop = Math.max(0, top)
}

async function onRecordEdit(mode) {
  const r = selectedRecord.value
  if (!r) return
  if (mode === 'single') {
    router.push(`/snap/edit/${r.id}`)
    return
  }
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
    await nextTick()
    scrollToLatestElapsed()
  } catch (e) {
    console.error('Failed to load event:', e)
    showToast('加载失败')
  }
}

onMounted(load)
</script>

<style scoped>
.overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center;
  padding: 24px 12px;
  box-sizing: border-box;
  z-index: 100;
}

.loading { color: #fff; font-size: 14px; }

.event-card {
  background: #fff; border-radius: 18px;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.15);
  width: 100%; max-width: 440px;
  max-height: 82vh;
  display: flex; flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex; align-items: center; gap: 14px; padding: 18px 18px 16px;
  flex-shrink: 0;
}
.divider { flex-shrink: 0; }
.periods-scroll {
  flex: 1; overflow-y: auto; overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}
.header-icon {
  width: 54px; height: 54px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.header-main { flex: 1; min-width: 0; }
.header-top {
  display: flex; align-items: baseline; justify-content: space-between; gap: 8px;
}
.name {
  font-size: 20px; font-weight: 600; color: #1a1a1a;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.amount { font-size: 20px; font-weight: 600; flex-shrink: 0; }
.header-sub { font-size: 13px; color: #aaa; margin-top: 4px; }

.divider { height: 1px; background: #f2f3f5; margin: 0 18px; }

.period-row {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 18px;
  cursor: pointer; user-select: none;
}
.period-row.future { opacity: 0.4; }

.period-chip {
  min-width: 36px; padding: 4px 10px; border-radius: 14px;
  background: #4aa9ff; color: #fff; font-size: 14px; font-weight: 500;
  text-align: center; flex-shrink: 0;
}
.period-chip.future { background: #bde0ff; }

.period-date {
  flex: 1; font-size: 18px; color: #222; letter-spacing: 0.5px;
}
.period-amount { font-size: 17px; font-weight: 500; flex-shrink: 0; }

.amount-red { color: #f56c6c; }
.amount-green { color: #67c23a; }
</style>
