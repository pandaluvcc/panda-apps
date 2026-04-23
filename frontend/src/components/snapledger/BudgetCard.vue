<template>
  <div class="budget-card" @click="$router.push('/snap/budget')">
    <div class="icon">
      <van-icon name="gold-coin-o" size="28" />
    </div>
    <div class="content">
      <div class="title">每月统计</div>
      <div class="subtitle">
        今日剩余预算
        <span class="amount">¥{{ remainingBudget.toFixed(2) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
// 占位：预算系统落地后改为从 API 获取
const remainingBudget = 270.00
</script>

<style scoped>
.budget-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 16px;
  padding: 16px;
  background: var(--bg-white);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
}

.icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--bg-light);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.content {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.subtitle {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.amount {
  margin-left: 8px;
  color: var(--profit-negative);
  font-weight: 600;
}
</style>
