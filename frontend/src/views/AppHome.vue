<template>
  <div class="app-home">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
    </div>

    <!-- 主内容 -->
    <div class="content">
      <!-- 头部 -->
      <div class="header">
        <h1 class="title">Panda Apps</h1>
        <p class="subtitle">我的应用中心</p>
      </div>

      <!-- 应用列表 -->
      <div class="app-list">
        <!-- 网格计划 -->
        <div class="app-card" @click="goToApp('/grid')">
          <div class="app-icon">
            <el-icon :size="32"><TrendCharts /></el-icon>
          </div>
          <div class="app-info">
            <h2 class="app-name">网格计划</h2>
            <p class="app-desc">加密货币网格交易策略管理</p>
          </div>
          <div class="app-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>

        <!-- 快记账 -->
        <div class="app-card app-card-disabled" @click="showComingSoon">
          <div class="app-icon">
            <el-icon :size="32"><Wallet /></el-icon>
          </div>
          <div class="app-info">
            <h2 class="app-name">快记账</h2>
            <p class="app-desc">个人记账工具 · 即将上线</p>
          </div>
          <div class="app-badge">敬请期待</div>
        </div>
      </div>

      <!-- 底部信息 -->
      <div class="footer">
        <p>更多应用开发中...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { TrendCharts, ArrowRight, Wallet } from '@element-plus/icons-vue'

const router = useRouter()

// 禁用浏览器滑动手势导航
const preventSwipeNavigation = (e) => {
  // 阻止水平滑动触发的浏览器导航
  if (e.touches.length === 1) {
    const touch = e.touches[0]
    const startX = touch.clientX
    const startY = touch.clientY

    const handleMove = (moveEvent) => {
      const deltaX = moveEvent.touches[0].clientX - startX
      const deltaY = moveEvent.touches[0].clientY - startY

      // 如果水平滑动幅度大于垂直滑动，阻止默认行为
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

const showComingSoon = () => {
  ElMessage.info('快记账功能即将上线，敬请期待！')
}
</script>

<style scoped>
.app-home {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  /* 禁用水平滑动手势 */
  touch-action: pan-y;
  overscroll-behavior-x: none;
}

/* 深色模式 */
@media (prefers-color-scheme: dark) {
  .app-home {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }

  .app-card {
    background: #ffffff;
  }

  .app-name {
    color: #1a1a2e;
  }

  .app-desc {
    color: #6b7280;
  }

  .app-arrow {
    color: #9ca3af;
  }

  .app-badge {
    background: #f3f4f6;
    color: #6b7280;
  }
}

/* 背景装饰圆 */
.bg-decoration {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}

.bg-circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  right: -50px;
}

.bg-circle-2 {
  width: 200px;
  height: 200px;
  bottom: 100px;
  left: -80px;
}

/* 主内容 */
.content {
  position: relative;
  z-index: 1;
  padding: 60px 20px 40px;
  max-width: 480px;
  margin: 0 auto;
}

/* 头部 */
.header {
  text-align: center;
  margin-bottom: 48px;
}

.title {
  font-size: 32px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 8px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

/* 应用列表 */
.app-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 应用卡片 - 白色实心背景 */
.app-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.app-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.app-card:active {
  transform: scale(0.98);
}

/* 禁用状态卡片 */
.app-card-disabled {
  opacity: 0.6;
}

.app-card-disabled:hover {
  transform: none;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.app-card-disabled:active {
  transform: scale(1);
}

/* 应用图标 */
.app-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: #ffffff;
  flex-shrink: 0;
}

/* 应用信息 */
.app-info {
  flex: 1;
  margin-left: 16px;
  min-width: 0;
}

.app-name {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 4px;
}

.app-desc {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

/* 箭头 */
.app-arrow {
  color: #9ca3af;
  flex-shrink: 0;
}

/* 徽章 */
.app-badge {
  padding: 4px 10px;
  background: #f3f4f6;
  border-radius: 12px;
  font-size: 12px;
  color: #6b7280;
  flex-shrink: 0;
}

/* 底部 */
.footer {
  text-align: center;
  margin-top: 48px;
}

.footer p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}
</style>
