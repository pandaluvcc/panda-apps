<template>
  <div class="category-grid">
    <van-loading v-if="loading" class="loading-state" />
    <van-empty v-else-if="error" description="加载失败">
      <van-button size="small" @click="$emit('retry')">重试</van-button>
    </van-empty>
    <van-empty v-else-if="mainCategories.length === 0" description="暂无分类" />
    <div v-else class="grid-container">
      <!-- 主类别模式 -->
      <template v-if="categoryStep === 'main'">
        <div
          v-for="cat in mainCategories"
          :key="cat.name"
          class="grid-item"
          :class="{ selected: selectedMainCategory === cat.name }"
          @click="selectMainCategory(cat)"
        >
          <div class="icon-container">
            <van-icon :name="cat.icon || 'notes-o'" size="24" />
          </div>
          <span class="item-text">{{ cat.name }}</span>
        </div>
      </template>

      <!-- 子类别模式 -->
      <template v-else>
        <div class="grid-item" @click="goBackToMain">
          <div class="icon-container back-icon">
            <van-icon name="arrow-left" size="24" />
          </div>
          <span class="item-text">返回</span>
        </div>
        <div
          v-for="sub in subCategories"
          :key="sub.id"
          class="grid-item"
          :class="{ selected: selectedSubCategory === sub.subCategory }"
          @click="selectSubCategory(sub)"
        >
          <div class="icon-container">
            <van-icon :name="sub.icon || 'notes-o'" size="24" />
          </div>
          <span class="item-text">{{ sub.subCategory }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  recordType: { type: String, default: '支出' },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'retry'])

const categoryStep = ref('main')
const selectedMainCategory = ref(null)
const selectedSubCategory = ref(null)

// 当前类型下的所有分类
const currentTypeCategories = computed(() => {
  return props.categories.filter(c => c.type === props.recordType)
})

// 主类别列表（去重）
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
    c => c.mainCategory === selectedMainCategory.value
  )
})

// 记录类型变化时重置
watch(() => props.recordType, () => {
  categoryStep.value = 'main'
  selectedMainCategory.value = null
  selectedSubCategory.value = null
})

function selectMainCategory(cat) {
  const subs = currentTypeCategories.value.filter(c => c.mainCategory === cat.name)

  // 如果没有子类别，直接选中
  if (subs.length === 0 || subs.length === 1) {
    const sub = subs[0]
    emit('select', {
      id: sub?.id || null,
      type: props.recordType,
      mainCategory: cat.name,
      subCategory: sub?.subCategory || cat.name,
      icon: cat.icon
    })
    selectedMainCategory.value = cat.name
    selectedSubCategory.value = sub?.subCategory || cat.name
    return
  }

  // 进入子类别模式
  selectedMainCategory.value = cat.name
  categoryStep.value = 'sub'
}

function goBackToMain() {
  categoryStep.value = 'main'
  selectedSubCategory.value = null
}

function selectSubCategory(sub) {
  selectedSubCategory.value = sub.subCategory
  emit('select', {
    id: sub.id,
    type: sub.type,
    mainCategory: sub.mainCategory,
    subCategory: sub.subCategory,
    icon: sub.icon
  })
}
</script>

<style scoped>
.category-grid {
  background: #FFFFFF;
  padding: 16px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.grid-container {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.icon-container {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F5F5;
  border-radius: 8px;
  color: #D4B16A;
  transition: all 0.2s;
}

.grid-item.selected .icon-container {
  background: #E6F7FF;
  border: 2px solid #1890FF;
  color: #1890FF;
}

.back-icon {
  color: #666666;
}

.item-text {
  font-size: 12px;
  color: #333333;
  text-align: center;
}
</style>
