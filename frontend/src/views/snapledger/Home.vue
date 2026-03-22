<template>
  <div class="snap-home">
    <!-- 顶部概览 -->
    <div class="overview">
      <div class="month-selector" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth }}月
        <van-icon name="arrow-down" />
      </div>
      <div class="summary">
        <div class="item">
          <span class="label">收入</span>
          <span class="value income">+¥{{ totalIncome.toFixed(2) }}</span>
        </div>
        <div class="item">
          <span class="label">支出</span>
          <span class="value expense">-¥{{ totalExpense.toFixed(2) }}</span>
        </div>
        <div class="item">
          <span class="label">结余</span>
          <span class="value">{{ totalBalance >= 0 ? '+' : '' }}¥{{ totalBalance.toFixed(2) }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="quick-actions">
      <van-button type="primary" block to="/snap/add">
        <van-icon name="plus" /> 记一笔
      </van-button>
      <van-button type="default" block to="/snap/scan" class="scan-btn">
        <van-icon name="photograph" /> 图片记账
      </van-button>
    </div>


    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMonthCalendar } from '@/api'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const router = useRouter()
const activeTab = ref(0)
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const showMonthPicker = ref(false)

const monthData = ref(null)

const totalIncome = computed(() => monthData.value?.totalIncome || 0)
const totalExpense = computed(() => monthData.value?.totalExpense || 0)
const totalBalance = computed(() => totalIncome.value - totalExpense.value)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  try {
    const calendarRes = await getMonthCalendar(currentYear.value, currentMonth.value)
    // axios 拦截器已解包，res 直接是数据
    monthData.value = calendarRes
  } catch (e) {
    console.error('Failed to load data:', e)
  }
}
</script>

<style scoped>
.snap-home {
  padding-bottom: 60px;
}

.overview {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  border-radius: 0 0 20px 20px;
}

.month-selector {
  text-align: center;
  font-size: 16px;
  margin-bottom: 16px;
}

.summary {
  display: flex;
  justify-content: space-around;
}

.summary .item {
  text-align: center;
}

.summary .label {
  display: block;
  font-size: 12px;
  opacity: 0.8;
}

.summary .value {
  display: block;
  font-size: 18px;
  font-weight: bold;
  margin-top: 4px;
}

.summary .income { color: #90EE90; }
.summary .expense { color: #FFB6C1; }

.quick-actions {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scan-btn {
  color: #667eea;
  border-color: #667eea;
}
</style>
