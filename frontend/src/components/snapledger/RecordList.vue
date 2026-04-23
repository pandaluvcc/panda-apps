<template>
  <div class="record-list">
    <div
      v-for="record in displayRecords"
      :key="record.id"
      class="record-row"
      @click="$emit('edit', record)"
    >
      <div class="icon-circle" :style="{ background: styleOf(record).color }">
        <van-icon :name="styleOf(record).icon" color="#fff" size="24" />
      </div>

      <div class="middle">
        <div class="name">{{ displayName(record) }}</div>
        <div v-if="subLine(record)" class="sub-line">{{ subLine(record) }}</div>
      </div>

      <div class="right">
        <div class="amount" :class="amountClass(record)">
          {{ formatAmount(record) }}
        </div>
        <div class="tags">
          <span
            v-for="(t, idx) in tagsToShow(record)"
            :key="idx"
            class="tag"
            :class="t.type === 'category' ? 'tag-category' : 'tag-account'"
          >
            {{ t.text }}
          </span>
        </div>
      </div>
    </div>

    <van-empty v-if="records.length === 0" description="暂无记录" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  records: { type: Array, default: () => [] }
})

defineEmits(['edit'])

// 按 subCategory / mainCategory 查图标+色
const CATEGORY_STYLES = {
  // 餐饮相关（午餐示例用）
  '早餐': { icon: 'goods-collect-o', color: '#e5a04a' },
  '午餐': { icon: 'goods-collect-o', color: '#e5a04a' },
  '晚餐': { icon: 'goods-collect-o', color: '#e5a04a' },
  '餐饮': { icon: 'goods-collect-o', color: '#e5a04a' },

  // 房产/贷款
  '房贷': { icon: 'home-o', color: '#d94a5f' },
  '车贷': { icon: 'logistics', color: '#d94a5f' },

  // 交通
  '交通': { icon: 'logistics', color: '#4a88d9' },
  '地铁': { icon: 'logistics', color: '#4a88d9' },

  // 购物
  '购物': { icon: 'shopping-cart-o', color: '#c98ab6' },

  // 社交
  '社交': { icon: 'friends-o', color: '#8a9556' },

  // 工资
  '工资': { icon: 'gold-coin-o', color: '#67c23a' },

  // 主类别 fallback
  '生活开支': { icon: 'friends-o', color: '#8a9556' },

  // 应收款项 parent（借给别人）→ 青绿色 + 金币/手势
  '应收款项': { icon: 'cash-back-record', color: '#3dbab0' },

  // 应付款项（房贷场景）→ 粉红 + 房
  '应付款项': { icon: 'home-o', color: '#d94a5f' },

  // 收入
  '收入': { icon: 'gold-coin-o', color: '#67c23a' },

  // 转账三兄弟 → 棕色双向箭头
  '转账': { icon: 'exchange', color: '#a0663a' },
  '转出': { icon: 'exchange', color: '#a0663a' },
  '转入': { icon: 'exchange', color: '#a0663a' }
}

const DEFAULT_STYLE = { icon: 'notes-o', color: '#909399' }

const displayRecords = computed(() => {
  const result = []
  const used = new Set()

  for (const r of props.records) {
    if (used.has(r.id)) continue

    // Try pair match for 转出/转入
    if (r.recordType === '转出' || r.recordType === '转入') {
      const isOppositeDirection = (o) =>
        (r.recordType === '转出' && o.recordType === '转入')
        || (r.recordType === '转入' && o.recordType === '转出')

      // Strategy 1: 同 recurringEventId + 同 date + 反向
      let pair = null
      if (r.recurringEventId) {
        pair = props.records.find(o =>
          o.id !== r.id
          && !used.has(o.id)
          && o.recurringEventId === r.recurringEventId
          && o.date === r.date
          && isOppositeDirection(o)
        )
      }
      // Strategy 2: 同 date + 同 time + 同 abs amount + 反向
      if (!pair) {
        pair = props.records.find(o =>
          o.id !== r.id
          && !used.has(o.id)
          && o.date === r.date
          && o.time === r.time
          && Math.abs(Number(o.amount)) === Math.abs(Number(r.amount))
          && isOppositeDirection(o)
        )
      }
      if (pair) {
        used.add(r.id)
        used.add(pair.id)
        const outRec = r.recordType === '转出' ? r : pair
        const inRec = r.recordType === '转入' ? r : pair
        // Clone outRec but set target = in-side account so subLine shows "→target"
        result.push({ ...outRec, target: inRec.account })
        continue
      }
    }

    result.push(r)
  }

  return result
})

function isTransferType(recordType) {
  return recordType === '转账' || recordType === '转出' || recordType === '转入'
}

function styleOf(record) {
  if (isTransferType(record.recordType)) {
    return CATEGORY_STYLES[record.recordType] || DEFAULT_STYLE
  }
  return CATEGORY_STYLES[record.subCategory]
    || CATEGORY_STYLES[record.mainCategory]
    || CATEGORY_STYLES[record.recordType]
    || DEFAULT_STYLE
}

function displayName(record) {
  if (record.name) return record.name
  if (record.subCategory) return record.subCategory
  if (record.mainCategory) return record.mainCategory
  if (isTransferType(record.recordType)) return '转账'
  return '未命名'
}

function subLine(record) {
  // 周期记录
  if (record.recurringEventId) {
    const periodLabel = record.recurringTotalPeriods == null
      ? '无限期'
      : `共${record.recurringTotalPeriods}期`
    const transferTarget = record.target || record.recurringTargetAccount
    if (isTransferType(record.recordType) && transferTarget) {
      return `${record.account} → ${transferTarget}・周期 #${record.periodNumber || '?'} / ${periodLabel}`
    }
    const targetLabel = transferTarget ? `→${transferTarget}` : '不限定对象'
    return `周期 #${record.periodNumber || '?'} / ${periodLabel}・${targetLabel}`
  }
  // 分期记录
  if (record.installmentEventId) {
    return `分期 #${record.installmentPeriodNumber || '?'}`
  }
  // 非周期转账：显示流向
  if (isTransferType(record.recordType) && record.target) {
    return `${record.account} → ${record.target}`
  }
  // 应收/应付款项 parent：显示目标 or "不限定对象"
  if ((record.recordType === '应收款项' || record.recordType === '应付款项') && !record.parentRecordId) {
    return record.target ? `→${record.target}` : '不限定对象'
  }
  // 普通记录：显示商家（如果有）
  if (record.merchant) return record.merchant
  return ''
}

function tagsToShow(record) {
  // 转账（任何场景）→ 只显示"转账" tag
  if (isTransferType(record.recordType)) {
    return [{ type: 'category', text: '转账' }]
  }
  // 应收/应付 parent、周期、分期 → 只显示账户 tag
  const isReceivablePayableParent =
    (record.recordType === '应收款项' || record.recordType === '应付款项') && !record.parentRecordId
  if (isReceivablePayableParent || record.recurringEventId || record.installmentEventId) {
    return record.account ? [{ type: 'account', text: record.account }] : []
  }
  // 普通记录 → mainCategory + account
  const tags = []
  if (record.mainCategory) tags.push({ type: 'category', text: record.mainCategory })
  if (record.account) tags.push({ type: 'account', text: record.account })
  return tags
}

function amountClass(record) {
  const t = record.recordType
  const isChild = !!record.parentRecordId

  // 应付款项 parent = debt accrual on virtual asset acct (房产°) → green
  // child (actual repayment) → red
  if (t === '应付款项') return isChild ? 'amount-red' : 'amount-green'

  // 应收款项 parent = lending money out → red
  // child (receiving repayment) → green
  if (t === '应收款项') return isChild ? 'amount-green' : 'amount-red'

  // Income-like
  if (t === '收入' || t === '退款' || t === '折扣' || t === '返利回馈' || t === '转入') {
    return 'amount-green'
  }

  // Everything else (支出, 手续费, 利息, 分期还款, 转账, 转出, 还款, 余额调整, 账单分期) → red
  return 'amount-red'
}

function formatAmount(record) {
  const amt = Math.abs(Number(record.amount || 0))
  return `¥${amt.toFixed(2)}`
}
</script>

<style scoped>
.record-list {
  padding: 0;
}

.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  background: transparent;
}

.record-row:active {
  background: var(--bg-light);
}

.icon-circle {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.middle {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-line {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
  word-break: break-all;
}

.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.amount {
  font-size: 16px;
  font-weight: 600;
}

.amount-red {
  color: var(--profit-positive);
}

.amount-green {
  color: var(--profit-negative);
}

.amount-neutral {
  color: var(--text-primary);
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  line-height: 1.4;
  border: 1px solid;
  background: transparent;
}

.tag-category {
  border-color: #f57c00;
  color: #f57c00;
}

.tag-account {
  border-color: #1989fa;
  color: #1989fa;
}
</style>
