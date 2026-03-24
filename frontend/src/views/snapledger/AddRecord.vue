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
    <CategoryGrid
      :categories="categories"
      :record-type="form.recordType"
      :loading="loadingCategories"
      :error="categoryError"
      @select="onCategorySelect"
      @retry="loadCategories"
    />

    <!-- 表单区域 -->
    <RecordForm v-model="form" />

    <!-- 编辑模式：删除按钮 -->
    <div v-if="isEdit" class="delete-section">
      <van-button type="danger" block @click="remove">删除记录</van-button>
    </div>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createRecord, updateRecord, deleteRecord, getRecordById, getCategories } from '@/api'
import { formatDateISO } from '@/utils/format'
import RecordForm from '@/components/snapledger/RecordForm.vue'
import CategoryGrid from '@/components/snapledger/CategoryGrid.vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'
import { showConfirmDialog, showToast } from 'vant'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const activeTab = ref(-1)

// 记录类型列表
const recordTypes = ['建议', '支出', '收入', '转账', '应收款项', '应付款项']

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

const categories = ref([])
const loadingCategories = ref(false)
const categoryError = ref(false)
const saving = ref(false)

// 加载分类数据
async function loadCategories() {
  loadingCategories.value = true
  categoryError.value = false
  try {
    const res = await getCategories()
    categories.value = res || []
  } catch (e) {
    console.error('Failed to load categories:', e)
    categoryError.value = true
  } finally {
    loadingCategories.value = false
  }
}

// 加载现有记录（编辑模式）
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

// 切换记录类型
function changeRecordType(type) {
  form.value.recordType = type
  // 清空已选分类
  form.value.mainCategory = ''
  form.value.subCategory = ''
}

// 分类选择回调
function onCategorySelect(category) {
  form.value.recordType = category.type
  form.value.mainCategory = category.mainCategory
  form.value.subCategory = category.subCategory
}

// 取消操作
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

// 保存记录
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
    } else {
      await createRecord(data)
    }
    router.push('/snap')
  } catch (e) {
    console.error('Failed to save:', e)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 删除记录（编辑模式）
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

onMounted(() => {
  loadCategories()
  loadRecord()
})
</script>

<style scoped>
.add-record {
  min-height: 100vh;
  background: #F7F8FA;
  padding-bottom: 80px;
}

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
  box-shadow: 0 1px rgba(0, 0, 0, 0.1);
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

.record-type-tabs {
  margin-top: 56px;
  display: flex;
  background: #FFFFFF;
  border-bottom: 1px solid #EEEEEE;
  overflow-x: auto;
}

.type-tab {
  flex: 1;
  min-width: 60px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
}

.type-tab.active {
  color: #1890FF;
}

.type-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 2px;
  background: #1890FF;
}

.delete-section {
  padding: 16px;
  background: #FFFFFF;
  margin-top: 8px;
}
</style>
