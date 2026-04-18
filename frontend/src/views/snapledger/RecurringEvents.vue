<template>
  <div class="recurring-page">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">周期事件</span>
      <span class="header-right"></span>
    </div>

    <div class="tabs">
      <div
        class="tab"
        :class="{ active: activeStatus === 'ACTIVE' }"
        @click="activeStatus = 'ACTIVE'"
      >进行中 ({{ activeList.length }})</div>
      <div
        class="tab"
        :class="{ active: activeStatus === 'ENDED' }"
        @click="activeStatus = 'ENDED'"
      >已结束 ({{ endedList.length }})</div>
    </div>

    <div class="list">
      <div v-if="currentList.length === 0" class="empty">
        <van-icon name="calendar-o" class="empty-icon" />
        <p class="empty-title">{{ activeStatus === 'ACTIVE' ? '暂无进行中的周期事件' : '暂无已结束的周期事件' }}</p>
      </div>
      <div
        v-for="e in currentList"
        :key="e.id"
        class="card"
        @click="goDetail(e.id)"
      >
        <div class="card-icon" :style="{ backgroundColor: iconColor(e) }">
          <van-icon :name="iconName(e)" color="#fff" size="22" />
        </div>
        <div class="card-main">
          <div class="card-title">{{ e.name }}</div>
          <div class="card-sub">{{ subtitle(e) }}</div>
        </div>
        <div class="card-right">
          <div class="card-amount" :class="amountClass(e)">
            ￥{{ fmtAmount(e.amount) }}
          </div>
          <div class="card-next">{{ nextDueLabel(e) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { listRecurringEvents } from '@/api/snapledger/recurringEvent'

const router = useRouter()

const activeStatus = ref('ACTIVE')
const activeList = ref([])
const endedList = ref([])

const currentList = computed(() => {
  const raw = activeStatus.value === 'ACTIVE' ? activeList.value : endedList.value
  // 按下次执行日期升序；空值（已结束）排最后
  return [...raw].sort((a, b) => {
    if (!a.nextDueDate && !b.nextDueDate) return 0
    if (!a.nextDueDate) return 1
    if (!b.nextDueDate) return -1
    return a.nextDueDate < b.nextDueDate ? -1 : a.nextDueDate > b.nextDueDate ? 1 : 0
  })
})

const TRANSFER_TYPES = ['转账', '还款', '转出', '转入', '应付款项', '应收款项', '分期还款']

function iconName(e) {
  if (TRANSFER_TYPES.includes(e.recordType)) return 'exchange'
  if (e.recordType === '收入') return 'cash-o'
  return 'home-o'
}
function iconColor(e) {
  if (TRANSFER_TYPES.includes(e.recordType)) return '#D8944B'
  if (e.recordType === '收入') return '#67c23a'
  return '#C97789'
}
/** 周期事件配色：转账类红色，支出/收入类绿色，金额不加 +/- 符号。 */
function amountClass(e) {
  return TRANSFER_TYPES.includes(e.recordType) ? 'amount-red' : 'amount-green'
}
function fmtAmount(v) {
  const n = Number(v) || 0
  return Math.abs(n).toFixed(2)
}
function subtitle(e) {
  const parts = []
  if (TRANSFER_TYPES.includes(e.recordType) && e.account && e.targetAccount) {
    parts.push(`${e.account} → ${e.targetAccount}`)
  } else if (e.account) {
    parts.push(e.account)
  }
  parts.push(`#${e.nextPeriodNumber || '-'}`)
  parts.push(e.totalPeriods ? `${e.totalPeriods}期` : '无限期')
  if (e.mainCategory) parts.push(`【${e.mainCategory}】`)
  return parts.join(' · ')
}
function nextDueLabel(e) {
  if (!e.nextDueDate) return ''
  const d = new Date(e.nextDueDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diffDays = Math.round((d - today) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '明天'
  return e.nextDueDate
}

function goDetail(id) {
  router.push(`/snap/events/recurring/${id}`)
}

async function loadLists() {
  try {
    const [a, e] = await Promise.all([
      listRecurringEvents('ACTIVE'),
      listRecurringEvents('ENDED')
    ])
    activeList.value = a || []
    endedList.value = e || []
  } catch (err) {
    console.error('Failed to load recurring events:', err)
  }
}

onMounted(loadLists)
onActivated(loadLists)
</script>

<style scoped>
.recurring-page { min-height: 100vh; background: #f5f6f8; }
.page-header {
  background: #fff; padding: 12px 16px; border-bottom: 1px solid #ebedf0;
  display: grid; grid-template-columns: 32px 1fr 32px; align-items: center;
}
.back-btn { font-size: 20px; color: #333; cursor: pointer; }
.page-title { font-size: 17px; font-weight: 600; color: #1a1a1a; text-align: center; }
.tabs { display: flex; background: #fff; border-bottom: 1px solid #ebedf0; }
.tab {
  flex: 1; text-align: center; padding: 12px 0; font-size: 15px; color: #666;
  border-bottom: 2px solid transparent; cursor: pointer;
}
.tab.active { color: #4aa9ff; border-bottom-color: #4aa9ff; font-weight: 500; }
.list { padding: 12px; }
.empty {
  text-align: center; padding: 60px 20px; color: #969799;
  display: flex; flex-direction: column; align-items: center;
}
.empty-icon { font-size: 56px; color: #c8c9cc; margin-bottom: 16px; }
.empty-title { font-size: 14px; margin: 0; }
.card {
  background: #fff; border-radius: 12px; padding: 14px;
  display: flex; align-items: center; gap: 12px; margin-bottom: 10px;
  cursor: pointer;
}
.card-icon {
  width: 44px; height: 44px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.card-main { flex: 1; min-width: 0; }
.card-title { font-size: 15px; font-weight: 500; color: #1a1a1a; margin-bottom: 4px; }
.card-sub { font-size: 12px; color: #969799; line-height: 1.4; word-break: break-all; }
.card-right { text-align: right; flex-shrink: 0; }
.card-amount { font-size: 16px; font-weight: 500; margin-bottom: 4px; }
.card-next { font-size: 11px; color: #bbb; }
.amount-green { color: #67c23a; }
.amount-red { color: #f56c6c; }
</style>
