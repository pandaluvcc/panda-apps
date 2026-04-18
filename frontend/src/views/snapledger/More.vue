<template>
  <div class="more-page">
    <div class="page-header">
      <span class="page-title">更多</span>
    </div>

    <div class="content">
      <!-- 分组：导入 -->
      <section class="group">
        <header class="group-header">
          <van-icon name="down" class="group-icon" />
          <span class="group-title">导入</span>
        </header>
        <div class="group-items">
          <div class="list-item" @click="$router.push('/snap/import')">
            <span class="item-label">CSV 导入</span>
            <van-icon name="arrow" class="item-arrow" />
          </div>
        </div>
      </section>

      <!-- 分组：事件 -->
      <section class="group">
        <header class="group-header">
          <van-icon name="calendar-o" class="group-icon" />
          <span class="group-title">事件</span>
        </header>
        <div class="group-items">
          <div class="list-item" @click="$router.push('/snap/events/recurring')">
            <span class="item-label">周期事件</span>
            <van-icon name="arrow" class="item-arrow" />
          </div>
          <div class="list-item" @click="$router.push('/snap/events/installment')">
            <span class="item-label">分期事件</span>
            <van-icon name="arrow" class="item-arrow" />
          </div>
        </div>
      </section>
    </div>

    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const activeTab = ref(4)
</script>

<style scoped>
.more-page {
  min-height: 100vh;
  background: #f5f6f8;
  padding-bottom: 72px;
}

.page-header {
  background: #fff;
  padding: 12px 16px;
  border-bottom: 1px solid #ebedf0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}

.content {
  padding: 16px 12px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f2f5;
}
.group-icon {
  font-size: 18px;
  color: #667eea;
}
.group-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.group-items {
  display: flex;
  flex-direction: column;
}

.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.15s ease;
  min-height: 48px;
}
.list-item + .list-item {
  border-top: 1px solid #f5f6f8;
}
.list-item:active {
  background: #f5f6f8;
}

.item-label {
  font-size: 15px;
  color: #1a1a1a;
}
.item-arrow {
  font-size: 14px;
  color: #c8c9cc;
}
</style>
