<template>
  <div class="budget-card" @click="$router.push('/snap/budget')">
    <div class="icon">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M19 7.5h-1.13A8.97 8.97 0 0 0 14.5 4v2.5a.5.5 0 0 1-1 0V3.55a9.05 9.05 0 0 0-2 0V6.5a.5.5 0 0 1-1 0V4a8.97 8.97 0 0 0-3.37 3.5H6a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h.18c.26.92.7 1.76 1.29 2.5H6v2a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-1.35c.79.23 1.62.35 2.5.35s1.71-.12 2.5-.35V19a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-2h-.47A8.97 8.97 0 0 0 18.36 14H19a2 2 0 0 0 2-2V9.5a2 2 0 0 0-2-2zM16 11a1 1 0 1 1 0-2 1 1 0 0 1 0 2z" fill="currentColor"/>
      </svg>
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
