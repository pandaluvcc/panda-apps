<template>
  <van-cell-group inset>
    <van-field name="recordType" label="类型">
      <template #input>
        <van-radio-group v-model="form.recordType" direction="horizontal">
          <van-radio name="支出">支出</van-radio>
          <van-radio name="收入">收入</van-radio>
        </van-radio-group>
      </template>
    </van-field>

    <van-field
      v-model="form.amount"
      type="number"
      label="金额"
      placeholder="请输入金额"
      :rules="[{ required: true, message: '请输入金额' }]"
    />

    <van-field
      v-model="form.mainCategory"
      is-link
      readonly
      label="分类"
      placeholder="请选择分类"
      @click="showCategoryPicker = true"
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
  <van-popup v-model:show="showCategoryPicker" position="bottom" round>
    <van-picker
      :columns="categoryColumns"
      @confirm="onCategoryConfirm"
      @cancel="showCategoryPicker = false"
    />
  </van-popup>

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
import { getCategories, getAccounts } from '@/api'

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

const categories = ref([])
const accounts = ref([])
const showCategoryPicker = ref(false)
const showAccountPicker = ref(false)
const showDatePicker = ref(false)
const selectedDate = ref(['2024', '01', '01'])

const categoryColumns = computed(() => {
  const mainCats = [...new Set(categories.value.map(c => c.mainCategory))]
  return mainCats.map(cat => ({ text: cat, value: cat }))
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

onMounted(async () => {
  try {
    const [catRes, accRes] = await Promise.all([
      getCategories(),
      getAccounts()
    ])
    categories.value = catRes.data || []
    accounts.value = accRes.data || []
  } catch (e) {
    console.error('Failed to load categories/accounts:', e)
  }
})

function onCategoryConfirm({ selectedOptions }) {
  form.value.mainCategory = selectedOptions[0].text
  showCategoryPicker.value = false
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
