<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="datetime-picker">
      <div class="picker-header">
        <span class="picker-cancel" @click="visible = false">取消</span>
        <span class="picker-title">选择日期时间</span>
        <span class="picker-confirm" @click="confirm">确定</span>
      </div>
      <van-picker-group :tabs="['日期', '时间']" :tab-index="tabIndex" @update:tab-index="tabIndex = $event">
        <van-date-picker
          v-model="dateValue"
          :columns-type="['year', 'month', 'day']"
          :min-date="minDate"
          :max-date="maxDate"
        />
        <van-time-picker
          v-model="timeValue"
          :columns-type="['hour', 'minute', 'second']"
        />
      </van-picker-group>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: String, default: '' } // 格式: YYYY-MM-DD HH:mm:ss
})

const emit = defineEmits(['update:show', 'update:modelValue'])

const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

const tabIndex = ref(0)

const minDate = new Date(2000, 0, 1)
const maxDate = new Date(2100, 11, 31)

// 解析日期时间字符串
function parseDateTime(str) {
  if (!str) {
    const now = new Date()
    return {
      date: [String(now.getFullYear()), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')],
      time: [String(now.getHours()).padStart(2, '0'), String(now.getMinutes()).padStart(2, '0'), '00']
    }
  }
  const [datePart, timePart] = str.split(' ')
  const dateArr = datePart?.split('-') || []
  const timeArr = timePart?.split(':') || ['00', '00', '00']

  return {
    date: dateArr.length === 3 ? dateArr : [String(new Date().getFullYear()), '01', '01'],
    time: [timeArr[0] || '00', timeArr[1] || '00', timeArr[2] || '00']
  }
}

const dateValue = ref(['2024', '01', '01'])
const timeValue = ref(['00', '00', '00'])

// 初始化值
watch(() => props.show, (show) => {
  if (show) {
    const parsed = parseDateTime(props.modelValue)
    dateValue.value = parsed.date
    timeValue.value = parsed.time
  }
})

function confirm() {
  const dateStr = dateValue.value.join('-')
  const timeStr = timeValue.value.join(':')
  emit('update:modelValue', `${dateStr} ${timeStr}`)
  visible.value = false
}
</script>

<style scoped>
.datetime-picker {
  background: #FFFFFF;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #EEEEEE;
}

.picker-title {
  font-size: 16px;
  font-weight: 500;
  color: #333333;
}

.picker-cancel {
  font-size: 14px;
  color: #999999;
  cursor: pointer;
}

.picker-confirm {
  font-size: 14px;
  color: #1890FF;
  cursor: pointer;
}
</style>
