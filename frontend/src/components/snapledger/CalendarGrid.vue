<template>
  <div
    class="calendar-grid"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
  >
    <!-- 星期标题 -->
    <div class="week-header">
      <span>周日</span>
      <span>周一</span>
      <span>周二</span>
      <span>周三</span>
      <span>周四</span>
      <span>周五</span>
      <span>周六</span>
    </div>

    <!-- 日期格子 -->
    <div class="days-grid" :class="transitionClass">
      <div
        v-for="(day, index) in calendarDays"
        :key="index"
        class="day-cell"
        :class="{
          'selected': isSelected(day.date),
          'has-records': day.recordCount > 0,
          'today': isToday(day.date),
          'empty': !day.date,
          'sunday': day.date && day.dayOfWeek === 0,
          'saturday': day.date && day.dayOfWeek === 6
        }"
        @click="day.date && $emit('select', day.date)"
      >
        <span class="day-number">{{ day.date ? day.date.getDate() : '' }}</span>
        <span v-if="day.recordCount > 0" class="dot"></span>
        <div v-if="day.income > 0 || day.expense > 0" class="day-amounts">
          <span v-if="day.income > 0" class="income">+{{ formatShort(day.income) }}</span>
          <span v-if="day.expense > 0" class="expense">-{{ formatShort(day.expense) }}</span>
        </div>
      </div>
    </div>
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

  // 如果是水平滑动且距离较大，阻止浏览器默认行为（防止触发返回）
  if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 10) {
    e.preventDefault()
  }
}

function onTouchEnd(e) {
  const touchEndX = e.changedTouches[0].clientX
  const touchEndY = e.changedTouches[0].clientY
  const deltaX = touchEndX - touchStartX.value
  const deltaY = touchEndY - touchStartY.value

  // 只有水平滑动距离大于 50px 且垂直滑动距离较小时才触发
  if (Math.abs(deltaX) > 50 && Math.abs(deltaY) < 30) {
    if (deltaX > 0) {
      // 向右滑 = 上一月
      transitionClass.value = 'slide-right'
      setTimeout(() => { transitionClass.value = '' }, 300)
      emit('swipe', 'prev')
    } else {
      // 向左滑 = 下一月
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

  const result = []

  // 前置空白
  for (let i = 0; i < startPadding; i++) {
    result.push({ date: null, income: 0, expense: 0, recordCount: 0, dayOfWeek: null })
  }

  // 当月日期
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(props.year, props.month - 1, d)
    const dayOfWeek = date.getDay()
    const dayData = props.days.find(day => {
      const dayDate = new Date(day.date)
      return dayDate.getDate() === d
    }) || { date: date.toISOString().split('T')[0], income: 0, expense: 0, recordCount: 0 }
    result.push({ ...dayData, date, dayOfWeek })
  }

  return result
})

function isSelected(date) {
  if (!date || !props.selectedDate) return false
  return date.toDateString() === props.selectedDate.toDateString()
}

function isToday(date) {
  if (!date) return false
  return date.toDateString() === new Date().toDateString()
}

function formatShort(amount) {
  if (amount >= 10000) {
    return (amount / 10000).toFixed(1) + 'w'
  }
  if (amount >= 1000) {
    return (amount / 1000).toFixed(1) + 'k'
  }
  return amount.toFixed(0)
}
</script>

<style scoped>
.calendar-grid {
  background: #fff;
  padding: 12px;
  position: relative;
  overflow: hidden;
}

.week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  color: #333;
  margin-bottom: 8px;
  font-weight: 500;
}

.week-header span:first-child {
  color: #ee0a24;
}

.week-header span:last-child {
  color: #07c160;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
}

.day-cell.empty {
  cursor: default;
}

/* 未记账的日期 - 高透明度 */
.day-cell:not(.empty):not(.has-records) {
  opacity: 0.4;
}

/* 已记账的日期 - 正常透明度，黑色字体 */
.day-cell.has-records .day-number {
  color: #333;
}

/* 周日红色 */
.day-cell.sunday .day-number {
  color: #ee0a24;
}

/* 周六绿色 */
.day-cell.saturday .day-number {
  color: #07c160;
}

/* 选中状态 */
.day-cell.selected {
  background: #1989fa;
}

.day-cell.selected .day-number {
  color: white;
}

.day-cell.selected .income {
  color: #b5f5b5;
}

.day-cell.selected .expense {
  color: #ffa39e;
}

/* 今日 */
.day-cell.today .day-number {
  font-weight: bold;
}

.day-cell.today:not(.selected) .day-number {
  color: #1989fa;
}

/* 已记账日期覆盖周日/周六的颜色（保持黑色） */
.day-cell.has-records:not(.selected) .day-number {
  color: #333;
}

/* 但如果既是已记账又是周日/周六，保持红/绿色 */
.day-cell.has-records.sunday:not(.selected) .day-number {
  color: #ee0a24;
}

.day-cell.has-records.saturday:not(.selected) .day-number {
  color: #07c160;
}

.day-cell.has-records .dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 4px;
  height: 4px;
  background: #1989fa;
  border-radius: 50%;
}

.day-number {
  font-size: 14px;
}

.day-amounts {
  font-size: 11px;
  margin-top: 2px;
  line-height: 1.2;
}

.income { color: #07c160; }
.expense { color: #ee0a24; }

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
