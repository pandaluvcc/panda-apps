<template>
  <van-popup
    :show="visible"
    position="bottom"
    round
    @update:show="val => $emit('update:visible', val)"
  >
    <div class="action-sheet">
      <div class="actions-row">
        <button class="action-btn" @click="onEdit">
          <van-icon name="edit" /> 编辑
        </button>
        <button class="action-btn" @click="onDelete">
          <van-icon name="delete-o" /> 删除
        </button>
        <button class="action-btn" @click="onCopy">
          <van-icon name="records" /> 复制
        </button>
        <button class="action-btn" @click="onRefund">
          <van-icon name="replay" /> 退款
        </button>
      </div>
      <button class="cancel-btn" @click="$emit('update:visible', false)">取消</button>
    </div>

    <!-- 二级编辑弹窗 -->
    <van-popup
      v-model:show="showEditChoice"
      position="center"
      round
      class="edit-choice-popup"
    >
      <div class="edit-choice">
        <button class="choice-btn" @click="emitEdit('single')">修改此记录</button>
        <button class="choice-btn" @click="emitEdit('entire')">修改整个周期事件</button>
        <button class="choice-btn" @click="emitEdit('future')">修改连同未来周期</button>
        <button class="choice-cancel" @click="showEditChoice = false">取消</button>
      </div>
    </van-popup>
  </van-popup>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'

defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible', 'edit', 'delete'])

const showEditChoice = ref(false)

function onEdit() {
  showEditChoice.value = true
}

function emitEdit(mode) {
  showEditChoice.value = false
  emit('update:visible', false)
  emit('edit', mode)
}

function onDelete() {
  emit('update:visible', false)
  emit('delete')
}

function onCopy() {
  emit('update:visible', false)
  showToast('功能开发中')
}

function onRefund() {
  emit('update:visible', false)
  showToast('功能开发中')
}
</script>

<style scoped>
.action-sheet { padding: 16px; }
.actions-row {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px;
}
.action-btn {
  border: none; background: #f7f8fa; border-radius: 12px;
  padding: 14px 8px; font-size: 13px; color: #333;
  display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer;
}
.action-btn .van-icon { font-size: 22px; color: #666; }
.cancel-btn {
  width: 100%; border: none; background: #fff; color: #f56c6c;
  padding: 14px; border-top: 1px solid #ebedf0; font-size: 15px; cursor: pointer;
}
.edit-choice-popup { width: 80%; max-width: 320px; }
.edit-choice { padding: 20px; display: flex; flex-direction: column; gap: 10px; }
.choice-btn {
  border: none; background: #f2f3f5; border-radius: 10px;
  padding: 14px; font-size: 15px; color: #333; cursor: pointer;
}
.choice-cancel {
  border: none; background: transparent; color: #666; padding: 8px; cursor: pointer;
}
</style>
