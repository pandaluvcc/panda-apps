<template>
  <div class="placeholder-page">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">周期事件</span>
      <span class="header-right"></span>
    </div>
    <div class="empty">
      <van-icon name="calendar-o" class="empty-icon" />
      <p class="empty-title">功能开发中</p>
      <p class="empty-desc">周期性重复发生的账单事件（如月供、订阅）将在此管理</p>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.placeholder-page {
  min-height: 100vh;
  background: #f5f6f8;
  display: flex;
  flex-direction: column;
}
.page-header {
  background: #fff;
  padding: 12px 16px;
  border-bottom: 1px solid #ebedf0;
  display: grid;
  grid-template-columns: 32px 1fr 32px;
  align-items: center;
}
.back-btn {
  font-size: 20px;
  color: #333;
  cursor: pointer;
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  text-align: center;
}
.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 32px;
  text-align: center;
}
.empty-icon {
  font-size: 56px;
  color: #c8c9cc;
  margin-bottom: 16px;
}
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #646566;
  margin: 0 0 8px;
}
.empty-desc {
  font-size: 13px;
  color: #969799;
  line-height: 1.6;
  margin: 0;
}
</style>
