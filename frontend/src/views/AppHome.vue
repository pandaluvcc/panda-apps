<template>
  <div class="app-home">
    <!-- 背景噪点纹理 -->
    <div class="bg-noise"></div>

    <!-- 主内容 -->
    <div class="content">
      <!-- 头部 -->
      <div class="header">
        <div class="header-icon">
          <svg width="52" height="52" viewBox="0 0 52 52" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect class="logo-rect logo-rect-1" x="6" y="6" width="16" height="16" rx="5" fill="#2dd4bf"/>
            <rect class="logo-rect logo-rect-2" x="30" y="6" width="16" height="16" rx="5" fill="#fb923c"/>
            <rect class="logo-rect logo-rect-3" x="6" y="30" width="16" height="16" rx="5" fill="#0f766e"/>
            <rect class="logo-rect logo-rect-4" x="30" y="30" width="16" height="16" rx="5" fill="#c2410c"/>
          </svg>
        </div>
        <h1 class="title">Panda Apps</h1>
        <p class="subtitle">我的应用中心</p>
      </div>

      <!-- 应用列表 -->
      <div class="app-list">
        <!-- 网格计划 -->
        <div class="app-card" @click="goToApp('/grid')">
          <div class="app-icon grid-icon">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="app-info">
            <h2 class="app-name">网格计划</h2>
            <p class="app-desc">加密货币网格交易策略管理</p>
          </div>
          <div class="app-arrow">
            <el-icon :size="16"><ArrowRight /></el-icon>
          </div>
        </div>

        <!-- 快记账 -->
        <div class="app-card" @click="goToApp('/snap/calendar')">
          <div class="app-icon snap-icon">
            <el-icon :size="28"><Wallet /></el-icon>
          </div>
          <div class="app-info">
            <h2 class="app-name">快记账</h2>
            <p class="app-desc">个人账单随手记</p>
          </div>
          <div class="app-arrow">
            <el-icon :size="16"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- 底部 -->
      <div class="footer">
        <p>更多应用开发中</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { TrendCharts, ArrowRight, Wallet } from '@element-plus/icons-vue'

const router = useRouter()

const preventSwipeNavigation = (e) => {
  if (e.touches.length === 1) {
    const touch = e.touches[0]
    const startX = touch.clientX
    const startY = touch.clientY

    const handleMove = (moveEvent) => {
      const deltaX = moveEvent.touches[0].clientX - startX
      const deltaY = moveEvent.touches[0].clientY - startY
      if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
        moveEvent.preventDefault()
      }
    }

    const handleEnd = () => {
      document.removeEventListener('touchmove', handleMove)
      document.removeEventListener('touchend', handleEnd)
    }

    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
  }
}

onMounted(() => {
  document.addEventListener('touchstart', preventSwipeNavigation, { passive: true })
})

onUnmounted(() => {
  document.removeEventListener('touchstart', preventSwipeNavigation)
})

const goToApp = (path) => {
  router.push(path)
}
</script>

<style scoped>
.app-home {
  min-height: 100vh;
  position: relative;
  background-color: #f8f8f8;
  touch-action: pan-y;
  overscroll-behavior-x: none;
}

.bg-noise {
  display: none;
}

/* 主内容 */
.content {
  position: relative;
  z-index: 1;
  padding: 72px 24px 48px;
  max-width: 480px;
  margin: 0 auto;
}

/* 头部 */
.header {
  text-align: center;
  margin-bottom: 52px;
}

.header-icon {
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
}

@keyframes pop-in {
  0%   { opacity: 0; transform: scale(0.4); }
  70%  { transform: scale(1.15); }
  100% { opacity: 1; transform: scale(1); }
}

@keyframes bounce-idle {
  0%, 60%, 100% { transform: translateY(0); }
  30%            { transform: translateY(-4px); }
}

.logo-rect {
  opacity: 0;
  transform-origin: center;
  animation:
    pop-in 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards,
    bounce-idle 2.4s ease-in-out 0.8s infinite;
}

.logo-rect-1 { animation-delay: 0.05s, 0.8s; }
.logo-rect-2 { animation-delay: 0.15s, 1.4s; }
.logo-rect-3 { animation-delay: 0.25s, 1.1s; }
.logo-rect-4 { animation-delay: 0.35s, 1.7s; }


.title {
  font-size: 26px;
  font-weight: 700;
  color: #1d1c1d;
  margin: 0 0 6px;
  letter-spacing: -0.3px;
}

.subtitle {
  font-size: 14px;
  color: #616061;
  margin: 0;
}

/* 应用列表 */
.app-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 应用卡片 */
.app-card {
  display: flex;
  align-items: center;
  padding: 16px 18px;
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s ease, box-shadow 0.15s ease, transform 0.12s ease;
}

.app-card:hover {
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border-color: #d1d1d1;
}

.app-card:active {
  transform: scale(0.99);
  background: #f9f9f9;
}

/* 应用图标 */
.app-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  flex-shrink: 0;
}

/* 网格计划 */
.grid-icon {
  background: #eff6ff;
  color: #2563eb;
}

/* 快记账 */
.snap-icon {
  background: #fff3e8;
  color: #c2410c;
}

/* 应用信息 */
.app-info {
  flex: 1;
  margin-left: 16px;
  min-width: 0;
}

.app-name {
  font-size: 16px;
  font-weight: 700;
  color: #1d1c1d;
  margin: 0 0 3px;
}

.app-desc {
  font-size: 13px;
  color: #616061;
  margin: 0;
}

/* 箭头 */
.app-arrow {
  color: #c8c8c8;
  flex-shrink: 0;
  margin-left: 8px;
}

/* 底部 */
.footer {
  text-align: center;
  margin-top: 48px;
}

.footer p {
  font-size: 12px;
  color: #b0b0b0;
  margin: 0;
}
</style>
