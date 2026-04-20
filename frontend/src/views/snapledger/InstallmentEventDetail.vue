<template>
  <div class="detail-page" v-if="event">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">分期详情</span>
      <span class="header-right"></span>
    </div>

    <div class="summary">
      <div class="summary-title">{{ event.name || '未命名分期' }}</div>
      <div class="summary-amount">￥{{ fmt(event.totalAmount) }}</div>
      <div class="summary-sub">总金额</div>
      <div class="summary-meta">
        <span>{{ event.totalPeriods }} 期</span>
        <span>末期 ￥{{ fmt(event.perPeriodAmount) }}</span>
        <span>{{ fmtDate(event.firstDate) }} - {{ fmtDate(event.lastDate) }}</span>
      </div>
      <div class="summary-meta">
        <span v-if="event.account">【{{ event.account }}】</span>
        <span v-if="event.merchant">{{ event.merchant }}</span>
        <span v-if="event.subCategory">{{ event.subCategory }}</span>
      </div>
    </div>

    <div class="totals">
      <div class="totals-row">
        <span class="k">本金总计</span><span class="v">￥{{ fmt(event.principalTotal) }}</span>
      </div>
      <div class="totals-row">
        <span class="k">已还本金</span><span class="v">￥{{ fmt(paidPrincipal) }}</span>
      </div>
      <div class="totals-row">
        <span class="k">剩余本金</span><span class="v">￥{{ fmt(sub(event.principalTotal, paidPrincipal)) }}</span>
      </div>
      <div class="totals-sep"></div>
      <div class="totals-row">
        <span class="k">利息总计</span><span class="v">￥{{ fmt(event.interestTotal) }}</span>
      </div>
      <div class="totals-row">
        <span class="k">已还利息</span><span class="v">￥{{ fmt(paidInterest) }}</span>
      </div>
      <div class="totals-row">
        <span class="k">剩余利息</span><span class="v">￥{{ fmt(sub(event.interestTotal, paidInterest)) }}</span>
      </div>
    </div>

    <div class="section">
      <div class="section-title">还款记录</div>
      <div v-for="p in event.periods" :key="p.periodNumber" class="row">
        <div class="row-left">
          <div class="row-period">{{ p.periodNumber }}</div>
          <div class="row-date">{{ fmtDate(p.date) }}</div>
        </div>
        <div class="row-amount">￥{{ fmt(p.total) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getInstallmentEvent } from '@/api/snapledger/installmentEvent'

const route = useRoute()
const event = ref(null)

function fmt(v) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtDate(d) {
  if (!d) return ''
  return d.toString().replaceAll('-', '/')
}
function sub(a, b) {
  return (Number(a) || 0) - (Number(b) || 0)
}

// 已结束分期：所有期都已还（paid = total）
// 进行中：paid = 日期 ≤ 今日 的期数累计
const today = new Date()
today.setHours(0, 0, 0, 0)
const paidPrincipal = computed(() => {
  if (!event.value?.periods) return 0
  return event.value.periods
    .filter(p => new Date(p.date) <= today)
    .reduce((s, p) => s + (Number(p.principal) || 0), 0)
})
const paidInterest = computed(() => {
  if (!event.value?.periods) return 0
  return event.value.periods
    .filter(p => new Date(p.date) <= today)
    .reduce((s, p) => s + (Number(p.interest) || 0), 0)
})

async function load() {
  try {
    event.value = await getInstallmentEvent(route.params.id)
  } catch (err) {
    console.error('Failed to load installment detail:', err)
  }
}

onMounted(load)
</script>

<style scoped>
.detail-page { min-height: 100vh; background: #f5f6f8; padding-bottom: 24px; }
.page-header {
  background: #fff; padding: 12px 16px; border-bottom: 1px solid #ebedf0;
  display: grid; grid-template-columns: 32px 1fr 32px; align-items: center;
}
.back-btn { font-size: 20px; color: #333; cursor: pointer; }
.page-title { font-size: 17px; font-weight: 600; color: #1a1a1a; text-align: center; }

.summary {
  background: #fff; padding: 20px 16px; text-align: center;
  border-bottom: 1px solid #ebedf0;
}
.summary-title { font-size: 16px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; }
.summary-amount { font-size: 28px; font-weight: 700; color: #f56c6c; }
.summary-sub { font-size: 11px; color: #969799; margin: 2px 0 10px; }
.summary-meta {
  font-size: 12px; color: #969799; display: flex; justify-content: center;
  gap: 12px; flex-wrap: wrap; margin-top: 4px;
}

.totals { margin: 12px; background: #fff; border-radius: 12px; padding: 8px 16px; }
.totals-row {
  display: flex; justify-content: space-between; font-size: 13px;
  padding: 8px 0; color: #333;
}
.totals-row .k { color: #969799; }
.totals-row .v { font-weight: 500; }
.totals-sep { height: 1px; background: #f0f0f0; margin: 4px -16px; }

.section { margin: 12px; background: #fff; border-radius: 12px; overflow: hidden; }
.section-title {
  font-size: 13px; font-weight: 600; color: #666; padding: 12px 16px 8px;
  border-bottom: 1px solid #f0f0f0;
}
.row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #f5f5f5;
}
.row:last-child { border-bottom: none; }
.row-left { display: flex; flex-direction: column; }
.row-period { font-size: 14px; color: #1a1a1a; font-weight: 500; }
.row-date { font-size: 11px; color: #969799; margin-top: 2px; }
.row-amount { font-size: 15px; color: #f56c6c; font-weight: 500; }
</style>
