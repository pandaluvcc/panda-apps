<template>
  <van-cell-group inset>
    <van-cell
      v-for="record in records"
      :key="record.id"
      :title="record.name || record.mainCategory"
      :value="formatAmount(record)"
      :label="record.subCategory || record.mainCategory"
      :value-class="record.recordType === '收入' ? 'text-green' : 'text-red'"
      is-link
      @click="$emit('edit', record)"
    >
      <template #icon>
        <van-icon :name="getCategoryIcon(record.mainCategory)" class="mr-2" />
      </template>
    </van-cell>
    <van-empty v-if="records.length === 0" description="暂无记录" />
  </van-cell-group>
</template>

<script setup>
defineProps({
  records: { type: Array, default: () => [] }
})

defineEmits(['edit'])

function formatAmount(record) {
  const prefix = record.recordType === '收入' ? '+' : '-'
  return `${prefix}¥${record.amount}`
}

function getCategoryIcon(category) {
  const icons = {
    '餐饮': 'food',
    '交通': 'transport',
    '购物': 'shopping-cart',
    '娱乐': 'music',
    '医疗': 'hospital',
    '教育': 'education',
    '居住': 'home',
    '通讯': 'phone',
    '工资': 'gold-coin',
    '理财': 'balance-list'
  }
  return icons[category] || 'notes'
}
</script>

<style scoped>
.text-green { color: #07c160; }
.text-red { color: #ee0a24; }
.mr-2 { margin-right: 8px; }
</style>
