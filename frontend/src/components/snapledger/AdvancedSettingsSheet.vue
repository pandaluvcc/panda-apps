<template>
  <van-popup
    :show="visible"
    position="center"
    round
    :close-on-click-overlay="false"
    @update:show="val => $emit('update:visible', val)"
    class="advanced-popup"
  >
    <div class="sheet">
      <div class="sheet-title">高级设置</div>
      <div class="tabs">
        <div
          v-for="t in tabs"
          :key="t.value"
          class="tab"
          :class="{ active: mode === t.value }"
          @click="mode = t.value"
        >{{ t.label }}</div>
      </div>

      <!-- 单次 -->
      <div v-if="mode === 'single'" class="panel">
        <div class="row">
          <span class="row-label">入账方式</span>
          <span class="row-value row-value-disabled">立即入账</span>
        </div>
      </div>

      <!-- 周期 -->
      <div v-else-if="mode === 'recurring'" class="panel">
        <div class="row" @click="showIntervalPicker = true">
          <span class="row-label">区间</span>
          <span class="row-value">{{ intervalLabel }}</span>
        </div>
        <div
          v-if="config.intervalType === 'MONTHLY'"
          class="row"
          @click="showDayOfMonthPicker = true"
        >
          <span class="row-label">指定日期</span>
          <span class="row-value">{{ config.dayOfMonth }}号</span>
        </div>
        <div
          v-if="config.intervalType === 'WEEKLY'"
          class="row"
          @click="showDayOfWeekPicker = true"
        >
          <span class="row-label">指定日期</span>
          <span class="row-value">{{ weekdayLabel }}</span>
        </div>
        <div class="row">
          <span class="row-label">次数</span>
          <div class="periods-value">
            <label class="radio-inline">
              <input type="radio" v-model="periodMode" value="infinite" /> 无限期
            </label>
            <label class="radio-inline">
              <input type="radio" v-model="periodMode" value="finite" />
              <input
                v-model.number="config.totalPeriods"
                type="number"
                class="periods-input"
                :disabled="periodMode !== 'finite'"
                placeholder="N"
              /> 次
            </label>
          </div>
        </div>
        <div class="row">
          <span class="row-label">入账方式</span>
          <span class="row-value row-value-disabled">立即入账</span>
        </div>
      </div>

      <!-- 分期占位 -->
      <div v-else class="panel placeholder-panel">
        <p>分期功能开发中</p>
      </div>

      <div class="sheet-actions">
        <button class="btn btn-cancel" @click="onCancel">取消</button>
        <button class="btn btn-ok" @click="onConfirm">确定</button>
      </div>
    </div>

    <van-popup v-model:show="showIntervalPicker" position="bottom" round>
      <van-picker
        :columns="intervalOptions"
        @confirm="onIntervalConfirm"
        @cancel="showIntervalPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showDayOfMonthPicker" position="bottom" round>
      <van-picker
        :columns="dayOfMonthOptions"
        @confirm="({ selectedValues }) => { config.dayOfMonth = selectedValues[0]; showDayOfMonthPicker = false }"
        @cancel="showDayOfMonthPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showDayOfWeekPicker" position="bottom" round>
      <van-picker
        :columns="dayOfWeekOptions"
        @confirm="({ selectedValues }) => { config.dayOfWeek = selectedValues[0]; showDayOfWeekPicker = false }"
        @cancel="showDayOfWeekPicker = false"
      />
    </van-popup>
  </van-popup>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  visible: { type: Boolean, default: false },
  initialConfig: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:visible', 'confirm'])

const tabs = [
  { label: '单次', value: 'single' },
  { label: '周期', value: 'recurring' },
  { label: '分期', value: 'installment' }
]

const mode = ref(props.initialConfig.mode || 'single')
const periodMode = ref(props.initialConfig.totalPeriods ? 'finite' : 'infinite')

const config = reactive({
  intervalType: props.initialConfig.intervalType || 'MONTHLY',
  intervalValue: props.initialConfig.intervalValue || 1,
  dayOfMonth: props.initialConfig.dayOfMonth || new Date().getDate(),
  dayOfWeek: props.initialConfig.dayOfWeek || 1,
  totalPeriods: props.initialConfig.totalPeriods || null
})

watch(() => props.visible, (val) => {
  if (val) {
    mode.value = props.initialConfig.mode || 'single'
    periodMode.value = props.initialConfig.totalPeriods ? 'finite' : 'infinite'
    Object.assign(config, {
      intervalType: props.initialConfig.intervalType || 'MONTHLY',
      intervalValue: props.initialConfig.intervalValue || 1,
      dayOfMonth: props.initialConfig.dayOfMonth || new Date().getDate(),
      dayOfWeek: props.initialConfig.dayOfWeek || 1,
      totalPeriods: props.initialConfig.totalPeriods || null
    })
  }
})

const showIntervalPicker = ref(false)
const showDayOfMonthPicker = ref(false)
const showDayOfWeekPicker = ref(false)

const intervalOptions = [
  { text: '每日', value: 'DAILY' },
  { text: '每周', value: 'WEEKLY' },
  { text: '每月', value: 'MONTHLY' },
  { text: '每年', value: 'YEARLY' }
]

const dayOfMonthOptions = Array.from({ length: 31 }, (_, i) => ({
  text: `${i + 1}号`,
  value: i + 1
}))
const dayOfWeekOptions = [
  { text: '周一', value: 1 },
  { text: '周二', value: 2 },
  { text: '周三', value: 3 },
  { text: '周四', value: 4 },
  { text: '周五', value: 5 },
  { text: '周六', value: 6 },
  { text: '周日', value: 7 }
]

const intervalLabel = computed(() =>
  intervalOptions.find(o => o.value === config.intervalType)?.text || '每月'
)
const weekdayLabel = computed(() =>
  dayOfWeekOptions.find(o => o.value === config.dayOfWeek)?.text || '周一'
)

function onIntervalConfirm({ selectedValues }) {
  config.intervalType = selectedValues[0]
  showIntervalPicker.value = false
}

function onCancel() {
  emit('update:visible', false)
}

function onConfirm() {
  if (mode.value === 'installment') {
    showToast('分期功能开发中')
    return
  }
  if (mode.value === 'recurring' && periodMode.value === 'finite') {
    if (!config.totalPeriods || config.totalPeriods < 1) {
      showToast('请填写有效的次数')
      return
    }
  }
  const payload = {
    mode: mode.value,
    intervalType: config.intervalType,
    intervalValue: config.intervalValue,
    dayOfMonth: config.intervalType === 'MONTHLY' ? config.dayOfMonth : null,
    dayOfWeek: config.intervalType === 'WEEKLY' ? config.dayOfWeek : null,
    totalPeriods: periodMode.value === 'finite' ? config.totalPeriods : null
  }
  emit('confirm', payload)
  emit('update:visible', false)
}
</script>

<style scoped>
.advanced-popup { width: 80%; max-width: 360px; }
.sheet { padding: 20px 16px 16px; background: #fff; }
.sheet-title { text-align: center; font-size: 17px; font-weight: 600; color: #1a1a1a; margin-bottom: 16px; }
.tabs { display: flex; border-bottom: 1px solid #ebedf0; margin-bottom: 12px; }
.tab {
  flex: 1; text-align: center; padding: 10px 0; font-size: 15px; color: #666;
  border-bottom: 2px solid transparent;
}
.tab.active { color: #409eff; font-weight: 500; border-bottom-color: #4aa9ff; }
.panel { min-height: 120px; padding: 4px 0 12px; }
.placeholder-panel { display: flex; align-items: center; justify-content: center; color: #999; }
.row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 4px; border-bottom: 1px solid #f2f3f5;
}
.row-label { color: #333; font-size: 14px; }
.row-value { color: #1a1a1a; font-size: 14px; }
.row-value-disabled { color: #999; }
.periods-value { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }
.radio-inline { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: #333; }
.periods-input {
  width: 48px; padding: 2px 4px; border: 1px solid #dcdfe6; border-radius: 4px;
  font-size: 13px; text-align: center;
}
.sheet-actions { display: flex; gap: 10px; margin-top: 12px; }
.btn { flex: 1; padding: 10px; border-radius: 8px; font-size: 15px; border: none; cursor: pointer; }
.btn-cancel { background: #f2f3f5; color: #666; }
.btn-ok { background: #409eff; color: #fff; }
</style>
