<template>
  <div class="header-area">
    <!-- 收益卡片 -->
    <ProfitCard
      :total-market-value="totalMarketValue"
      :total-position-profit="totalPositionProfit"
      :today-profit="todayProfit"
      :is-midnight-to-market-open="isMidnightToMarketOpen"
    />
  </div>
</template>

<script setup>
import ProfitCard from './ProfitCard.vue'

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
  },
  isMidnightToMarketOpen: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.header-area {
  background: var(--bg-card);
  padding: 16px 16px 24px;
  border-radius: 0 0 20px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: background-color var(--transition-base);
}
</style>
