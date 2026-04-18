<template>
  <van-popup
    :show="visible"
    position="center"
    round
    class="detail-popup"
    @update:show="val => $emit('update:visible', val)"
  >
    <div class="card" v-if="record">
      <!-- 顶部：彩色区 + 图标 + 类型 + 操作按钮 -->
      <div class="top" :style="{ backgroundColor: headerColor }">
        <div class="top-actions">
          <van-icon name="cross" class="icon-btn" @click="close" />
          <div class="right-icons">
            <van-icon name="replay" class="icon-btn" @click="onRefund" />
            <van-icon name="delete-o" class="icon-btn" @click="onDelete" />
            <van-icon name="records" class="icon-btn" @click="onCopy" />
            <van-icon name="edit" class="icon-btn" @click="onEditTap" />
          </div>
        </div>
        <div class="big-icon-wrap">
          <van-icon :name="headerIcon" color="#fff" size="56" />
        </div>
        <div class="type-label">{{ typeLabel }}</div>
      </div>

      <!-- 小图标 + 名称 + 金额 -->
      <div class="title-row">
        <div class="small-icon" :style="{ backgroundColor: headerColor }">
          <van-icon :name="headerIcon" color="#fff" size="16" />
        </div>
        <div class="name">{{ record.name }}</div>
        <div class="amount" :class="amountClass">
          ￥{{ fmtAmount(record.amount) }}
        </div>
      </div>

      <!-- 明细 -->
      <div class="fields">
        <div class="field-row">
          <div class="field">
            <van-icon name="balance-list-o" class="field-icon" />
            <span>{{ record.account || '---' }}</span>
          </div>
          <div v-if="isTransfer" class="field">
            <van-icon name="balance-list-o" class="field-icon" />
            <span>{{ record.target || '---' }}</span>
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <van-icon name="label-o" class="field-icon" />
            <span>{{ record.project || '没有项目' }}</span>
          </div>
          <div class="field">
            <van-icon name="shop-o" class="field-icon" />
            <span>{{ record.merchant || '---' }}</span>
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <van-icon name="calendar-o" class="field-icon" />
            <span>{{ record.date }}</span>
          </div>
          <div class="field">
            <van-icon name="clock-o" class="field-icon" />
            <span>{{ record.time || '---' }}</span>
          </div>
        </div>
        <div class="period-line">
          周期：#{{ record.periodNumber || '-' }}
          <span v-if="eventInfo"> / {{ eventInfo }}</span>
        </div>
      </div>
    </div>

    <!-- 二级编辑菜单 -->
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
import { ref, computed } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  visible: { type: Boolean, default: false },
  record: { type: Object, default: null },
  eventInfo: { type: String, default: '' }
})
const emit = defineEmits(['update:visible', 'edit', 'delete'])

const showEditChoice = ref(false)

const TRANSFER_TYPES = ['转账', '还款', '转出', '转入', '应付款项', '应收款项', '分期还款']
const EXPENSE_TYPES = ['支出', '手续费', '利息']

const isTransfer = computed(() => TRANSFER_TYPES.includes(props.record?.recordType))

const headerColor = computed(() => {
  if (!props.record) return '#999'
  if (isTransfer.value) return '#D8944B'
  if (props.record.recordType === '收入') return '#67c23a'
  return '#C97789'
})
const headerIcon = computed(() => {
  if (isTransfer.value) return 'exchange'
  if (props.record?.recordType === '收入') return 'cash-o'
  return 'home-o'
})
const typeLabel = computed(() => {
  if (isTransfer.value) return '转账'
  if (props.record?.recordType === '收入') return '收入'
  return '支出'
})

/** 颜色规则：转账类红色，支出类绿色，不加 +/- 符号。 */
const amountClass = computed(() => {
  if (!props.record) return ''
  if (isTransfer.value) return 'amount-red'
  if (EXPENSE_TYPES.includes(props.record.recordType)) return 'amount-green'
  return 'amount-green'
})

function fmtAmount(v) {
  return (Math.abs(Number(v) || 0)).toFixed(2)
}

function close() {
  emit('update:visible', false)
}

function onEditTap() {
  showEditChoice.value = true
}
function emitEdit(mode) {
  showEditChoice.value = false
  close()
  emit('edit', mode)
}
function onDelete() {
  close()
  emit('delete')
}
function onCopy() {
  close()
  showToast('功能开发中')
}
function onRefund() {
  close()
  showToast('功能开发中')
}
</script>

<style scoped>
.detail-popup { width: 86%; max-width: 400px; background: transparent !important; }
.card { background: #fff; border-radius: 16px; overflow: hidden; }

.top {
  padding: 16px 16px 20px;
  color: #fff;
  display: flex; flex-direction: column; align-items: center;
  position: relative;
}
.top-actions {
  width: 100%; display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px;
}
.right-icons { display: flex; gap: 16px; }
.icon-btn { font-size: 18px; color: #fff; cursor: pointer; opacity: 0.9; }
.icon-btn:active { opacity: 0.6; }
.big-icon-wrap {
  width: 120px; height: 120px; border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.6);
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 12px;
}
.type-label { font-size: 18px; color: #fff; margin-bottom: 4px; }

.title-row {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 16px 10px; border-bottom: 1px solid #f2f3f5;
}
.small-icon {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.name { flex: 1; font-size: 16px; font-weight: 500; color: #1a1a1a; }
.amount { font-size: 18px; font-weight: 600; }
.amount-red { color: #f56c6c; }
.amount-green { color: #67c23a; }

.fields { padding: 10px 16px 16px; font-size: 13px; color: #555; }
.field-row { display: flex; gap: 16px; margin-bottom: 8px; }
.field { flex: 1; display: flex; align-items: center; gap: 6px; min-width: 0; }
.field-icon { color: #999; font-size: 14px; }
.field span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.period-line {
  margin-top: 6px; padding-top: 8px; border-top: 1px solid #f2f3f5;
  font-size: 12px; color: #999;
}

.edit-choice-popup { width: 80%; max-width: 320px; }
.edit-choice { padding: 20px; display: flex; flex-direction: column; gap: 10px; }
.choice-btn {
  border: none; background: #f2f3f5; border-radius: 10px;
  padding: 14px; font-size: 15px; color: #333; cursor: pointer;
}
.choice-cancel { border: none; background: transparent; color: #666; padding: 8px; cursor: pointer; }
</style>
