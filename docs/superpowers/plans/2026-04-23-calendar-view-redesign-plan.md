# Calendar View Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Snap Ledger 日历视图重设计为 Moze 日期视图风格（三档颜色、今日圆圈、月首标记、顶栏图标占位、预算卡占位）。

**Architecture:** 前端纯视觉改版，不改后端。重写 `CalendarGrid.vue` + `Calendar.vue`，新增 `CalendarHeader.vue` + `BudgetCard.vue`。CSS 变量统一加到 `frontend/src/styles/variables.css`（含 dark mode）。

**Tech Stack:** Vue 3 `<script setup>` + Vant 4 + vitest + @vue/test-utils（jsdom 环境）。

**Spec:** `docs/superpowers/specs/2026-04-23-calendar-view-redesign-design.md`

---

## Pre-flight（已验证，此处备案）

以下在计划撰写时已逐一核对，实施者无需再查：

- `frontend/src/styles/variables.css` 包含：`--bg-white`、`--bg-light`、`--bg-color`、`--shadow-sm`、`--border-radius-lg`、`--profit-negative`、`--text-primary/regular/secondary`、`--border-color`、`--font-size-xs/sm/base/lg`、`--page-transition-duration`（所有本计划引用的变量均存在）
- `frontend/src/router.js` 有 `path: '/snap/budget'` 路由
- `frontend/src/utils/format.js` 导出 `formatDateISO`（现 `Calendar.vue` 已在用）
- `frontend/package.json` 有 `lint` 脚本
- Vant 4 含 `bell`、`search`、`gold-coin-o` 图标

---

## File Structure

### Create

- `frontend/src/components/snapledger/CalendarHeader.vue` — 顶栏：🔔 + 日期标题 + 🔍
- `frontend/src/components/snapledger/BudgetCard.vue` — 底部预算卡（占位）
- `frontend/tests/components/snapledger/CalendarGrid.test.js` — 单元测试

### Modify

- `frontend/src/styles/variables.css` — 新增 11 个 `--cal-*` 变量（light + dark）
- `frontend/src/components/snapledger/CalendarGrid.vue` — 重写（填充逻辑、三档着色、月首标记、拉手条）
- `frontend/src/views/snapledger/Calendar.vue` — 重写（组装新组件，标题=选中日期而非当月）

### 不动

- 后端：`CalendarService.java` 已返回 `recordCount`，够用
- `RecordList.vue`、`SnapTabbar.vue`：复用
- `router.js`：不变（日历路由不变）

---

## Task 1: 新增 CSS 变量

**Files:**
- Modify: `frontend/src/styles/variables.css`（在 `:root` 最后追加 + dark mode 块里追加）

- [ ] **Step 1: 在 `:root` 块末尾（`--page-transition-duration` 之后、结束 `}` 之前）追加日历色变量**

```css
  /* 日历日期颜色 — 三档 × 三色相（见 calendar-view-redesign-design） */
  --cal-weekday-strong: #323233;
  --cal-weekday-weak: #c8c9cc;
  --cal-weekday-faint: #ebedf0;

  --cal-sunday-strong: #ee0a24;
  --cal-sunday-weak: #fcc9ce;
  --cal-sunday-faint: #fde6e9;

  --cal-saturday-strong: #07c160;
  --cal-saturday-weak: #bfe7cc;
  --cal-saturday-faint: #dff3e6;

  --cal-today-ring: #1989fa;
  --cal-selected-ring: #c8c9cc;
```

- [ ] **Step 2: 在 dark mode 块（`@media (prefers-color-scheme: dark) :root`）末尾追加**

```css
    --cal-weekday-strong: #e5e5e5;
    --cal-weekday-weak: #6b6b6b;
    --cal-weekday-faint: #3a3a3a;

    --cal-sunday-strong: #ff6b6b;
    --cal-sunday-weak: #8a4a4f;
    --cal-sunday-faint: #4a2a2e;

    --cal-saturday-strong: #6dd580;
    --cal-saturday-weak: #4a7555;
    --cal-saturday-faint: #2a4532;

    --cal-today-ring: #4da5ff;
    --cal-selected-ring: #6b6b6b;
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/styles/variables.css
git commit -m "feat(snapledger): add calendar date color variables (light+dark)"
```

---

## Task 2: 新建 CalendarHeader 组件

**Files:**
- Create: `frontend/src/components/snapledger/CalendarHeader.vue`

- [ ] **Step 1: 创建组件**

```vue
<template>
  <div class="calendar-header">
    <div class="icon-btn" @click="onBellClick">
      <van-icon name="bell" size="22" />
    </div>
    <div class="title" @click="$emit('click-title')">
      {{ formattedDate }}
    </div>
    <div class="icon-btn" @click="onSearchClick">
      <van-icon name="search" size="22" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  selectedDate: { type: Date, required: true }
})

defineEmits(['click-title'])

const formattedDate = computed(() => {
  const d = props.selectedDate
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}/${m}/${day}`
})

function onBellClick() {
  showToast('通知功能开发中')
}

function onSearchClick() {
  showToast('搜索功能开发中')
}
</script>

<style scoped>
.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  background: var(--bg-white);
}

.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--bg-white);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-primary);
}

.icon-btn:active {
  background: var(--bg-light);
}

.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/snapledger/CalendarHeader.vue
git commit -m "feat(snapledger): add CalendarHeader component (bell/title/search)"
```

---

## Task 3: 新建 BudgetCard 组件

**Files:**
- Create: `frontend/src/components/snapledger/BudgetCard.vue`

- [ ] **Step 1: 创建组件（占位，硬编码金额）**

```vue
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
  color: var(--profit-negative); /* 绿色（剩余=正向） */
  font-weight: 600;
}
</style>
```

> **Note:** Vant 没有直接的「小猪」图标，先用 `gold-coin-o`（金币图标）近似。若需要小猪可后续改为内联 SVG，不阻塞本 spec。

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/snapledger/BudgetCard.vue
git commit -m "feat(snapledger): add BudgetCard placeholder component"
```

---

## Task 4: 重写 CalendarGrid — 先写测试

**Files:**
- Create: `frontend/tests/components/snapledger/CalendarGrid.test.js`

- [ ] **Step 1: 写测试（覆盖填充逻辑 + 着色 class + 月首标记）**

```js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'

const stubs = {
  VanIcon: { template: '<i class="van-icon"></i>', props: ['name', 'size'] }
}

function mountGrid(props = {}) {
  return mount(CalendarGrid, {
    props: {
      year: 2026,
      month: 4,
      days: [],
      selectedDate: new Date(2026, 3, 23), // 4/23
      ...props
    },
    global: { stubs }
  })
}

describe('CalendarGrid', () => {
  describe('网格填充', () => {
    it('2026/4 前置填充 3 天（3/29-3/31），后置填充 2 天（5/1-5/2）', () => {
      // 2026-04-01 是周三 (getDay=3)，前补 3 天
      // 2026-04-30 是周四 (getDay=4)，后补 2 天（周五+周六）
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      expect(cells.length).toBe(35) // 5 周 × 7

      // 第一格 = 3/29（周日）
      expect(cells[0].text()).toBe('29')
      expect(cells[0].classes()).toContain('out-of-month')

      // 第四格 = 4/1（周三），显示"4月"
      expect(cells[3].text()).toBe('4月')
      expect(cells[3].classes()).not.toContain('out-of-month')

      // 最后一格 = 5/2（周六），out-of-month
      expect(cells[34].text()).toBe('02')
      expect(cells[34].classes()).toContain('out-of-month')
    })

    it('2026/1 前补 4 天跨年到 2025/12', () => {
      // 2026-01-01 是周四 (getDay=4)，前补 4 天 = 2025/12/28-31
      const wrapper = mountGrid({
        year: 2026, month: 1,
        selectedDate: new Date(2026, 0, 1)
      })
      const cells = wrapper.findAll('.day-cell')
      expect(cells[0].text()).toBe('28')
      expect(cells[0].classes()).toContain('out-of-month')
      expect(cells[4].text()).toBe('1月') // 当月 1 号显示月份
    })

    it('2025/12 后补 3 天跨年到 2026/1', () => {
      // 2025-12-31 是周三 (getDay=3)，后补 3 天 = 2026/1/1-3
      const wrapper = mountGrid({
        year: 2025, month: 12,
        selectedDate: new Date(2025, 11, 31)
      })
      const cells = wrapper.findAll('.day-cell')
      const last = cells[cells.length - 1]
      expect(last.text()).toBe('03')
      expect(last.classes()).toContain('out-of-month')
    })
  })

  describe('日期着色 class', () => {
    it('工作日+有记录 → weekday-strong', () => {
      // 4/20 是周一，假设有 2 条记录
      const wrapper = mountGrid({
        days: [{ date: '2026-04-20', recordCount: 2, income: 0, expense: 0 }]
      })
      const cells = wrapper.findAll('.day-cell')
      const apr20 = cells.find(c => c.text() === '20')
      expect(apr20.classes()).toContain('weekday-strong')
    })

    it('工作日+无记录 → weekday-weak', () => {
      const wrapper = mountGrid({ days: [] })
      const apr21 = wrapper.findAll('.day-cell').find(c => c.text() === '21')
      expect(apr21.classes()).toContain('weekday-weak')
    })

    it('周日+无记录 → sunday-weak', () => {
      const wrapper = mountGrid({ days: [] })
      // 2026/4/5 是周日
      const apr5 = wrapper.findAll('.day-cell').find(c => c.text() === '05')
      expect(apr5.classes()).toContain('sunday-weak')
    })

    it('周六+非当月 → saturday-faint', () => {
      const wrapper = mountGrid()
      // 3/29 是周日（填充）；5/2 是周六（填充）
      const may2 = wrapper.findAll('.day-cell').find(c => c.text() === '02')
      expect(may2.classes()).toContain('saturday-faint')
    })

    it('非当月填充位不可点击（emit select 被阻止）', async () => {
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      await cells[0].trigger('click') // 3/29 out-of-month
      expect(wrapper.emitted('select')).toBeFalsy()
    })

    it('当月日期点击 → emit select(Date)', async () => {
      const wrapper = mountGrid()
      const apr15 = wrapper.findAll('.day-cell').find(c => c.text() === '15')
      await apr15.trigger('click')
      const emitted = wrapper.emitted('select')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toBeInstanceOf(Date)
      expect(emitted[0][0].getDate()).toBe(15)
    })
  })

  describe('今日圆圈', () => {
    it('当日是今天 → 加 is-today class', () => {
      const today = new Date()
      const wrapper = mountGrid({
        year: today.getFullYear(),
        month: today.getMonth() + 1,
        selectedDate: today
      })
      const cells = wrapper.findAll('.day-cell')
      const todayCell = cells.find(c => {
        const t = today.getDate() === 1
          ? `${today.getMonth() + 1}月`
          : String(today.getDate()).padStart(2, '0')
        return c.text() === t && !c.classes().includes('out-of-month')
      })
      expect(todayCell.classes()).toContain('is-today')
    })

    it('今日 = 选中时，只显示 is-today，不叠加 is-selected', () => {
      const today = new Date()
      const wrapper = mountGrid({
        year: today.getFullYear(),
        month: today.getMonth() + 1,
        selectedDate: today // 今日 = 选中
      })
      const cells = wrapper.findAll('.day-cell')
      const todayCell = cells.find(c => {
        const t = today.getDate() === 1
          ? `${today.getMonth() + 1}月`
          : String(today.getDate()).padStart(2, '0')
        return c.text() === t && !c.classes().includes('out-of-month')
      })
      expect(todayCell.classes()).toContain('is-today')
      expect(todayCell.classes()).not.toContain('is-selected')
    })
  })

  describe('月首标记', () => {
    it('非当月填充位的 1 号不显示"X月"', () => {
      // 2026/4 视图下，5/1 是填充位（周五），应显示 "01" 而非 "5月"
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      // 倒数第 2 格 = 5/1（周五），倒数第 1 格 = 5/2（周六）
      const may1 = cells[cells.length - 2]
      expect(may1.classes()).toContain('out-of-month')
      expect(may1.text()).toBe('01') // 不是 "5月"
    })

    it('六周月（当月跨 6 周）渲染 42 格', () => {
      // 2026/8 有 31 天，8/1 是周六 → startPadding=6, endPadding=6-1=5
      // 总格数 = 6 + 31 + 5 = 42
      const wrapper = mountGrid({
        year: 2026, month: 8,
        selectedDate: new Date(2026, 7, 1)
      })
      const cells = wrapper.findAll('.day-cell')
      expect(cells.length).toBe(42)
    })
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend && npx vitest run tests/components/snapledger/CalendarGrid.test.js
```

Expected: 全部失败（组件还没改到满足这些契约）

- [ ] **Step 3: Commit（测试先行）**

```bash
git add frontend/tests/components/snapledger/CalendarGrid.test.js
git commit -m "test(snapledger): add CalendarGrid tests for new visual rules"
```

---

## Task 5: 重写 CalendarGrid 组件

**Files:**
- Modify: `frontend/src/components/snapledger/CalendarGrid.vue`（整文件替换）

- [ ] **Step 1: 覆盖写入新组件**

```vue
<template>
  <div
    class="calendar-grid"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- 星期标题 -->
    <div class="week-header">
      <span class="sunday-col">周日</span>
      <span>周一</span>
      <span>周二</span>
      <span>周三</span>
      <span>周四</span>
      <span>周五</span>
      <span class="saturday-col">周六</span>
    </div>

    <!-- 日期格子 -->
    <div class="days-grid" :class="transitionClass">
      <div
        v-for="(day, index) in calendarDays"
        :key="index"
        class="day-cell"
        :class="cellClasses(day)"
        @click="onCellClick(day)"
      >
        <span class="day-number">{{ cellLabel(day) }}</span>
      </div>
    </div>

    <!-- 装饰性拉手条 -->
    <div class="pull-handle"></div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  days: { type: Array, default: () => [] },
  selectedDate: { type: Date, default: null }
})

const emit = defineEmits(['select', 'swipe'])

const touchStartX = ref(0)
const touchStartY = ref(0)
const transitionClass = ref('')

function onTouchStart(e) {
  touchStartX.value = e.touches[0].clientX
  touchStartY.value = e.touches[0].clientY
}

function onTouchMove(e) {
  const deltaX = e.touches[0].clientX - touchStartX.value
  const deltaY = e.touches[0].clientY - touchStartY.value
  if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
    e.preventDefault()
  }
}

function onTouchEnd(e) {
  const deltaX = e.changedTouches[0].clientX - touchStartX.value
  const deltaY = e.changedTouches[0].clientY - touchStartY.value
  if (Math.abs(deltaX) > 50 && Math.abs(deltaY) < 30) {
    if (deltaX > 0) {
      transitionClass.value = 'slide-right'
      setTimeout(() => { transitionClass.value = '' }, 300)
      emit('swipe', 'prev')
    } else {
      transitionClass.value = 'slide-left'
      setTimeout(() => { transitionClass.value = '' }, 300)
      emit('swipe', 'next')
    }
  }
}

// 构造 5-6 周的日期列表：前月末填充 + 当月 + 下月初填充
const calendarDays = computed(() => {
  const firstDay = new Date(props.year, props.month - 1, 1)
  const lastDay = new Date(props.year, props.month, 0)
  const startPadding = firstDay.getDay() // 0=周日
  const endPadding = 6 - lastDay.getDay()

  // 查找当月每日 recordCount
  const recordCountByDay = new Map()
  for (const d of props.days) {
    const dayNum = parseInt(d.date.split('-')[2], 10)
    recordCountByDay.set(dayNum, d.recordCount || 0)
  }

  const result = []

  // 前置填充：上月末尾日期
  for (let i = startPadding - 1; i >= 0; i--) {
    const date = new Date(props.year, props.month - 1, -i)
    result.push({
      date,
      dayOfWeek: date.getDay(),
      recordCount: 0,
      outOfMonth: true
    })
  }

  // 当月日期
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(props.year, props.month - 1, d)
    result.push({
      date,
      dayOfWeek: date.getDay(),
      recordCount: recordCountByDay.get(d) || 0,
      outOfMonth: false
    })
  }

  // 后置填充：下月开头日期
  for (let i = 1; i <= endPadding; i++) {
    const date = new Date(props.year, props.month, i)
    result.push({
      date,
      dayOfWeek: date.getDay(),
      recordCount: 0,
      outOfMonth: true
    })
  }

  return result
})

function cellLabel(day) {
  if (!day.outOfMonth && day.date.getDate() === 1) {
    return `${day.date.getMonth() + 1}月`
  }
  return String(day.date.getDate()).padStart(2, '0')
}

function cellClasses(day) {
  const classes = []

  // 色相（工作日/周日/周六）
  let hue
  if (day.dayOfWeek === 0) hue = 'sunday'
  else if (day.dayOfWeek === 6) hue = 'saturday'
  else hue = 'weekday'

  // 档位（faint/strong/weak）
  let tier
  if (day.outOfMonth) tier = 'faint'
  else if (day.recordCount > 0) tier = 'strong'
  else tier = 'weak'

  classes.push(`${hue}-${tier}`)
  if (day.outOfMonth) classes.push('out-of-month')

  if (isToday(day.date)) classes.push('is-today')
  else if (isSelected(day.date)) classes.push('is-selected')

  return classes
}

function isToday(date) {
  const now = new Date()
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
}

function isSelected(date) {
  if (!props.selectedDate) return false
  return date.getFullYear() === props.selectedDate.getFullYear()
    && date.getMonth() === props.selectedDate.getMonth()
    && date.getDate() === props.selectedDate.getDate()
}

function onCellClick(day) {
  if (day.outOfMonth) return
  emit('select', day.date)
}
</script>

<style scoped>
.calendar-grid {
  background: var(--bg-white);
  padding: 8px 12px 4px;
  position: relative;
  overflow: hidden;
}

.week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--text-regular);
  margin-bottom: 8px;
  font-weight: 500;
}

.week-header .sunday-col { color: var(--cal-sunday-strong); }
.week-header .saturday-col { color: var(--cal-saturday-strong); }

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  font-size: var(--font-size-base);
  font-weight: 500;
  transition: background 0.15s;
}

.out-of-month {
  cursor: default;
}

/* 三档 × 三色相 */
.weekday-strong { color: var(--cal-weekday-strong); }
.weekday-weak   { color: var(--cal-weekday-weak); }
.weekday-faint  { color: var(--cal-weekday-faint); }

.sunday-strong  { color: var(--cal-sunday-strong); }
.sunday-weak    { color: var(--cal-sunday-weak); }
.sunday-faint   { color: var(--cal-sunday-faint); }

.saturday-strong { color: var(--cal-saturday-strong); }
.saturday-weak   { color: var(--cal-saturday-weak); }
.saturday-faint  { color: var(--cal-saturday-faint); }

/* 今日：蓝色圈 */
.is-today {
  box-shadow: inset 0 0 0 1.5px var(--cal-today-ring);
}

/* 选中（非今日）：灰色圈 */
.is-selected {
  box-shadow: inset 0 0 0 1.5px var(--cal-selected-ring);
}

/* 拉手条 */
.pull-handle {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--border-color);
  margin: 10px auto 4px;
}

/* 滑动动画 */
.days-grid {
  transition: transform 0.3s ease-out, opacity 0.3s ease-out;
}

.days-grid.slide-left {
  animation: slideLeft 0.3s ease-out;
}

.days-grid.slide-right {
  animation: slideRight 0.3s ease-out;
}

@keyframes slideLeft {
  0% { transform: translateX(100%); opacity: 0; }
  100% { transform: translateX(0); opacity: 1; }
}

@keyframes slideRight {
  0% { transform: translateX(-100%); opacity: 0; }
  100% { transform: translateX(0); opacity: 1; }
}
</style>
```

- [ ] **Step 2: 跑测试确认通过**

```bash
cd frontend && npx vitest run tests/components/snapledger/CalendarGrid.test.js
```

Expected: 全部测试通过。若有个别失败，修 bug 直到全绿。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/snapledger/CalendarGrid.vue
git commit -m "feat(snapledger): rewrite CalendarGrid with three-tier coloring"
```

---

## Task 6: 重写 Calendar.vue 页面

**Files:**
- Modify: `frontend/src/views/snapledger/Calendar.vue`（整文件替换）

- [ ] **Step 1: 覆盖写入新页面**

```vue
<template>
  <div class="calendar-page">
    <CalendarHeader
      :selected-date="selectedDate"
      @click-title="showMonthPicker = true"
    />

    <CalendarGrid
      :year="year"
      :month="month"
      :days="monthData?.days || []"
      :selected-date="selectedDate"
      @select="onDateSelect"
      @swipe="onSwipe"
    />

    <BudgetCard />

    <div class="day-records">
      <RecordList :records="dayRecords" @edit="goToEdit" />
    </div>

    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        v-model="selectedMonthValue"
        title="选择月份"
        :columns-type="['year', 'month']"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar, getRecordsByDate } from '@/api'
import { formatDateISO } from '@/utils/format'
import CalendarHeader from '@/components/snapledger/CalendarHeader.vue'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'
import BudgetCard from '@/components/snapledger/BudgetCard.vue'
import RecordList from '@/components/snapledger/RecordList.vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const router = useRouter()

const activeTab = ref(-1)
const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const selectedDate = ref(new Date())
const monthData = ref(null)
const dayRecords = ref([])
const showMonthPicker = ref(false)
const selectedMonthValue = ref([year.value.toString(), month.value.toString()])

onMounted(async () => {
  await loadMonthData()
  await loadDayRecords()
})

watch([year, month], loadMonthData)

async function loadMonthData() {
  try {
    const res = await getMonthCalendar(year.value, month.value)
    monthData.value = res
  } catch (e) {
    console.error('Failed to load calendar:', e)
  }
}

async function loadDayRecords() {
  try {
    const dateStr = formatDateISO(selectedDate.value)
    const res = await getRecordsByDate(dateStr)
    dayRecords.value = res || []
  } catch (e) {
    console.error('Failed to load records:', e)
  }
}

function prevMonth() {
  if (month.value === 1) { month.value = 12; year.value-- }
  else month.value--
  updateSelectedMonthValue()
}

function nextMonth() {
  if (month.value === 12) { month.value = 1; year.value++ }
  else month.value++
  updateSelectedMonthValue()
}

function updateSelectedMonthValue() {
  selectedMonthValue.value = [year.value.toString(), month.value.toString()]
}

function onMonthConfirm({ selectedValues }) {
  year.value = parseInt(selectedValues[0])
  month.value = parseInt(selectedValues[1])
  updateSelectedMonthValue()
  showMonthPicker.value = false
}

function onDateSelect(date) {
  selectedDate.value = date
  // 如果选的日期在当前浏览月份之外（跨月填充位被点击时不会触发，
  // 但 van-date-picker 切月后可能有其他来源），这里保持选中日期与网格解耦
  loadDayRecords()
}

function onSwipe(direction) {
  if (direction === 'prev') prevMonth()
  else nextMonth()
  // 注意：切月时不改 selectedDate，标题保持显示原选中日期（符合 spec）
}

function goToEdit(record) {
  router.push(`/snap/edit/${record.id}`)
}
</script>

<style scoped>
.calendar-page {
  min-height: 100vh;
  background: var(--bg-color);
  padding-bottom: 80px;
}

.day-records {
  margin-top: 8px;
}
</style>
```

**关键变化**：
- 去掉了原 `.month-nav`（被 `CalendarHeader` 替代）
- 去掉了"当日日期"的 `van-cell`（标题已在 Header 显示）
- 切月份时不重置 `selectedDate`（保留跨月选中）

- [ ] **Step 2: 启动 dev 服务手工验证**

Run:
```bash
cd frontend && npm run dev
```

手工测试点：
- 打开 `/snap/calendar` 页面显示新样式
- 当前日期蓝色圆圈，周六绿色/周日红色
- 有记录日期比无记录深色
- 4/1 显示为 "4月" 而非 "01"
- 上月末日期（3/29-31）最浅灰色，点击无响应
- 点 🔔 → Toast「通知功能开发中」
- 点 🔍 → Toast「搜索功能开发中」
- 点顶部日期 → 月份选择器弹出
- 左右滑网格切月，标题不变
- 切到 5 月后回到 4 月，4/23 仍然选中
- 跨年：切到 2026/1，前月填充是 2025/12 的日期
- 点预算卡 → 跳 `/snap/budget`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/snapledger/Calendar.vue
git commit -m "feat(snapledger): redesign Calendar page to Moze date-view style"
```

---

## Task 7: 运行全量测试

- [ ] **Step 1: 跑全部前端测试**

```bash
cd frontend && npx vitest run
```

Expected: 全部测试通过（新增 CalendarGrid tests + 已有 CategoryGrid/CategoryPicker/RecordForm/AddRecord tests）

- [ ] **Step 2: Lint 检查**

```bash
cd frontend && npm run lint
```

Expected: 无错误（或只有无关文件的已有 warning）

- [ ] **Step 3: 若有问题修复并提交**

```bash
git add <修改文件>
git commit -m "fix(snapledger): address lint/test issues from calendar redesign"
```

---

## Verification Checklist

完成以下所有项目才算实施完成：

- [ ] CSS 变量已加（含 dark mode）
- [ ] `CalendarHeader.vue` 已创建
- [ ] `BudgetCard.vue` 已创建，点击跳 `/snap/budget`
- [ ] `CalendarGrid.vue` 已重写，填充/着色/月首标记/拉手条全部满足
- [ ] `CalendarGrid.test.js` 全部通过
- [ ] `Calendar.vue` 已重写，组装新组件
- [ ] 手工验证所有交互点（🔔 toast / 🔍 toast / 标题切月 / 滑动切月 / 跨月保留选中 / 跨年边界）
- [ ] `npx vitest run` 全绿
- [ ] `npm run lint` 无错

---

## Rollback

如需回滚：
```bash
git revert <task-N-commit-sha>
```
每个 Task 独立 commit，可按需逆向回滚。CSS 变量即便保留也不会影响其他页面（命名空间 `--cal-*`）。
