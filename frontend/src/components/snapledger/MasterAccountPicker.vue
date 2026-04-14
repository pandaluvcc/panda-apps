<template>
  <van-popup v-model:show="show" position="bottom" round :style="{ maxHeight: '70vh' }">
    <div class="master-picker">
      <div class="picker-header">
        <span class="picker-title">选择主账户</span>
        <button class="picker-close" @click="emit('update:show', false)">✕</button>
      </div>

      <div class="picker-options">
        <!-- 无主账户选项 -->
        <div
          :class="['picker-option', { active: modelValue === null || modelValue === '' || modelValue === '无' }]"
          @click="select(null)"
        >
          <span class="option-name">无</span>
          <span v-if="modelValue === null || modelValue === '' || modelValue === '无'" class="option-check">✓</span>
        </div>

        <!-- 主账户列表 -->
        <div
          v-for="master in masterAccounts"
          :key="master.id"
          :class="['picker-option', { active: modelValue === master.name }]"
          @click="select(master.name)"
        >
          <div class="option-info">
            <span class="option-name">{{ master.name }}</span>
            <span class="option-group">{{ master.accountGroup }}</span>
          </div>
          <div class="option-actions">
            <span v-if="modelValue === master.name" class="option-check">✓</span>
            <button class="option-add" @click.stop="goAddMaster">+</button>
          </div>
        </div>
      </div>

      <div v-if="masterAccounts.length === 0" class="picker-empty">
        暂无主账户，点击右上角"+"创建
      </div>

      <div class="picker-footer">
        <button class="footer-btn" @click="goAddMaster">新增主账户 +</button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAccounts } from '@/api'

const props = defineProps({
  show: Boolean,
  modelValue: String,
  creditGroupOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:show', 'update:modelValue'])

const router = useRouter()
const allAccounts = ref([])

onMounted(async () => {
  try {
    allAccounts.value = await getAccounts()
  } catch {
    allAccounts.value = []
  }
})

// 过滤：仅返回未归档的、isMasterAccount=true 的账户
const masterAccounts = computed(() => {
  return allAccounts.value.filter(acc => {
    if (acc.isArchived) return false
    if (!acc.isMasterAccount) return false
    if (props.creditGroupOnly && acc.accountGroup !== '信用卡') return false
    return true
  })
})

function select(name) {
  emit('update:modelValue', name)
  emit('update:show', false)
}

function goAddMaster() {
  emit('update:show', false)
  // 跳转到新增账户页面，携带 masterMode 参数
  router.push({
    path: '/snap/add',
    query: { masterMode: 'true' }
  })
}
</script>

<style scoped>
.master-picker {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}
.picker-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}
.picker-close {
  width: 28px; height: 28px;
  border: none; background: none;
  font-size: 18px; color: #999;
  cursor: pointer;
}
.picker-options {
  flex: 1;
  overflow-y: auto;
}
.picker-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.picker-option.active {
  background: #e8f4ff;
}
.picker-option:active {
  background: #f0f0f0;
}
.option-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.option-name {
  font-size: 15px;
  color: #333;
  font-weight: 500;
}
.option-group {
  font-size: 12px;
  color: #999;
}
.option-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.option-check {
  color: #1989fa;
  font-size: 16px;
  font-weight: bold;
}
.option-add {
  width: 28px; height: 28px;
  border-radius: 50%;
  border: none;
  background: #1989fa;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.picker-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
  padding: 32px;
}
.picker-footer {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.footer-btn {
  width: 100%;
  height: 44px;
  border-radius: 10px;
  border: none;
  background: #1989fa;
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
}
</style>
