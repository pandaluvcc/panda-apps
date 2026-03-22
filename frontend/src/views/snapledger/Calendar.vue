<template>
  <div class="calendar-page">
    <van-nav-bar title="日历" />

    <!-- 月份切换 -->
    <div class="month-nav">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="current-month">{{ year }}年{{ month }}月</span>
      <van-icon name="arrow-right" @click="nextMonth" />
    </div>

    <!-- 日历网格 -->
    <CalendarGrid
      :year="year"
      :month="month"
      :days="monthData?.days || []"
      :selected-date="selectedDate"
      @select="onDateSelect"
    />

    <!-- 当日记录列表 -->
    <div class="day-records">
      <van-cell-group inset>
        <van-cell :title="formatDate(selectedDate)" />
      </van-cell-group>
      <RecordList :records="dayRecords" @edit="goToEdit" />
    </div>

    <!-- 添加按钮 -->
    <van-floating-bubble
      icon="plus"
      @click="$router.push('/snap/add')"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar, getRecordsByDate } from '@/api'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'
import RecordList from '@/components/snapledger/RecordList.vue'

const router = useRouter()

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const selectedDate = ref(new Date())
const monthData = ref(null)
const dayRecords = ref([])

onMounted(async () => {
  await loadMonthData()
  await loadDayRecords()
})

watch([year, month], loadMonthData)

async function loadMonthData() {
  try {
    const res = await getMonthCalendar(year.value, month.value)
    // axios 拦截器已解包，res 直接是数据
    monthData.value = res
  } catch (e) {
    console.error('Failed to load calendar:', e)
  }
}

async function loadDayRecords() {
  try {
    const dateStr = selectedDate.value.toISOString().split('T')[0]
    const res = await getRecordsByDate(dateStr)
    // axios 拦截器已解包，res 直接是数据
    dayRecords.value = res || []
  } catch (e) {
    console.error('Failed to load records:', e)
  }
}

function prevMonth() {
  if (month.value === 1) {
    month.value = 12
    year.value--
  } else {
    month.value--
  }
}

function nextMonth() {
  if (month.value === 12) {
    month.value = 1
    year.value++
  } else {
    month.value++
  }
}

function onDateSelect(date) {
  selectedDate.value = date
  loadDayRecords()
}

function formatDate(date) {
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function goToEdit(record) {
  router.push(`/snap/edit/${record.id}`)
}
</script>

<style scoped>
.calendar-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 80px;
}

.month-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: white;
}

.current-month {
  font-size: 16px;
  font-weight: bold;
}

.day-records {
  margin-top: 12px;
}
</style>
