<template>
  <div class="profit-card">
    <!-- 第一行：证券市值 - 最重要数据，独占一行 -->
    <div class="market-value-section">
      <div class="profit-label">证券市值</div>
      <div class="market-value">¥{{ formatAmount(totalMarketValue) }}</div>
    </div>

    <!-- 分隔线 -->
    <div class="section-divider"></div>

    <!-- 第二行：持仓盈亏 + 今日盈亏 - 两并排 -->
    <div class="bottom-row">
      <div class="bottom-item">
        <div class="profit-label">持仓盈亏</div>
        <div class="profit-value" :class="getProfitClass(totalPositionProfit)">
          {{ formatProfit(totalPositionProfit) }}
        </div>
      </div>
      <div class="divider"></div>
      <div class="bottom-item">
        <div class="profit-label">今日盈亏</div>
        <div class="profit-value" :class="getProfitClass(todayProfit)">
          {{ formatProfit(todayProfit) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatAmount } from '@/utils/format'

defineProps({
  totalMarketValue: {
    type: [Number, String],
    default: 0
  },
  totalPositionProfit: {
    type: [Number, String],
    default: 0
  },
  todayProfit: {
    type: [Number, String],
    default: 0
  }
})

const formatProfit = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : `${num >= 0 ? '+' : ''}${num.toFixed(2)}`
}

// 获取盈亏颜色类：正数红色，负数绿色，零值灰色
const getProfitClass = (val) => {
  if (val === null || val === undefined || val === '') return 'profit-zero'
  const num = Number(val)
  if (isNaN(num) || num === 0) return 'profit-zero'
  return num > 0 ? 'profit-positive' : 'profit-negative'
}
</script>

<style scoped>
.profit-card {
  padding: 12px 16px;
}

/* 第一行：证券市值 */
.market-value-section {
  text-align: center;
  padding-bottom: 8px;
}

.profit-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  font-weight: 500;
}

.market-value {
  font-size: 26px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: -0.3px;
  line-height: 1.2;
  color: var(--text-primary);
}

/* 分隔线 */
.section-divider {
  height: 1px;
  width: 100%;
  background: var(--border-light);
  margin: 10px 0;
}

/* 第二行：底部两列 */
.bottom-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
}

.bottom-item {
  flex: 1;
  text-align: center;
  padding: 0 8px;
}

.divider {
  width: 1px;
  height: 32px;
  background: var(--border-light);
}

.profit-value {
  font-size: 20px;
  font-weight: 600;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: -0.2px;
  line-height: 1.2;
}

/* 盈亏颜色 */
.profit-positive {
  color: var(--profit-positive);
}

.profit-negative {
  color: var(--profit-negative);
}

.profit-zero {
  color: var(--text-secondary);
}
</style>
