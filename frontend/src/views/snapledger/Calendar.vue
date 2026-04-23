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
  loadDayRecords()
}

function onSwipe(direction) {
  if (direction === 'prev') prevMonth()
  else nextMonth()
  // 切月时不改 selectedDate，标题保持显示原选中日期（符合 spec）
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
