<template>
  <van-cell-group inset>
    <van-field
      v-model="categoryDisplay"
      is-link
      readonly
      label="分类"
      placeholder="请选择分类"
      @click="showCategoryPicker = true"
    />

    <van-field
      v-model="form.amount"
      type="number"
      label="金额"
      placeholder="请输入金额"
      :rules="[{ required: true, message: '请输入金额' }]"
    />

    <van-field
      v-model="form.account"
      is-link
      readonly
      label="账户"
      placeholder="请选择账户"
      @click="showAccountPicker = true"
    />

    <van-field
      v-model="form.date"
      is-link
      readonly
      label="日期"
      placeholder="请选择日期"
      @click="showDatePicker = true"
    />

    <van-field
      v-model="form.name"
      label="名称"
      placeholder="可选"
    />

    <van-field
      v-model="form.description"
      rows="2"
      autosize
      label="备注"
      type="textarea"
      placeholder="可选"
    />
  </van-cell-group>

  <!-- 分类选择器 -->
  <CategoryPicker
    v-model:show="showCategoryPicker"
    v-model:recordType="form.recordType"
    @select="onCategorySelect"
  />

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
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getAccounts } from '@/api'
import CategoryPicker from './CategoryPicker.vue'

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
  date: new Date().toISOString().split('T')[0],
  name: '',
  description: ''
})

const accounts = ref([])
const showCategoryPicker = ref(false)
const showAccountPicker = ref(false)
const showDatePicker = ref(false)
const selectedDate = ref(['2024', '01', '01'])

// 分类显示文本
const categoryDisplay = computed(() => {
  if (form.value.mainCategory && form.value.subCategory) {
    return `${form.value.mainCategory} - ${form.value.subCategory}`
  }
  if (form.value.mainCategory) {
    return form.value.mainCategory
  }
  return ''
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

onMounted(async () => {
  try {
    const res = await getAccounts()
    accounts.value = res || []
  } catch (e) {
    console.error('Failed to load accounts:', e)
  }
})

// 分类选择回调
function onCategorySelect(category) {
  form.value.recordType = category.type
  form.value.mainCategory = category.mainCategory
  form.value.subCategory = category.subCategory
}

function onAccountConfirm({ selectedOptions }) {
  form.value.account = selectedOptions[0].text
  showAccountPicker.value = false
}

function onDateConfirm({ selectedValues }) {
  form.value.date = selectedValues.join('-')
  showDatePicker.value = false
}
</script>
