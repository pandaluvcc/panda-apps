<template>
  <div class="stats-page">
    <van-nav-bar title="统计报表" left-arrow @click-left="$router.back()" />

    <div class="stats-content">
      <!-- Year/Month selector -->
      <div class="month-selector">
        <van-field
          v-model="currentDate"
          is-link
          readonly
          label="月份"
          placeholder="选择月份"
          @click="showMonthPicker = true"
        />
      </div>

      <!-- Overview card -->
      <van-card class="overview-card">
        <div class="overview-row">
          <div class="overview-item income">
            <div class="label">收入</div>
            <div class="value">+¥{{ (stats.totalIncome || 0).toFixed(2) }}</div>
          </div>
          <div class="overview-item expense">
            <div class="label">支出</div>
            <div class="value">-¥{{ (stats.totalExpense || 0).toFixed(2) }}</div>
          </div>
        </div>
        <div class="overview-row balance">
          <div class="label">结余</div>
          <div class="value" :class="{ 'positive': stats.balance >= 0, 'negative': stats.balance < 0 }">
            {{ stats.balance > 0 ? '+' : '' }}¥{{ (stats.balance || 0).toFixed(2) }}
          </div>
        </div>
      </van-card>

      <!-- Budget info if exists -->
      <div v-if="budget && budget.amount > 0" class="budget-card">
        <van-cell-group inset>
          <van-cell title="本月预算">
            <template #right-icon>
              <span class="budget-amount">¥{{ budget.amount.toFixed(2) }}</span>
            </template>
          </van-cell>
          <van-cell title="已支出">
            <template #right-icon>
              <span>¥{{ budget.spent.toFixed(2) }}</span>
            </template>
          </van-cell>
          <van-cell title="剩余">
            <template #right-icon>
              <span :class="{ 'over-budget': budget.overBudget, 'remaining': !budget.overBudget }">
                ¥{{ (budget.remaining || 0).toFixed(2) }}
              </span>
            </template>
          </van-cell>
        </van-cell-group>
        <div v-if="budget.overBudget" class="over-budget-notice">
          <van-notice-bar color="#ee0a24" background="#fff1f0" left-icon="warning-o" text="⚠️ 已超出本月预算" />
        </div>
      </div>

      <!-- Expense category pie chart -->
      <div v-if="stats.categoryStats && stats.categoryStats.length > 0" class="chart-card">
        <h3 class="chart-title">支出分类占比</h3>
        <div class="chart-container">
          <canvas id="categoryChart" width="300" height="300"></canvas>
        </div>

        <van-cell-group inset class="category-list">
          <van-cell
            v-for="cat in stats.categoryStats"
            :key="cat.categoryName"
            :title="cat.categoryName"
          >
            <template #right-icon>
              <span class="category-amount">¥{{ cat.amount.toFixed(2) }} ({{ cat.percentage.toFixed(1) }}%)</span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>
    </div>

    <!-- Month Picker -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        title="选择月份"
        type="month"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Chart } from 'chart.js'
import { getMonthlyStats } from '@/api/snapledger/stats'
import { getBudget } from '@/api/snapledger/budget'
import { showToast } from 'vant'

const route = useRoute()
const showMonthPicker = ref(false)

const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const currentDate = computed(() => {
  return `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`
})
const selectedDate = ref([currentYear.value.toString(), currentMonth.value.toString()])

const stats = ref({
  totalIncome: 0,
  totalExpense: 0,
  balance: 0,
  categoryStats: []
})

const budget = ref(null)
let categoryChart = null

async function loadStats() {
  try {
    const res = await getMonthlyStats(currentYear.value, currentMonth.value)
    stats.value = res.data || {
      totalIncome: 0,
      totalExpense: 0,
      balance: 0,
      categoryStats: []
    }
    renderChart()
  } catch (e) {
    showToast('加载统计失败: ' + e.message)
  }
}

async function loadBudget() {
  try {
    const res = await getBudget(currentYear.value, currentMonth.value)
    budget.value = res.data
  } catch (e) {
    console.error('Failed to load budget:', e)
  }
}

function renderChart() {
  const canvas = document.getElementById('categoryChart')
  if (!canvas || !stats.value.categoryStats || stats.value.categoryStats.length === 0) {
    return
  }

  if (categoryChart) {
    categoryChart.destroy()
  }

  const labels = stats.value.categoryStats.map(c => c.categoryName)
  const data = stats.value.categoryStats.map(c => c.amount.toNumber())
  const colors = [
    '#FF6384', '#36A2EB', '#FFCE56', '#4BC0C0', '#9966FF',
    '#FF9F40', '#FF6384', '#C9CBCF', '#4BC0C0', '#36A2EB'
  ]

  categoryChart = new Chart(canvas, {
    type: 'pie',
    data: {
      labels: labels,
      datasets: [{
        data: data,
        backgroundColor: colors,
        hoverBackgroundColor: colors
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom'
        }
      }
    }
  })
}

function onMonthConfirm({ selectedValues }) {
  currentYear.value = parseInt(selectedValues[0])
  currentMonth.value = parseInt(selectedValues[1])
  showMonthPicker.value = false
  loadStats()
  loadBudget()
}

onMounted(() => {
  const year = parseInt(route.query.year)
  const month = parseInt(route.query.month)
  if (year && month) {
    currentYear.value = year
    currentMonth.value = month
    selectedDate.value = [year.toString(), month.toString()]
  }
  loadStats()
  loadBudget()
})
</script>

<style scoped>
.stats-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.stats-content {
  padding: 16px;
}

.month-selector {
  margin-bottom: 16px;
}

.overview-card {
  margin-bottom: 16px;
}

.overview-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.overview-row:last-child {
  margin-bottom: 0;
  padding-top: 12px;
  border-top: 1px solid #eee;
}

.overview-item {
  flex: 1;
  text-align: center;
}

.overview-item .label {
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}

.overview-item .value {
  font-size: 18px;
  font-weight: bold;
}

.overview-item.income .value {
  color: #07c160;
}

.overview-item.expense .value {
  color: #ee0a24;
}

.balance .value.positive {
  color: #07c160;
}

.balance .value.negative {
  color: #ee0a24;
}

.budget-card {
  margin-bottom: 16px;
}

.budget-amount {
  color: #323233;
  font-weight: 500;
}

.over-budget {
  color: #ee0a24;
  font-weight: 500;
}

.remaining {
  color: #07c160;
  font-weight: 500;
}

.over-budget-notice {
  margin-top: 8px;
}

.chart-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.chart-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 500;
  color: #323233;
}

.chart-container {
  height: 300px;
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.category-list {
  margin-top: 8px;
}

.category-amount {
  color: #646566;
}
</style>
