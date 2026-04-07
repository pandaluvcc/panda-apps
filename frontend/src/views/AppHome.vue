<template>
  <div class="shell" :class="`shell--${currentTheme}`">
    <!-- 当前主题渲染 -->
    <component :is="themeComponent" :go-to-app="goToApp" />

    <!-- 主题切换触发按钮 -->
    <button class="fab" @click="showPicker = true" :style="fabStyle" aria-label="切换主题">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/>
      </svg>
    </button>

    <!-- 遮罩 -->
    <Transition name="fade">
      <div v-if="showPicker" class="overlay" @click="showPicker = false" />
    </Transition>

    <!-- 主题选择抽屉 -->
    <Transition name="slide-up">
      <div v-if="showPicker" class="picker-sheet">
        <div class="picker-handle"></div>
        <h3 class="picker-title">选择风格</h3>
        <div class="theme-grid">
          <button
            v-for="t in themes"
            :key="t.id"
            class="theme-card"
            :class="{ active: currentTheme === t.id }"
            @click="selectTheme(t.id)"
          >
            <div class="preview" :style="{ background: t.previewBg }">
              <div class="preview-dot" :style="{ background: t.accent }"></div>
              <div class="preview-line" :style="{ background: t.accent + '60' }"></div>
              <div class="preview-line short" :style="{ background: t.accent + '40' }"></div>
            </div>
            <span class="theme-name">{{ t.name }}</span>
            <span class="theme-sub">{{ t.sub }}</span>
            <span v-if="currentTheme === t.id" class="theme-check">✓</span>
          </button>
        </div>
        <button class="close-btn" @click="showPicker = false">关闭</button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import ThemeRevolut from './themes/ThemeRevolut.vue'
import ThemeWise    from './themes/ThemeWise.vue'
import ThemeMiro    from './themes/ThemeMiro.vue'
import ThemeCal     from './themes/ThemeCal.vue'
import ThemeClay    from './themes/ThemeClay.vue'

const router = useRouter()
const showPicker  = ref(false)
const currentTheme = ref(localStorage.getItem('panda-theme') || 'revolut')

const themes = [
  { id: 'revolut', name: 'Revolut', sub: '深色·科技',   previewBg: '#191c1f', accent: '#4f55f1' },
  { id: 'wise',    name: 'Wise',    sub: '石灰·活力',   previewBg: '#ffffff', accent: '#9fe870' },
  { id: 'miro',    name: 'Miro',    sub: 'Pastel·亲切', previewBg: '#ffffff', accent: '#5b76fe' },
  { id: 'cal',     name: 'Cal',     sub: '极简·黑白',   previewBg: '#ffffff', accent: '#242424' },
  { id: 'clay',    name: 'Clay',    sub: '暖奶油·手工', previewBg: '#faf9f7', accent: '#078a52' },
]

const themeMap = { revolut: ThemeRevolut, wise: ThemeWise, miro: ThemeMiro, cal: ThemeCal, clay: ThemeClay }
const themeComponent = computed(() => themeMap[currentTheme.value])

// FAB 颜色随主题变化，Revolut 用品牌蓝确保在深色背景上可见
const fabStyle = computed(() => {
  const map = {
    revolut: { background: '#4f55f1', color: '#ffffff', border: 'none' },
    wise:    { background: '#9fe870', color: '#163300', border: 'none' },
    miro:    { background: '#5b76fe', color: '#ffffff', border: 'none' },
    cal:     { background: '#242424', color: '#ffffff', border: 'none' },
    clay:    { background: '#000000', color: '#ffffff', border: 'none' },
  }
  return map[currentTheme.value] || map.revolut
})

const selectTheme = (id) => {
  currentTheme.value = id
  localStorage.setItem('panda-theme', id)
  showPicker.value = false
}

const goToApp = (path) => router.push(path)

const preventSwipeNavigation = (e) => {
  if (e.touches.length === 1) {
    const startX = e.touches[0].clientX
    const startY = e.touches[0].clientY
    const handleMove = (moveEvent) => {
      const dx = moveEvent.touches[0].clientX - startX
      const dy = moveEvent.touches[0].clientY - startY
      if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 10) moveEvent.preventDefault()
    }
    const handleEnd = () => {
      document.removeEventListener('touchmove', handleMove)
      document.removeEventListener('touchend', handleEnd)
    }
    document.addEventListener('touchmove', handleMove, { passive: false })
    document.addEventListener('touchend', handleEnd)
  }
}
onMounted(() => document.addEventListener('touchstart', preventSwipeNavigation, { passive: true }))
onUnmounted(() => document.removeEventListener('touchstart', preventSwipeNavigation))
</script>

<style scoped>
.shell {
  position: relative;
  min-height: 100dvh;
  padding-top: env(safe-area-inset-top);
}

/* ─── FAB 主题按钮 ────────────────────────────────────── */
.fab {
  position: fixed;
  bottom: calc(24px + env(safe-area-inset-bottom));
  right: 20px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 100;
  box-shadow: 0 2px 10px rgba(0,0,0,0.25);
  transition: transform 0.2s cubic-bezier(0.34,1.56,0.64,1), opacity 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.fab:active { transform: scale(0.9); }

/* ─── 遮罩 ────────────────────────────────────────────── */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  z-index: 200;
}

/* ─── 底部抽屉 ────────────────────────────────────────── */
.picker-sheet {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #ffffff;
  border-radius: 20px 20px 0 0;
  padding: 12px 20px calc(24px + env(safe-area-inset-bottom));
  z-index: 300;
  box-shadow: 0 -4px 32px rgba(0,0,0,0.15);
}

.picker-handle {
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: #e0e0e0;
  margin: 0 auto 20px;
}

.picker-title {
  font-size: 15px;
  font-weight: 600;
  color: #111;
  margin: 0 0 16px;
  letter-spacing: -0.2px;
}

/* 主题卡片网格 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.theme-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 4px;
  background: none;
  border: 1.5px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.1s ease;
  -webkit-tap-highlight-color: transparent;
}

.theme-card.active {
  border-color: #111;
  background: #f8f8f8;
}

.theme-card:active { transform: scale(0.95); }

/* 小预览色块 */
.preview {
  width: 100%;
  height: 36px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 6px 7px;
  gap: 4px;
  overflow: hidden;
}

.preview-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.preview-line {
  width: 80%;
  height: 3px;
  border-radius: 2px;
}

.preview-line.short { width: 55%; }

.theme-name {
  font-size: 11px;
  font-weight: 600;
  color: #111;
  letter-spacing: -0.1px;
}

.theme-sub {
  font-size: 9px;
  color: #999;
  text-align: center;
  letter-spacing: 0.1px;
  line-height: 1.3;
}

.theme-check {
  position: absolute;
  top: 5px;
  right: 7px;
  font-size: 10px;
  font-weight: 700;
  color: #111;
}

/* 关闭按钮 */
.close-btn {
  width: 100%;
  height: 44px;
  background: #f4f4f4;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: #555;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.close-btn:active { opacity: 0.7; }

/* ─── 过渡动画 ────────────────────────────────────────── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.slide-up-enter-active { transition: transform 0.32s cubic-bezier(0.32,0.72,0,1), opacity 0.25s ease; }
.slide-up-leave-active { transition: transform 0.25s ease, opacity 0.2s ease; }
.slide-up-enter-from  { transform: translateY(100%); opacity: 0; }
.slide-up-leave-to    { transform: translateY(100%); opacity: 0; }
</style>
