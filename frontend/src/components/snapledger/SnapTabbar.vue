<template>
  <div class="snap-tabbar-wrapper">
    <!-- 底部导航背景 -->
    <div class="tabbar-bg"></div>

    <!-- 中央LOGO按钮（绝对定位） -->
    <div class="center-logo" @click="handleCenterClick">
      <div class="logo-wrapper">
        <van-icon v-if="isLogoPage" name="plus" class="plus-icon" />
        <div v-else class="logo-svg">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="24" cy="24" r="22" fill="url(#logoGradient)" />
            <path d="M24 12C20 12 17 15 17 19V22C14 23 12 26 12 30C12 36 17 40 24 40C31 40 36 36 36 30C36 26 34 23 31 22V19C31 15 28 12 24 12Z" fill="white" fill-opacity="0.95"/>
            <path d="M19 12C19 10.5 21 9 24 9C27 9 29 10.5 29 12" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
            <text x="24" y="34" text-anchor="middle" fill="url(#logoGradient)" font-size="16" font-weight="bold">¥</text>
            <defs>
              <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#667eea"/>
                <stop offset="100%" style="stop-color:#764ba2"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
      </div>
    </div>

    <!-- 导航项 -->
    <div class="tabbar-items">
      <div class="tabbar-item" :class="{ active: activeIndex === 0 }" @click="navigateTo('/snap', 0)">
        <van-icon name="gold-coin-o" />
        <span>总览</span>
      </div>
      <div class="tabbar-item" :class="{ active: activeIndex === 1 }" @click="navigateTo('/snap/stats', 1)">
        <van-icon name="bar-chart-o" />
        <span>统计</span>
      </div>
      <!-- 中间占位 -->
      <div class="tabbar-item center-placeholder"></div>
      <div class="tabbar-item" :class="{ active: activeIndex === 3 }" @click="navigateTo('/snap/budget', 3)">
        <van-icon name="balance-list-o" />
        <span>预算</span>
      </div>
      <div class="tabbar-item" :class="{ active: activeIndex === 4 }" @click="navigateTo('/snap/more', 4)">
        <van-icon name="apps-o" />
        <span>更多</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue'])

const activeIndex = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isLogoPage = computed(() => route.path === '/snap/calendar')

function handleCenterClick() {
  if (isLogoPage.value) {
    router.push('/snap/add')
  } else {
    router.push('/snap/calendar')
  }
}

function navigateTo(path, index) {
  activeIndex.value = index
  router.push(path)
}
</script>

<style scoped>
.snap-tabbar-wrapper {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 50px;
}

.tabbar-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 50px;
  background: #fff;
  border-top: 1px solid #ebedf0;
}

.center-logo {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: -12px;
  z-index: 101;
}

.logo-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  cursor: pointer;
  transition: transform 0.2s ease;
}

.logo-wrapper:active {
  transform: scale(0.95);
}

.plus-icon {
  font-size: 28px;
  color: white;
  font-weight: bold;
}

.logo-svg {
  width: 40px;
  height: 40px;
}

.logo-svg svg {
  width: 100%;
  height: 100%;
  display: block;
}

.tabbar-items {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  align-items: center;
  height: 50px;
  background: transparent;
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 12px;
  color: #646566;
  cursor: pointer;
  padding: 4px 0;
}

.tabbar-item.active {
  color: #667eea;
}

.tabbar-item .van-icon {
  font-size: 20px;
}

.center-placeholder {
  pointer-events: none;
}
</style>
