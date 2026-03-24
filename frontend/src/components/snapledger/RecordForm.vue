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
          <label class="field-label">日期时间</label>
          <div class="field-input field-select" @click="showDateTimePicker = true">
            {{ displayDateTime }}
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

    <!-- 日期时间选择器 -->
    <DateTimePicker
      v-model:show="showDateTimePicker"
      v-model="dateTimeValue"
    />

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
import DateTimePicker from './DateTimePicker.vue'

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
const showDateTimePicker = ref(false)
const showTagPicker = ref(false)

// 日期时间显示值
const displayDateTime = computed(() => {
  if (form.value.date) {
    return form.value.time ? `${form.value.date} ${form.value.time}` : form.value.date
  }
  return '请选择'
})

// 日期时间选择器的值
const dateTimeValue = computed({
  get: () => {
    if (form.value.date) {
      return form.value.time ? `${form.value.date} ${form.value.time}` : `${form.value.date} 00:00:00`
    }
    return ''
  },
  set: (val) => {
    if (val) {
      const [date, time] = val.split(' ')
      form.value.date = date
      form.value.time = time || ''
    }
  }
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

// 同步外部值，避免循环更新
watch(() => props.modelValue, (val, oldVal) => {
  if (val) {
    // 检查值是否真的变化了，避免不必要的更新
    const hasChanged = !oldVal ||
      Object.keys(val).some(key => val[key] !== oldVal[key])
    if (hasChanged) {
      form.value = { ...form.value, ...val }
    }
  }
}, { immediate: true })

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
