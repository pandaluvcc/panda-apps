<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="tag-picker">
      <div class="tag-picker-header">
        <span class="tag-picker-title">选择标签</span>
        <van-icon name="cross" @click="visible = false" />
      </div>

      <div class="tag-input-row">
        <input
          v-model="newTag"
          class="tag-input"
          placeholder="输入新标签"
          @keyup.enter="addNewTag"
        />
        <van-button size="small" type="primary" @click="addNewTag">添加</van-button>
      </div>

      <div class="tag-list">
        <van-tag
          v-for="tag in allTags"
          :key="tag"
          :type="selectedTags.includes(tag) ? 'primary' : 'default'"
          size="large"
          class="tag-item"
          @click="toggleTag(tag)"
        >
          {{ tag }}
        </van-tag>
      </div>

      <div class="tag-picker-footer">
        <van-button type="primary" block @click="confirm">确定</van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:show', 'update:modelValue'])

const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

const selectedTags = ref([])
const newTag = ref('')

// 预设标签列表
const presetTags = ['返利', '报销', '工资', '投资', '日常', '娱乐', '交通', '餐饮']
const allTags = ref([...presetTags])

// 同步外部值
watch(() => props.modelValue, (val) => {
  selectedTags.value = [...(val || [])]
}, { immediate: true })

watch(() => props.show, (show) => {
  if (show) {
    selectedTags.value = [...(props.modelValue || [])]
  }
})

function toggleTag(tag) {
  const idx = selectedTags.value.indexOf(tag)
  if (idx >= 0) {
    selectedTags.value.splice(idx, 1)
  } else {
    selectedTags.value.push(tag)
  }
}

function addNewTag() {
  const tag = newTag.value.trim()
  if (tag && !allTags.value.includes(tag)) {
    allTags.value.push(tag)
    selectedTags.value.push(tag)
    newTag.value = ''
  }
}

function confirm() {
  emit('update:modelValue', [...selectedTags.value])
  visible.value = false
}
</script>

<style scoped>
.tag-picker {
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}

.tag-picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.tag-picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #333333;
}

.tag-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tag-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #EEEEEE;
  border-radius: 4px;
  font-size: 14px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tag-item {
  cursor: pointer;
}

.tag-picker-footer {
  margin-top: 16px;
}
</style>
