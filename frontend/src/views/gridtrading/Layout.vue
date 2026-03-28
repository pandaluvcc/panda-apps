<template>
  <div class="mobile-layout">
    <!-- 顶部导航栏 -->
    <header v-if="showHeader" class="mobile-header">
      <div class="header-left">
        <el-icon v-if="showBack" class="back-btn" @click.stop="goBack"><ArrowLeft /></el-icon>
      </div>
      <h1 class="header-title">{{ title }}</h1>
      <div class="header-right">
        <slot name="header-right"></slot>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="mobile-main" :class="{ 'no-header': !showHeader }">
      <slot></slot>
    </main>

    <!-- 底部Tab栏（仅首页显示） -->
    <nav v-if="showTabBar" class="mobile-tabbar">
      <div class="tab-item" :class="{ active: activeTab === 'strategies' }" @click="switchTab('strategies')">
        <el-icon><Grid /></el-icon>
        <span>我的网格</span>
      </div>
      <div class="tab-item" :class="{ active: activeTab === 'records' }" @click="switchTab('records')">
        <el-icon><Document /></el-icon>
        <span>成交记录</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, Grid, Document } from '@element-plus/icons-vue'

const props = defineProps({
  title: {
    type: String,
    default: '网格交易'
  },
  showBack: {
    type: Boolean,
    default: false
  },
  showTabBar: {
    type: Boolean,
    default: true
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  backTo: {
    type: String,
    default: ''
  }
})

const router = useRouter()
const route = useRoute()

const activeTab = computed(() => {
  if (route.path.includes('/grid/record')) return 'records'
  return 'strategies'
})

const goBack = () => {
  // 如果指定了返回路径，直接跳转
  if (props.backTo) {
    router.push(props.backTo)
    return
  }
  // 否则如果有历史记录则返回上一页，否则跳转到应用首页
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const switchTab = (tab) => {
  if (tab === 'strategies') {
    router.push('/grid')
  } else if (tab === 'records') {
    router.push('/grid/record')
  }
}
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-color);
  transition: background-color var(--transition-base);
}

/* 顶部导航 */
.mobile-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 50px;
  padding-top: env(safe-area-inset-top);
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 12px;
  padding-right: 12px;
  padding-bottom: 0;
  z-index: 100;
  border-bottom: 1px solid var(--border-lighter);
}

.header-left {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.back-btn {
  font-size: 22px;
  color: var(--text-primary);
  cursor: pointer;
  transition: opacity var(--transition-fast);
}

.back-btn:active {
  opacity: 0.6;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

/* 主内容区 */
.mobile-main {
  flex: 1;
  margin-top: calc(50px + env(safe-area-inset-top));
  margin-bottom: calc(60px + env(safe-area-inset-bottom));
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.mobile-main.no-header {
  margin-top: 0;
}

/* 底部Tab栏 */
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: var(--bg-card);
  display: flex;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.08);
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
  transition: background-color var(--transition-base);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.tab-item .el-icon {
  font-size: 22px;
}

.tab-item.active {
  color: var(--primary-color);
}

.tab-item.active .el-icon {
  color: var(--primary-color);
}
</style>
