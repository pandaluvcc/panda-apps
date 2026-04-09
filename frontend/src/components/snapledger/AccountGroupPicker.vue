<template>
  <van-popup
    v-model:show="visible"
    position="bottom"
    round
    :style="{ maxHeight: '70vh' }"
  >
    <div class="group-picker">
      <!-- 标题栏 -->
      <div class="picker-header">
        <span class="picker-title">账户分组</span>
        <button class="header-add-btn" @click="toggleAddInput" aria-label="新增分组">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </div>

      <!-- 新增分组输入 -->
      <transition name="fade-in">
        <div v-if="showAddInput" class="add-input-row">
          <input
            ref="addInputRef"
            v-model="newGroupName"
            class="add-input"
            placeholder="输入新分组名称"
            maxlength="20"
            @keydown.enter="confirmAddGroup"
          />
          <button
            class="add-confirm-btn"
            :disabled="!newGroupName.trim() || adding"
            @click="confirmAddGroup"
          >
            <van-loading v-if="adding" size="14" color="#fff" />
            <span v-else>添加</span>
          </button>
        </div>
      </transition>

      <!-- 分组列表 -->
      <div class="group-list">
        <van-loading v-if="loading" class="list-loading" color="#999" size="20" />
        <template v-else>
          <div
            v-for="group in groups"
            :key="group.id"
            class="group-item"
            :class="{ selected: tempSelected === group.name }"
            @click="tempSelected = group.name"
          >
            {{ group.name }}
          </div>
        </template>
      </div>

      <!-- 底部按钮 -->
      <div class="picker-footer">
        <button class="footer-btn cancel-btn" @click="cancel">取消</button>
        <button class="footer-btn confirm-btn" @click="confirm">确定</button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { getAccountGroups, createAccountGroup } from '@/api'
import { showToast } from 'vant'

const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:show', 'update:modelValue'])

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v)
})

const groups = ref([])
const loading = ref(false)
const adding = ref(false)
const showAddInput = ref(false)
const newGroupName = ref('')
const addInputRef = ref(null)
const tempSelected = ref('')

watch(() => props.show, async (v) => {
  if (v) {
    tempSelected.value = props.modelValue || ''
    showAddInput.value = false
    newGroupName.value = ''
    await loadGroups()
  }
})

async function loadGroups() {
  loading.value = true
  try {
    groups.value = await getAccountGroups() || []
    // 如果当前没有选中值，默认选第一项
    if (!tempSelected.value && groups.value.length > 0) {
      tempSelected.value = groups.value[0].name
    }
  } catch (e) {
    showToast('加载分组失败')
  } finally {
    loading.value = false
  }
}

async function toggleAddInput() {
  showAddInput.value = !showAddInput.value
  if (showAddInput.value) {
    await nextTick()
    addInputRef.value?.focus()
  }
}

async function confirmAddGroup() {
  const name = newGroupName.value.trim()
  if (!name) return

  adding.value = true
  try {
    const created = await createAccountGroup(name)
    groups.value.push(created)
    tempSelected.value = created.name
    newGroupName.value = ''
    showAddInput.value = false
  } catch (e) {
    const msg = e?.response?.data?.message || '添加失败'
    showToast(msg)
  } finally {
    adding.value = false
  }
}

function confirm() {
  emit('update:modelValue', tempSelected.value)
  visible.value = false
}

function cancel() {
  visible.value = false
}
</script>

<style scoped>
.group-picker {
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  border-radius: 16px 16px 0 0;
  overflow: hidden;
  max-height: 70vh;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 18px 20px 16px;
  border-bottom: 1px solid #F2F2F2;
  flex-shrink: 0;
}

.picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
}

.header-add-btn {
  position: absolute;
  right: 20px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: #555;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.15s;
}

.header-add-btn:active { background: #F0F0F0; }
.header-add-btn svg { width: 20px; height: 20px; }

.add-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: #F8F8F8;
  border-bottom: 1px solid #F2F2F2;
  flex-shrink: 0;
}

.add-input {
  flex: 1;
  height: 40px;
  padding: 0 12px;
  font-size: 15px;
  background: #FFFFFF;
  border: 1px solid #E0E0E0;
  border-radius: 8px;
  outline: none;
  color: #1A1A1A;
}

.add-input:focus { border-color: #1890FF; }

.add-confirm-btn {
  min-width: 56px;
  height: 40px;
  padding: 0 14px;
  background: #1890FF;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s;
}

.add-confirm-btn:disabled { opacity: 0.4; cursor: default; }

.group-list {
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.list-loading {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.group-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52px;
  font-size: 16px;
  color: #333333;
  border-bottom: 1px solid #F5F5F5;
  cursor: pointer;
  transition: background 0.1s, color 0.1s;
}

.group-item:last-child { border-bottom: none; }
.group-item.selected { color: #1890FF; background: #F0F8FF; font-weight: 500; }
.group-item:active:not(.selected) { background: #FAFAFA; }

.picker-footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px 20px;
  background: #FFFFFF;
  border-top: 1px solid #F2F2F2;
  flex-shrink: 0;
}

.footer-btn {
  flex: 1;
  height: 48px;
  border-radius: 10px;
  font-size: 16px;
  border: none;
  cursor: pointer;
  transition: opacity 0.15s;
}

.footer-btn:active { opacity: 0.7; }
.cancel-btn { background: #F2F2F2; color: #333333; }
.confirm-btn { background: #1890FF; color: #FFFFFF; }

.fade-in-enter-active { transition: opacity 0.15s, transform 0.15s; }
.fade-in-leave-active { transition: opacity 0.1s, transform 0.1s; }
.fade-in-enter-from  { opacity: 0; transform: translateY(-6px); }
.fade-in-leave-to    { opacity: 0; transform: translateY(-6px); }
</style>
