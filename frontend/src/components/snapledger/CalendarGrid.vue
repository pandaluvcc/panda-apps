<template>
  <div class="calendar-grid">
    <!-- 星期标题 -->
    <div class="week-header">
      <span class="weekend">日</span>
      <span>一</span>
      <span>二</span>
      <span>三</span>
      <span>四</span>
      <span>五</span>
      <span class="weekend">六</span>
    </div>

    <!-- 日期格子 -->
    <div class="days-grid">
      <div
        v-for="(day, index) in calendarDays"
        :key="index"
        class="day-cell"
        :class="{
          'selected': isSelected(day.date),
          'has-records': day.recordCount > 0,
          'today': isToday(day.date),
          'empty': !day.date
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
import { computed } from 'vue'

const props = defineProps({
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  days: { type: Array, default: () => [] },
  selectedDate: { type: Date, default: null }
})

defineEmits(['select'])

const calendarDays = computed(() => {
  const firstDay = new Date(props.year, props.month - 1, 1)
  const lastDay = new Date(props.year, props.month, 0)
  const startPadding = firstDay.getDay()

  const result = []

  // 前置空白
  for (let i = 0; i < startPadding; i++) {
    result.push({ date: null, income: 0, expense: 0, recordCount: 0 })
  }

  // 当月日期
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const date = new Date(props.year, props.month - 1, d)
    const dayData = props.days.find(day => {
      const dayDate = new Date(day.date)
      return dayDate.getDate() === d
    }) || { date: date.toISOString().split('T')[0], income: 0, expense: 0, recordCount: 0 }
    result.push({ ...dayData, date })
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
}

.week-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.week-header .weekend {
  color: #ee0a24;
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

.day-cell.selected {
  background: #e8f4ff;
}

.day-cell.today .day-number {
  color: #1989fa;
  font-weight: bold;
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
  font-size: 10px;
  margin-top: 2px;
}

.income { color: #07c160; }
.expense { color: #ee0a24; }
</style>
