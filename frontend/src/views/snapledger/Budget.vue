<template>
  <div class="budget-page">
    <van-nav-bar title="预算设置" left-arrow @click-left="$router.back()" />

    <div class="budget-content">
      <!-- Month selector -->
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

      <!-- Current budget info -->
      <van-card class="budget-card">
        <div class="budget-info" v-if="budget">
          <div class="info-row">
            <span class="label">预算金额</span>
            <van-field
              v-model.number="budget.amount"
              type="number"
              placeholder="0.00"
              :border="false"
            />
          </div>
          <div class="info-row">
            <span class="label">已支出</span>
            <span class="value">¥{{ (budget.spent || 0).toFixed(2) }}</span>
          </div>
          <div class="info-row remaining">
            <span class="label">剩余</span>
            <span class="value" :class="{ 'over-budget': budget.overBudget, 'remaining': !budget.overBudget }">
              ¥{{ (budget.remaining || 0).toFixed(2) }}
            </span>
          </div>
        </div>
      </van-card>

      <!-- Usage progress bar -->
      <div v-if="budget && budget.amount > 0" class="progress-card">
        <div class="progress-label">
          <span>预算使用进度</span>
          <span>{{ progressPercent.toFixed(1) }}%</span>
        </div>
        <van-progress
          :percentage="progressPercent"
          :color="progressColor"
          :stroke-width="8"
        />
      </div>

      <div v-if="budget && budget.overBudget" class="over-notice">
        <van-notice-bar
          color="#ee0a24"
          background="#fff1f0"
          left-icon="warning-o"
          text="⚠️ 当前已超出预算，请控制消费"
        />
      </div>

      <!-- Save button -->
      <div class="action-button">
        <van-button type="primary" block @click="handleSave" :loading="saving">
          保存预算
        </van-button>
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

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getBudget, setBudget } from '@/api/snapledger/budget'
import { showToast, showSuccessToast } from 'vant'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const route = useRoute()
const showMonthPicker = ref(false)
const saving = ref(false)
const activeTab = ref(3) // 预算页对应 tabbar 第 4 位（索引 3）

const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const currentDate = computed(() => {
  return `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`
})
const selectedDate = ref([currentYear.value.toString(), currentMonth.value.toString()])

const budget = ref(null)

const progressPercent = computed(() => {
  if (!budget.value || !budget.value.amount || budget.value.amount === 0) {
    return 0
  }
  const percent = (budget.value.spent / budget.value.amount) * 100
  return Math.min(percent, 100)
})

const progressColor = computed(() => {
  if (!budget.value) return '#1989fa'
  if (budget.value.overBudget) return '#ee0a24'
  if (progressPercent.value > 80) return '#ff976a'
  return '#1989fa'
})

async function loadBudget() {
  try {
    const res = await getBudget(currentYear.value, currentMonth.value)
    // axios 拦截器已解包，res 直接是数据
    budget.value = res
  } catch (e) {
    showToast('加载预算失败: ' + e.message)
  }
}

async function handleSave() {
  if (!budget.value || !budget.value.amount || budget.value.amount <= 0) {
    showToast('请输入有效的预算金额')
    return
  }

  saving.value = true
  try {
    await setBudget({
      year: currentYear.value,
      month: currentMonth.value,
      amount: budget.value.amount
    })
    await loadBudget()
    showSuccessToast('保存成功')
  } catch (e) {
    showToast('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function onMonthConfirm({ selectedValues }) {
  currentYear.value = parseInt(selectedValues[0])
  currentMonth.value = parseInt(selectedValues[1])
  selectedDate.value = [currentYear.value.toString(), currentMonth.value.toString()]
  showMonthPicker.value = false
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
  loadBudget()
})
</script>

<style scoped>
.budget-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 80px;
}

.budget-content {
  padding: 16px;
}

.month-selector {
  margin-bottom: 16px;
}

.budget-card {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row .label {
  color: #646566;
  width: 80px;
}

.info-row .value {
  flex: 1;
  text-align: right;
  color: #323233;
  font-weight: 500;
}

.remaining .value.over-budget {
  color: #ee0a24;
  font-weight: 500;
}

.remaining .value.remaining {
  color: #07c160;
  font-weight: 500;
}

.progress-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #323233;
}

.over-notice {
  margin-bottom: 16px;
}

.action-button {
  padding: 0 16px;
}
</style>
