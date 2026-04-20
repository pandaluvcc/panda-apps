<template>
  <div class="add-record">
    <!-- 顶部导航栏 -->
    <div class="nav-bar">
      <div class="nav-btn" @click="handleCancel">
        <van-icon name="cross" size="24" />
      </div>
      <span class="nav-title">{{ isEdit ? '编辑记录' : '新增记录' }}</span>
      <div class="nav-btn confirm-btn" @click="save">
        <van-icon name="success" size="24" />
      </div>
    </div>

    <!-- 分类标签栏 -->
    <div class="record-type-tabs">
      <div
        v-for="type in recordTypes"
        :key="type"
        class="type-tab"
        :class="{ active: form.recordType === type }"
        @click="changeRecordType(type)"
      >
        {{ type }}
      </div>
    </div>

    <!-- 分类图标网格 -->
    <div class="category-grid">
      <div
        v-for="cat in displayCategories"
        :key="cat.id"
        class="category-item"
        :class="{ selected: isSelectedCategory(cat) }"
        @click="selectCategory(cat)"
      >
        <div class="category-icon" :style="{ backgroundColor: cat.color }">
          <van-icon :name="cat.icon" size="28" color="#FFFFFF" />
        </div>
        <span class="category-name">{{ cat.name }}</span>
      </div>
    </div>

    <!-- 金额输入 -->
    <div class="amount-row">
      <div class="currency">CNY</div>
      <div class="amount-wrapper">
        <input
          v-model.number="form.amount"
          type="number"
          class="amount-input"
          placeholder="0"
        />
        <div class="amount-controls">
          <button class="control-btn" @mousedown="startDecrement" @mouseup="stopTimer" @mouseleave="stopTimer">
            <van-icon name="minus" size="16" />
          </button>
          <button class="control-btn" @mousedown="startIncrement" @mouseup="stopTimer" @mouseleave="stopTimer">
            <van-icon name="plus" size="16" />
          </button>
        </div>
      </div>
    </div>

    <!-- 名称输入 -->
    <div class="form-row">
      <div class="form-item">
        <input v-model="form.name" class="form-input" placeholder="名称" />
      </div>
    </div>

    <!-- 账户 / 项目 -->
    <div class="form-row">
      <div class="form-item">
        <div class="form-input pick-input" @click="showAccountPicker = true">
          <span class="input-text">{{ form.account || '请选择账户' }}</span>
          <van-icon name="arrow" class="pick-arrow" />
        </div>
      </div>
      <div class="form-item">
        <input v-model="form.project" class="form-input" placeholder="项目" />
      </div>
    </div>

    <!-- 商家 / 高级设置 -->
    <div class="form-row">
      <div class="form-item">
        <input v-model="form.merchant" class="form-input" placeholder="商家" />
      </div>
      <div class="form-item">
        <div class="form-input pick-input" @click="showAdvancedSheet = true">
          <span class="input-text">{{ advancedLabel }}</span>
          <van-icon name="arrow" class="pick-arrow" />
        </div>
      </div>
    </div>

    <!-- 日期 / 时间 -->
    <div class="form-row">
      <div class="form-item">
        <div class="form-input pick-input" @click="showDateTimePicker = true">
          <span class="input-text">{{ displayDate }}</span>
          <van-icon name="arrow" class="pick-arrow" />
        </div>
      </div>
      <div class="form-item">
        <div class="form-input pick-input" @click="showDateTimePicker = true">
          <span class="input-text">{{ displayTime }}</span>
          <van-icon name="arrow" class="pick-arrow" />
        </div>
      </div>
    </div>

    <!-- 标签 / 返利 -->
    <div class="tag-row" @click="showTagPicker = true">
      <template v-if="form.tags && form.tags.length > 0">
        <span v-for="tag in form.tags" :key="tag" class="tag-chip">#{{ tag }}</span>
      </template>
      <span v-else class="tag-placeholder"># 标签 / 返利</span>
    </div>

    <!-- 备注 -->
    <div class="remark-box">
      <textarea v-model="form.description" class="remark-input" placeholder="备注" rows="2"></textarea>
    </div>

    <!-- 删除按钮（编辑模式） -->
    <div v-if="isEdit" class="delete-btn" @click="remove">
      <van-button type="danger" block>删除记录</van-button>
    </div>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />

    <!-- 账户选择器 -->
    <van-popup v-model:show="showAccountPicker" position="bottom" round>
      <van-picker :columns="accountColumns" @confirm="onAccountConfirm" @cancel="showAccountPicker = false" />
    </van-popup>

    <!-- 日期时间选择器 -->
    <DateTimePicker v-model:show="showDateTimePicker" v-model="dateTimeValue" />

    <!-- 标签选择器 -->
    <TagPicker v-model:show="showTagPicker" v-model="form.tags" />

    <!-- 高级设置：单次/周期/分期 -->
    <AdvancedSettingsSheet
      v-model:visible="showAdvancedSheet"
      :initial-config="advancedConfig"
      @confirm="onAdvancedConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createRecord, updateRecord, deleteRecord, getRecordById, getCategories, getAccounts } from '@/api'
import { createReceivable } from '@/api/snapledger/receivable'
import { createRecurringEvent } from '@/api/snapledger/recurringEvent'
import { formatDateISO } from '@/utils/format'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'
import TagPicker from '@/components/snapledger/TagPicker.vue'
import DateTimePicker from '@/components/snapledger/DateTimePicker.vue'
import AdvancedSettingsSheet from '@/components/snapledger/AdvancedSettingsSheet.vue'
import { showConfirmDialog, showToast } from 'vant'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const activeTab = ref(-1)

// 记录类型
const recordTypes = ['建议', '支出', '收入', '转账', '应收款项', '应付款项']

// 分类数据（硬编码，对应图片中的设计）
const allCategories = ref([
  { id: 1, name: '饮食', type: '支出', icon: 'food-o', color: '#F4D06D' },
  { id: 2, name: '交通', type: '支出', icon: 'car-o', color: '#4A90E2' },
  { id: 3, name: '娱乐', type: '支出', icon: 'scissors-o', color: '#9B59B6' },
  { id: 4, name: '购物', type: '支出', icon: 'shopping-cart-o', color: '#E67E22' },
  { id: 5, name: '个人', type: '支出', icon: 'friend-o', color: '#95A5A6' },
  { id: 6, name: '医疗', type: '支出', icon: 'hospital-o', color: '#E74C3C' },
  { id: 7, name: '家居', type: '支出', icon: 'home-o', color: '#34495E' },
  { id: 8, name: '家庭', type: '支出', icon: 'friends-o', color: '#D2B4DE' },
  { id: 9, name: '生活', type: '支出', icon: 'bag-o', color: '#8EAC58' },
  { id: 10, name: '学习', type: '支出', icon: 'gift-o', color: '#F39C12' },
  // 收入分类
  { id: 11, name: '工资', type: '收入', icon: 'cash-o', color: '#27AE60' },
  { id: 12, name: '投资', type: '收入', icon: 'trend-o', color: '#16A085' },
  { id: 13, name: '其他', type: '收入', icon: 'notes-o', color: '#2ECC71' }
])

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
const showAdvancedSheet = ref(false)
const saving = ref(false)

// 高级设置：默认"单次"
const advancedConfig = ref({ mode: 'single' })

const advancedLabel = computed(() => {
  if (advancedConfig.value.mode === 'recurring') return '周期'
  if (advancedConfig.value.mode === 'installment') return '分期'
  return '单次'
})

function onAdvancedConfirm(payload) {
  advancedConfig.value = payload
}

// 定时器
let incrementTimer = null
let decrementTimer = null

// 当前类型下的分类
const displayCategories = computed(() => {
  return allCategories.value.filter(c => c.type === form.value.recordType)
})

// 日期时间显示
const displayDate = computed(() => {
  return form.value.date || '日期'
})

const displayTime = computed(() => {
  return form.value.time || '时间'
})

// 日期时间完整显示
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

// 账户列表
const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

// 判断是否选中某个分类
function isSelectedCategory(cat) {
  return form.value.mainCategory === cat.name && form.value.subCategory === cat.name
}

// 选择分类
function selectCategory(cat) {
  form.value.recordType = cat.type
  form.value.mainCategory = cat.name
  form.value.subCategory = cat.name
}

// 切换记录类型
function changeRecordType(type) {
  form.value.recordType = type
  form.value.mainCategory = ''
  form.value.subCategory = ''
}

// 金额控制
function startIncrement() {
  increment()
  incrementTimer = setInterval(increment, 100)
}

function startDecrement() {
  decrement()
  decrementTimer = setInterval(decrement, 100)
}

function stopTimer() {
  if (incrementTimer) {
    clearInterval(incrementTimer)
    incrementTimer = null
  }
  if (decrementTimer) {
    clearInterval(decrementTimer)
    decrementTimer = null
  }
}

function increment() {
  const val = Number(form.value.amount) || 0
  form.value.amount = val + 1
}

function decrement() {
  const val = Number(form.value.amount) || 0
  form.value.amount = Math.max(0, val - 1)
}

// 账户选择确认
function onAccountConfirm({ selectedOptions }) {
  form.value.account = selectedOptions[0].text
  showAccountPicker.value = false
}

// 取消
async function handleCancel() {
  if (hasChanges()) {
    try {
      await showConfirmDialog({
        title: '提示',
        message: '确定要放弃当前编辑吗？',
      })
      router.back()
    } catch {
      // 用户取消
    }
  } else {
    router.back()
  }
}

// 检查是否有修改
function hasChanges() {
  return form.value.amount || form.value.mainCategory || form.value.description
}

// 保存
async function save() {
  if (!form.value.amount) {
    showToast('请填写金额')
    return
  }
  if (!form.value.mainCategory) {
    showToast('请选择分类')
    return
  }

  saving.value = true
  try {
    const data = {
      ...form.value,
      tags: form.value.tags?.join(',') || ''
    }

    if (isEdit.value) {
      await updateRecord(route.params.id, data)
      router.push('/snap')
      return
    }

    if (advancedConfig.value.mode === 'recurring') {
      // 创建周期事件：以表单内容为模板
      const payload = {
        name: form.value.name || form.value.mainCategory,
        recordType: form.value.recordType,
        amount: Number(form.value.amount),
        mainCategory: form.value.mainCategory,
        subCategory: form.value.subCategory,
        account: form.value.account,
        targetAccount: form.value.target || null,
        intervalType: advancedConfig.value.intervalType,
        intervalValue: advancedConfig.value.intervalValue || 1,
        dayOfMonth: advancedConfig.value.dayOfMonth,
        dayOfWeek: advancedConfig.value.dayOfWeek,
        startDate: form.value.date,
        totalPeriods: advancedConfig.value.totalPeriods,
        note: form.value.description || null
      }
      await createRecurringEvent(payload)
      showToast('周期事件已创建')
      router.push('/snap/events/recurring')
      return
    }

    if (['应收款项', '应付款项'].includes(form.value.recordType)) {
      await createReceivable({
        recordType: form.value.recordType,
        subCategory: form.value.subCategory,
        name: form.value.name || form.value.mainCategory,
        account: form.value.account,
        amount: Number(form.value.amount),
        date: form.value.date,
        time: form.value.time,
        target: form.value.target,
        description: form.value.description
      })
      showToast('已保存')
      router.push('/snap/receivables')
      return
    }

    await createRecord(data)
    router.push('/snap')
  } catch (e) {
    console.error('Failed to save:', e)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 删除
async function remove() {
  try {
    await showConfirmDialog({
      title: '提示',
      message: '确定删除这条记录吗？',
    })
    await deleteRecord(route.params.id)
    router.push('/snap')
  } catch (e) {
    if (e !== 'cancel') {
      console.error('Failed to delete:', e)
      showToast('删除失败')
    }
  }
}

// 加载数据
async function loadCategories() {
  try {
    await getCategories()
  } catch (e) {
    console.error('Failed to load categories:', e)
  }
}

async function loadAccounts() {
  try {
    const res = await getAccounts()
    accounts.value = res || []
  } catch (e) {
    console.error('Failed to load accounts:', e)
  }
}

async function loadRecord() {
  if (!isEdit.value) return
  try {
    const record = await getRecordById(route.params.id)
    if (record) {
      form.value = {
        ...form.value,
        ...record,
        tags: record.tags ? record.tags.split(',').filter(Boolean) : []
      }
    }
  } catch (e) {
    console.error('Failed to load record:', e)
    showToast('加载记录失败')
    router.back()
  }
}

onMounted(async () => {
  loadCategories()
  await loadAccounts()
  // Pre-fill account when navigated from Account Detail page (?accountId=:id)
  if (route.query.accountId) {
    const acc = accounts.value.find(a => String(a.id) === String(route.query.accountId))
    if (acc) form.value.account = acc.name
  }
  loadRecord()
})
</script>

<style scoped>
.add-record {
  min-height: 100vh;
  background: #F7F8FA;
  padding-bottom: 80px;
}

/* 顶部导航栏 */
.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FFFFFF;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  z-index: 100;
  padding: 0 16px;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #000000;
}

.nav-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  cursor: pointer;
}

.confirm-btn {
  color: #1890FF;
}

/* 分类标签栏 */
.record-type-tabs {
  margin-top: 56px;
  display: flex;
  background: #FFFFFF;
  border-bottom: 1px solid #EEEEEE;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.record-type-tabs::-webkit-scrollbar {
  display: none;
}

.type-tab {
  flex: 1;
  min-width: 60px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #999999;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
}

.type-tab.active {
  color: #333333;
  font-weight: 500;
}

.type-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  background: #1890FF;
  border-radius: 2px;
}

/* 分类图标网格 */
.category-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  padding: 20px 16px;
  background: #FFFFFF;
  margin-top: 8px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.category-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  transition: transform 0.15s, opacity 0.15s;
}

.category-item:active .category-icon {
  transform: scale(0.92);
}

.category-item.selected .category-icon {
  opacity: 0.7;
}

.category-name {
  font-size: 12px;
  color: #666666;
  text-align: center;
}

/* 金额输入行 */
.amount-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #FFFFFF;
  margin-top: 8px;
}

.currency {
  font-size: 16px;
  font-weight: 500;
  color: #666666;
  background: #F0F0F0;
  padding: 8px 10px;
  border-radius: 6px;
  flex-shrink: 0;
}

.amount-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.amount-input {
  flex: 1;
  font-size: 36px;
  font-weight: 600;
  color: #000000;
  background: transparent;
  border: none;
  outline: none;
  padding: 8px 0;
  text-align: right;
  min-width: 0;
}

.amount-input::placeholder {
  color: #DDDDDD;
}

.amount-controls {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.control-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F0F0F0;
  border: none;
  border-radius: 50%;
  color: #666666;
  cursor: pointer;
  transition: background 0.15s, transform 0.1s;
}

.control-btn:active {
  background: #E0E0E0;
  transform: scale(0.95);
}

/* 表单区域 */
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 16px;
  margin-top: 8px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-input {
  font-size: 14px;
  color: #333333;
  background: #FFFFFF;
  border: 1px solid #EEEEEE;
  border-radius: 8px;
  padding: 14px 16px;
  height: 48px;
  box-sizing: border-box;
  outline: none;
  transition: all 0.15s;
}

.form-input:focus {
  border-color: #1890FF;
  background: #FFFFFF;
}

.form-input:active {
  background: #FAFAFA;
}

.form-input::placeholder {
  color: #CCCCCC;
}

.pick-input {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pick-arrow {
  font-size: 14px;
  color: #CCCCCC;
  flex-shrink: 0;
}

/* 标签行 */
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #FFFFFF;
  margin: 8px 16px 0 16px;
  cursor: pointer;
  min-height: 44px;
  border: 1px solid #EEEEEE;
  border-radius: 8px;
}

.tag-chip {
  font-size: 14px;
  color: #1890FF;
  background: #E6F7FF;
  padding: 4px 10px;
  border-radius: 4px;
}

.tag-placeholder {
  font-size: 14px;
  color: #1890FF;
  opacity: 0.6;
}

/* 备注框 */
.remark-box {
  background: #FFFFFF;
  margin: 8px 16px;
  padding: 12px 16px;
  border: 1px solid #EEEEEE;
  border-radius: 8px;
}

.remark-input {
  width: 100%;
  font-size: 14px;
  color: #333333;
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  resize: none;
  box-sizing: border-box;
  min-height: 60px;
  outline: none;
}

.remark-input::placeholder {
  color: #CCCCCC;
}

/* 删除按钮 */
.delete-btn {
  padding: 16px;
  background: #FFFFFF;
  margin: 8px 16px;
}

/* 底部导航占位 */
.snap-tabbar-placeholder {
  height: 80px;
}
</style>
