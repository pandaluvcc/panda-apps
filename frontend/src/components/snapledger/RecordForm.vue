<template>
  <div class="record-form">
    <!-- 金额输入区域 -->
    <AmountInput v-model="form.amount" />

    <!-- 详细信息区域 -->
    <div class="detail-section">
      <div class="form-row">
        <div class="form-field">
          <label class="field-label">名称</label>
          <input
            v-model="form.name"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">账户</label>
          <div class="field-input field-select" @click="showAccountPicker = true">
            {{ form.account || '请选择' }}
          </div>
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">项目</label>
          <input
            v-model="form.project"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">商家</label>
          <input
            v-model="form.merchant"
            class="field-input"
            placeholder="可选"
          />
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">次数</label>
          <input
            v-model.number="form.count"
            type="number"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">日期</label>
          <div class="field-input field-select" @click="showDatePicker = true">
            {{ form.date }}
          </div>
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">时间</label>
          <div class="field-input field-select" @click="showTimePicker = true">
            {{ form.time || '请选择' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 标签显示区域 -->
    <div class="tag-section" @click="showTagPicker = true">
      <template v-if="form.tags && form.tags.length > 0">
        <van-tag
          v-for="tag in form.tags"
          :key="tag"
          type="primary"
          size="medium"
          class="tag-chip"
        >
          #{{ tag }}
        </van-tag>
      </template>
      <span v-else class="tag-placeholder">#标签</span>
    </div>

    <!-- 备注输入区域 -->
    <div class="remark-section">
      <label class="field-label">备注</label>
      <textarea
        v-model="form.description"
        class="remark-input"
        placeholder="备注"
        rows="2"
      ></textarea>
    </div>

    <!-- 账户选择器 -->
    <van-popup v-model:show="showAccountPicker" position="bottom" round>
      <van-picker
        :columns="accountColumns"
        @confirm="onAccountConfirm"
        @cancel="showAccountPicker = false"
      />
    </van-popup>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 时间选择器 -->
    <van-popup v-model:show="showTimePicker" position="bottom" round>
      <van-time-picker
        v-model="selectedTime"
        @confirm="onTimeConfirm"
        @cancel="showTimePicker = false"
      />
    </van-popup>

    <!-- 标签选择器 -->
    <TagPicker
      v-model:show="showTagPicker"
      v-model="form.tags"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getAccounts } from '@/api'
import { formatDateISO } from '@/utils/format'
import AmountInput from './AmountInput.vue'
import TagPicker from './TagPicker.vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: formatDateISO(new Date()),
  time: '',
  name: '',
  project: '',
  merchant: '',
  count: null,
  tags: [],
  description: ''
})

const accounts = ref([])
const showAccountPicker = ref(false)
const showDatePicker = ref(false)
const showTimePicker = ref(false)
const showTagPicker = ref(false)

// 根据 form.date 初始化日期选择器
const selectedDate = computed({
  get: () => {
    const parts = form.value.date?.split('-') || []
    return parts.length === 3 ? parts : [String(new Date().getFullYear()), '01', '01']
  },
  set: (val) => { /* 由 onDateConfirm 处理 */ }
})

// 根据 form.time 初始化时间选择器
const selectedTime = computed({
  get: () => {
    const parts = form.value.time?.split(':') || []
    return parts.length >= 2 ? [parts[0], parts[1]] : ['00', '00']
  },
  set: (val) => { /* 由 onTimeConfirm 处理 */ }
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

// 同步外部值
watch(() => props.modelValue, (val) => {
  if (val) {
    form.value = { ...form.value, ...val }
  }
}, { immediate: true, deep: true })

onMounted(async () => {
  try {
    const res = await getAccounts()
    accounts.value = res || []
  } catch (e) {
    console.error('Failed to load accounts:', e)
  }
})

function onAccountConfirm({ selectedOptions }) {
  form.value.account = selectedOptions[0].text
  showAccountPicker.value = false
}

function onDateConfirm({ selectedValues }) {
  form.value.date = selectedValues.join('-')
  showDatePicker.value = false
}

function onTimeConfirm({ selectedValues }) {
  form.value.time = `${selectedValues[0]}:${selectedValues[1]}`
  showTimePicker.value = false
}
</script>

<style scoped>
.record-form {
  background: #F7F8FA;
}

.detail-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 14px;
  color: #666666;
}

.field-input {
  font-size: 14px;
  color: #333333;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
}

.field-select {
  cursor: pointer;
}

.tag-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  cursor: pointer;
}

.tag-placeholder {
  font-size: 14px;
  color: #1890FF;
}

.tag-chip {
  cursor: pointer;
}

.remark-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
  border-radius: 8px;
  min-height: 80px;
}

.remark-input {
  width: 100%;
  margin-top: 8px;
  font-size: 14px;
  color: #333333;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
  resize: none;
  box-sizing: border-box;
}
</style>
