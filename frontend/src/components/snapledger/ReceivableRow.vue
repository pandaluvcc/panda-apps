<template>
  <div class="receivable-row" :class="{ selected }" @click="$emit('click', item)">
    <div class="icon-wrap" :style="{ background: iconBg }">
      <span class="icon-glyph">{{ iconGlyph }}</span>
    </div>
    <div class="content">
      <div class="timestamp">{{ formatDateTime(item.date, item.time) }}</div>
      <div class="name">{{ item.name || '(未命名)' }}</div>
      <div class="subtitle">{{ subtitle }}</div>
    </div>
    <div class="right">
      <div class="status-text">{{ statusText }}</div>
      <div class="amount" :class="amountClass">¥{{ formatAmount(item.absAmount) }}</div>
      <div class="target-badge" v-if="item.account">{{ item.account }}</div>
    </div>
    <div class="check-circle" :class="{ active: selected }">
      <span v-if="selected">✓</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})
defineEmits(['click'])

const SUB_CATEGORY_META = {
  借出:  { bg: '#8fd7c6', glyph: '⑃' },
  代付:  { bg: '#8fd7c6', glyph: '♡' },
  报账:  { bg: '#8fd7c6', glyph: '▢' },
  借入:  { bg: '#d78fa8', glyph: '⑂' },
  信贷:  { bg: '#d78fa8', glyph: '▭' },
  车贷:  { bg: '#d78fa8', glyph: '⛟' },
  房贷:  { bg: '#d78fa8', glyph: '⌂' },
  利息:  { bg: '#d78fa8', glyph: '?' }
}

const DEFAULT_META = { bg: '#ccc', glyph: '·' }

const meta = computed(() => SUB_CATEGORY_META[props.item.subCategory] || DEFAULT_META)
const iconBg = computed(() => meta.value.bg)
const iconGlyph = computed(() => meta.value.glyph)

const isReceivable = computed(() => props.item.recordType === '应收款项')

const amountClass = computed(() => {
  if (props.item.status === 'COMPLETED') return 'completed'
  return isReceivable.value ? 'positive' : 'negative'
})

const statusText = computed(() => {
  if (props.item.status === 'COMPLETED') return '已完成'
  const paid = Number(props.item.paidAmount) || 0
  if (paid > 0) {
    return isReceivable.value
      ? `已收款 ¥${formatAmount(paid)}`
      : `已还款 ¥${formatAmount(paid)}`
  }
  return isReceivable.value ? '尚未收款' : '尚未还款'
})

const subtitle = computed(() => {
  const target = props.item.target || '不限定对象'
  if (props.item.recurringEventId) {
    return `周期 #${props.item.periodNumber} / 无限期 · ${target}`
  }
  return target
})

function formatDateTime(date, time) {
  if (!date) return ''
  const d = new Date(date)
  const wd = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  const dateStr = `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} 周${wd}`
  return time ? `${dateStr} ${String(time).substring(0, 5)}` : dateStr
}

function formatAmount(n) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.receivable-row {
  display: grid;
  grid-template-columns: 48px 1fr auto 24px;
  gap: 12px;
  padding: 12px 16px;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  background: #fff;
}
.receivable-row.selected { background: #eaf4ff; }
.icon-wrap {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px;
}
.content { min-width: 0; }
.timestamp { font-size: 12px; color: #999; }
.name { font-size: 16px; font-weight: 500; margin-top: 2px; color: #333; }
.subtitle { font-size: 12px; color: #999; margin-top: 2px; }
.right { text-align: right; }
.status-text { font-size: 12px; color: #999; }
.amount { font-size: 17px; font-weight: 500; margin-top: 2px; }
.amount.positive { color: #8fb94b; }
.amount.negative { color: #e06969; }
.amount.completed { color: #ccc; text-decoration: line-through; }
.target-badge {
  display: inline-block;
  margin-top: 4px;
  padding: 1px 8px;
  border: 1px solid #8fc8e8;
  border-radius: 10px;
  font-size: 11px;
  color: #5a9bc8;
}
.check-circle {
  width: 20px; height: 20px; border-radius: 50%;
  border: 1.5px solid #ccc;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #fff;
}
.check-circle.active { background: #4a90e2; border-color: #4a90e2; }
</style>
