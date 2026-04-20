<template>
  <div class="installment-page">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">分期事件</span>
      <span class="header-right"></span>
    </div>

    <div class="tabs">
      <div class="tab" :class="{ active: activeStatus === 'ACTIVE' }" @click="activeStatus = 'ACTIVE'">
        进行中 ({{ activeList.length }})
      </div>
      <div class="tab" :class="{ active: activeStatus === 'ENDED' }" @click="activeStatus = 'ENDED'">
        已结束 ({{ endedList.length }})
      </div>
    </div>

    <div class="list" v-if="currentList.length > 0">
      <div v-for="e in currentList" :key="e.id" class="card" @click="openDetail(e.id)">
        <div class="card-icon" :style="{ backgroundColor: iconColor(e) }">
          <van-icon :name="iconName(e)" color="#fff" size="22" />
        </div>
        <div class="card-main">
          <div class="card-title">{{ displayName(e) }}</div>
          <div class="card-sub">{{ subtitle(e) }}</div>
        </div>
        <div class="card-right">
          <div class="card-amount">￥{{ fmt(e.perPeriodAmount) }}</div>
          <div class="card-date">{{ fmtDate(e.lastDate) }}</div>
        </div>
      </div>
    </div>
    <div v-else class="empty">
      <van-icon name="balance-list-o" class="empty-icon" />
      <p class="empty-title">
        {{ activeStatus === 'ACTIVE' ? '目前没有分期事件' : '暂无已结束的分期事件' }}
      </p>
    </div>

    <!-- 详情弹出卡片 -->
    <transition name="detail-fade">
      <div v-if="showDetail" class="detail-overlay" @click.self="closeDetail">
        <div class="detail-card" v-if="detail">
          <div class="detail-head">
            <div class="detail-icon" :style="{ backgroundColor: iconColor(detail) }">
              <van-icon :name="iconName(detail)" color="#fff" size="22" />
            </div>
            <div class="detail-head-main">
              <div class="detail-title">{{ displayName(detail) }}</div>
              <div class="detail-sub">{{ headerSubtitle(detail) }}</div>
            </div>
            <div class="detail-total">￥{{ fmt(detail.totalAmount) }}</div>
          </div>

          <div class="totals">
            <div class="totals-row"><span class="k">本金总计</span><span class="v">￥{{ fmt(detail.principalTotal) }}</span></div>
            <div class="totals-row"><span class="k">已还本金</span><span class="v">￥{{ fmt(paidPrincipal) }}</span></div>
            <div class="totals-row"><span class="k">剩余本金</span><span class="v">￥{{ fmt(sub(detail.principalTotal, paidPrincipal)) }}</span></div>
            <div class="totals-gap"></div>
            <div class="totals-row"><span class="k">利息总计</span><span class="v">￥{{ fmt(detail.interestTotal) }}</span></div>
            <div class="totals-row"><span class="k">已还利息</span><span class="v">￥{{ fmt(paidInterest) }}</span></div>
            <div class="totals-row"><span class="k">剩余利息</span><span class="v">￥{{ fmt(sub(detail.interestTotal, paidInterest)) }}</span></div>
          </div>

          <div class="periods">
            <div v-for="p in detail.periods" :key="p.periodNumber" class="period-row">
              <div class="period-num">{{ p.periodNumber }}</div>
              <div class="period-main">
                <div class="period-date-line">
                  <span class="period-date">{{ fmtDate(p.date) }}</span>
                  <span v-if="detail.yearRate" class="rate-badge">{{ fmtRate(detail.yearRate) }}%</span>
                </div>
                <div class="period-sub">
                  本金 ￥{{ fmt(p.principal) }}
                  <template v-if="Number(p.interest) > 0"> · 利息 ￥{{ fmt(p.interest) }}</template>
                </div>
              </div>
              <div class="period-amount">￥{{ fmt(p.total) }}</div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listInstallmentEvents, getInstallmentEvent } from '@/api/snapledger/installmentEvent'

const router = useRouter()
const TAB_KEY = 'snap.installment.activeTab'
const activeStatus = ref(sessionStorage.getItem(TAB_KEY) || 'ACTIVE')
const activeList = ref([])
const endedList = ref([])
const showDetail = ref(false)
const detail = ref(null)

watch(activeStatus, v => sessionStorage.setItem(TAB_KEY, v))

const currentList = computed(() =>
  activeStatus.value === 'ACTIVE' ? activeList.value : endedList.value
)

// 已还本金/利息 = 所有期数（已结束）或 日期 ≤ 今日（进行中）之和
const today = new Date()
today.setHours(0, 0, 0, 0)
const paidPrincipal = computed(() => {
  if (!detail.value?.periods) return 0
  return detail.value.periods
    .filter(p => new Date(p.date) <= today)
    .reduce((s, p) => s + (Number(p.principal) || 0), 0)
})
const paidInterest = computed(() => {
  if (!detail.value?.periods) return 0
  return detail.value.periods
    .filter(p => new Date(p.date) <= today)
    .reduce((s, p) => s + (Number(p.interest) || 0), 0)
})

function iconName(e) {
  const sub = e.subCategory || ''
  if (e.name === '账单分期') return 'exchange'
  if (/(箱包|包)/.test(sub)) return 'bag-o'
  if (/(衣物|服装|鞋)/.test(sub)) return 'shop-collect-o'
  if (/(数码|电脑|手机)/.test(sub) || /手机|苹果/.test(e.name || '')) return 'desktop-o'
  return 'balance-pay'
}
function iconColor(e) {
  if (e.name === '账单分期') return '#D8944B'
  const sub = e.subCategory || ''
  if (/(衣物|服装|鞋)/.test(sub)) return '#6FB3D2'
  return '#C97789'
}
function fmt(v) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtRate(v) {
  const n = Number(v) || 0
  return n % 1 === 0 ? String(n) : n.toString()
}
function fmtDate(d) {
  if (!d) return ''
  return d.toString().replaceAll('-', '/')
}
function sub(a, b) { return (Number(a) || 0) - (Number(b) || 0) }
function displayName(e) { return e.name || '未命名分期' }

// 列表项副标题：#已还/总 · 【账户】 · 商家
function subtitle(e) {
  const parts = []
  const paid = e.totalPeriods
  parts.push(`#${paid} / ${e.totalPeriods}`)
  if (e.account) parts.push(`【${e.account}】`)
  if (e.merchant) parts.push(e.merchant)
  return parts.join(' · ')
}

// 详情 header 副标题：有年利率优先展示年利率，否则展示每期利息，最后退化到只显示期数
function headerSubtitle(e) {
  const periods = `分 ${e.totalPeriods} 期`
  if (e.yearRate && Number(e.yearRate) > 0) {
    return `${periods}，年利率 ${fmtRate(e.yearRate)}%`
  }
  if (e.interestTotal && Number(e.interestTotal) > 0 && e.totalPeriods) {
    const per = Number(e.interestTotal) / e.totalPeriods
    return `${periods}，每期利息 ￥${per.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }
  return periods
}

async function openDetail(id) {
  try {
    detail.value = await getInstallmentEvent(id)
    showDetail.value = true
  } catch (err) {
    console.error('Failed to load installment detail:', err)
  }
}
function closeDetail() {
  showDetail.value = false
  detail.value = null
}

async function loadLists() {
  try {
    const [a, e] = await Promise.all([
      listInstallmentEvents('ACTIVE'),
      listInstallmentEvents('ENDED')
    ])
    activeList.value = a || []
    endedList.value = e || []
  } catch (err) {
    console.error('Failed to load installment events:', err)
  }
}

onMounted(loadLists)
onActivated(loadLists)
</script>

<style scoped>
.installment-page { min-height: 100vh; background: #f5f6f8; }
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
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 120px 32px 0; text-align: center;
}
.empty-icon { font-size: 56px; color: #c8c9cc; margin-bottom: 16px; }
.empty-title { font-size: 14px; color: #969799; margin: 0; }

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
.card-title {
  font-size: 15px; font-weight: 500; color: #1a1a1a; margin-bottom: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.card-sub { font-size: 12px; color: #969799; line-height: 1.4; word-break: break-all; }
.card-right { text-align: right; flex-shrink: 0; }
.card-amount { font-size: 16px; font-weight: 500; color: #f56c6c; margin-bottom: 4px; }
.card-date { font-size: 11px; color: #bbb; }

/* ============ 详情弹出卡片 ============ */
.detail-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4);
  z-index: 500; display: flex; align-items: flex-start; justify-content: center;
  padding: 120px 10px 10px; box-sizing: border-box;
  overflow: hidden;
}
.detail-card {
  width: 100%; max-width: 500px; max-height: calc(100vh - 140px);
  background: #f5f6f8; border-radius: 16px; overflow: auto;
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.08);
}
.detail-head {
  background: #fff; padding: 18px 16px;
  display: flex; align-items: center; gap: 12px;
  border-radius: 16px 16px 0 0;
}
.detail-icon {
  width: 48px; height: 48px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.detail-head-main { flex: 1; min-width: 0; }
.detail-title { font-size: 17px; font-weight: 600; color: #1a1a1a; margin-bottom: 4px; }
.detail-sub { font-size: 12px; color: #969799; }
.detail-total { font-size: 20px; font-weight: 600; color: #f56c6c; flex-shrink: 0; }

.totals {
  background: #fff; margin: 0; padding: 4px 16px; border-top: 1px solid #f0f0f0;
}
.totals-row {
  display: flex; justify-content: space-between; font-size: 14px;
  padding: 10px 0;
}
.totals-row .k { color: #333; }
.totals-row .v { color: #1a1a1a; font-weight: 500; }
.totals-gap { height: 10px; }

.periods { background: #fff; margin-top: 1px; }
.period-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-top: 1px solid #f5f5f5;
}
.period-num {
  width: 26px; height: 20px; border-radius: 10px;
  background: #7fbce8; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 500; flex-shrink: 0;
}
.period-main { flex: 1; min-width: 0; }
.period-date-line { display: flex; align-items: center; gap: 8px; }
.period-date { font-size: 15px; color: #1a1a1a; font-weight: 500; }
.rate-badge {
  background: #D8944B; color: #fff;
  font-size: 11px; padding: 2px 8px; border-radius: 10px;
}
.period-sub { font-size: 11px; color: #969799; margin-top: 2px; }
.period-amount { font-size: 15px; color: #f56c6c; font-weight: 500; flex-shrink: 0; }

.detail-fade-enter-active, .detail-fade-leave-active { transition: opacity 0.2s ease; }
.detail-fade-enter-active .detail-card,
.detail-fade-leave-active .detail-card { transition: transform 0.25s ease; }
.detail-fade-enter-from, .detail-fade-leave-to { opacity: 0; }
.detail-fade-enter-from .detail-card,
.detail-fade-leave-to .detail-card { transform: translateY(20px); }
</style>
