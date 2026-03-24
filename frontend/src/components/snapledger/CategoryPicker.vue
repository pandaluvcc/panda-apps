<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="category-picker">
      <!-- 记录类型 Tab -->
      <van-tabs v-model:active="activeTabIndex" @change="onTabChange">
        <van-tab v-for="type in recordTypes" :key="type" :title="type" />
      </van-tabs>

      <!-- 加载状态 -->
      <van-loading v-if="loading" class="loading-state" />

      <!-- 错误状态 -->
      <van-empty v-else-if="error" description="加载失败">
        <van-button size="small" @click="loadCategories">重试</van-button>
      </van-empty>

      <!-- 空数据状态 -->
      <van-empty v-else-if="mainCategories.length === 0" description="暂无分类" />

      <!-- 分类网格 -->
      <van-grid v-else :column-num="5">
        <!-- 主类别模式 -->
        <template v-if="categoryStep === 'main'">
          <van-grid-item
            v-for="cat in mainCategories"
            :key="cat.name"
            :icon="cat.icon || 'notes-o'"
            :text="cat.name"
            @click="selectMainCategory(cat)"
          />
        </template>

        <!-- 子类别模式 -->
        <template v-else>
          <van-grid-item
            :icon="selectedMainCategory?.icon || 'notes-o'"
            text="返回"
            @click="goBackToMain"
          />
          <van-grid-item
            v-for="sub in subCategories"
            :key="sub.id"
            :icon="sub.icon || 'notes-o'"
            :text="sub.subCategory"
            @click="selectSubCategory(sub)"
          />
        </template>
      </van-grid>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getCategories } from '@/api'

const props = defineProps({
  show: { type: Boolean, default: false },
  recordType: { type: String, default: '支出' }
})

const emit = defineEmits(['update:show', 'update:recordType', 'select'])

// 双向绑定
const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

// 状态
const categories = ref([])
const loading = ref(false)
const error = ref(false)
const categoryStep = ref('main') // 'main' | 'sub'
const selectedMainCategory = ref(null)
const activeTabIndex = ref(0)

// 记录类型列表（固定顺序）
const recordTypes = ['建议', '支出', '收入', '转账', '应收款项', '应付款项']

// 当前类型下的所有分类
const currentTypeCategories = computed(() => {
  const type = recordTypes[activeTabIndex.value]
  return categories.value.filter(c => c.type === type)
})

// 主类别列表（去重，每个带图标）
const mainCategories = computed(() => {
  const seen = new Set()
  return currentTypeCategories.value
    .filter(c => {
      if (seen.has(c.mainCategory)) return false
      seen.add(c.mainCategory)
      return true
    })
    .map(c => ({
      name: c.mainCategory,
      icon: c.icon
    }))
})

// 子类别列表
const subCategories = computed(() => {
  if (!selectedMainCategory.value) return []
  return currentTypeCategories.value.filter(
    c => c.mainCategory === selectedMainCategory.value.name
  )
})

// 加载分类数据
async function loadCategories() {
  if (categories.value.length > 0) return

  loading.value = true
  error.value = false
  try {
    const res = await getCategories()
    categories.value = res || []
  } catch (e) {
    console.error('Failed to load categories:', e)
    error.value = true
  } finally {
    loading.value = false
  }
}

// Tab 切换时重置状态
function onTabChange() {
  categoryStep.value = 'main'
  selectedMainCategory.value = null

  // 同步 recordType
  const newType = recordTypes[activeTabIndex.value]
  emit('update:recordType', newType)
}

// 选择主类别
function selectMainCategory(cat) {
  const subs = currentTypeCategories.value.filter(c => c.mainCategory === cat.name)

  // 如果没有子类别，直接选中
  if (subs.length === 0) {
    emit('select', {
      id: null,
      type: recordTypes[activeTabIndex.value],
      mainCategory: cat.name,
      subCategory: cat.name,
      icon: cat.icon
    })
    visible.value = false
    return
  }

  // 进入子类别模式
  selectedMainCategory.value = cat
  categoryStep.value = 'sub'
}

// 返回主类别
function goBackToMain() {
  categoryStep.value = 'main'
  selectedMainCategory.value = null
}

// 选择子类别
function selectSubCategory(sub) {
  emit('select', {
    id: sub.id,
    type: sub.type,
    mainCategory: sub.mainCategory,
    subCategory: sub.subCategory,
    icon: sub.icon
  })
  visible.value = false
}

// 监听 show 变化
watch(() => props.show, (show) => {
  if (show) {
    loadCategories()
    // 根据传入的 recordType 设置初始 tab
    const idx = recordTypes.indexOf(props.recordType)
    if (idx >= 0) activeTabIndex.value = idx
  } else {
    // 关闭时重置状态
    categoryStep.value = 'main'
    selectedMainCategory.value = null
  }
})
</script>

<style scoped>
.category-picker {
  max-height: 60vh;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}
</style>
