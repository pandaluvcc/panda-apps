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

const calendarDays = computed(() => {
  const firstDay = new Date(props.year, props.month - 1, 1)
  const lastDay = new Date(props.year, props.month, 0)
  const startPadding = firstDay.getDay()
  const endPadding = 6 - lastDay.getDay()

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

  let hue
  if (day.dayOfWeek === 0) hue = 'sunday'
  else if (day.dayOfWeek === 6) hue = 'saturday'
  else hue = 'weekday'

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

.weekday-strong { color: var(--cal-weekday-strong); }
.weekday-weak   { color: var(--cal-weekday-weak); }
.weekday-faint  { color: var(--cal-weekday-faint); }

.sunday-strong  { color: var(--cal-sunday-strong); }
.sunday-weak    { color: var(--cal-sunday-weak); }
.sunday-faint   { color: var(--cal-sunday-faint); }

.saturday-strong { color: var(--cal-saturday-strong); }
.saturday-weak   { color: var(--cal-saturday-weak); }
.saturday-faint  { color: var(--cal-saturday-faint); }

.is-today {
  box-shadow: inset 0 0 0 1.5px var(--cal-today-ring);
}

.is-selected {
  box-shadow: inset 0 0 0 1.5px var(--cal-selected-ring);
}

.pull-handle {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--border-color);
  margin: 10px auto 4px;
}

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
