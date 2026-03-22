<template>
  <div class="calendar-page">
    <!-- 月份切换 -->
    <div class="month-nav">
      <div class="month-selector" @click="showMonthPicker = true">
        <span class="current-month">{{ year }}年{{ month }}月</span>
        <van-icon name="arrow-down" class="dropdown-icon" />
      </div>
    </div>

    <!-- 日历网格 -->
    <CalendarGrid
      :year="year"
      :month="month"
      :days="monthData?.days || []"
      :selected-date="selectedDate"
      @select="onDateSelect"
      @swipe="onSwipe"
    />

    <!-- 当日记录列表 -->
    <div class="day-records">
      <van-cell-group inset>
        <van-cell :title="formatDate(selectedDate)" />
      </van-cell-group>
      <RecordList :records="dayRecords" @edit="goToEdit" />
    </div>

    <!-- 月份选择器弹窗 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        v-model="selectedMonthValue"
        title="选择月份"
        :columns-type="['year', 'month']"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar, getRecordsByDate } from '@/api'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'
import RecordList from '@/components/snapledger/RecordList.vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const router = useRouter()

const activeTab = ref(-1) // 日历页不在底部导航中
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
  updateSelectedMonthValue()
}

function nextMonth() {
  if (month.value === 12) {
    month.value = 1
    year.value++
  } else {
    month.value++
  }
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
  loadDayRecords()
}

function onSwipe(direction) {
  if (direction === 'prev') {
    prevMonth()
  } else {
    nextMonth()
  }
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
  justify-content: center;
  align-items: center;
  padding: 16px;
  background: white;
}

.month-selector {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.month-selector:active {
  background-color: #f7f8fa;
}

.current-month {
  font-size: 16px;
  font-weight: bold;
  color: #323233;
}

.dropdown-icon {
  font-size: 12px;
  color: #969799;
}

.day-records {
  margin-top: 12px;
}
</style>
