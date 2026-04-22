<template>
  <div class="receivables-page">
    <div class="page-header">
      <van-icon name="arrow-left" class="back-btn" @click="$router.back()" />
      <span class="page-title">应收应付款项</span>
      <van-icon name="filter-o" class="filter-btn" @click="showFilter = true" />
    </div>

    <div class="tabs">
      <div
        v-for="t in tabs" :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="changeTab(t.value)">{{ t.label }}</div>
    </div>

    <div class="chips">
      <span class="chip active">全部</span>
      <span class="chip">{{ targetFilter || '不限定对象' }}</span>
    </div>

    <div class="summary-row" v-if="!loading && items.length > 0">
      <span class="summary-label">— 不限定对象 ({{ items.length }})</span>
      <span class="summary-amount" v-if="activeTab !== 'COMPLETED'">
        {{ formatSignedAmount(totalSigned) }}
      </span>
    </div>

    <div class="list" v-if="!loading && items.length > 0">
      <ReceivableRow
        v-for="item in items" :key="item.id"
        :item="item"
        :selected="selectedId === item.id"
        @click="handleRowClick"
      />
    </div>
    <div class="empty" v-else-if="!loading">暂无记录</div>
    <div class="loading" v-else>加载中…</div>

    <transition name="slide-up">
      <div class="action-bar" v-if="selectedItem && activeTab === 'IN_PROGRESS'">
        <span class="action-text">{{ actionLabel }}&nbsp;&nbsp;{{ formatSignedAmount(actionAmountSigned) }}</span>
        <button class="action-btn" @click="goAddChild">+</button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listReceivables } from '@/api/snapledger/receivable'
import ReceivableRow from '@/components/snapledger/ReceivableRow.vue'

const router = useRouter()
const TAB_KEY = 'snap.receivables.activeTab'
const tabs = [
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '已完成', value: 'COMPLETED' }
]

const activeTab = ref(sessionStorage.getItem(TAB_KEY) || 'IN_PROGRESS')
const items = ref([])
const loading = ref(false)
const selectedId = ref(null)
const showFilter = ref(false)
const targetFilter = ref('')

const selectedItem = computed(() => items.value.find(i => i.id === selectedId.value))

const totalSigned = computed(() => {
  return items.value.reduce((sum, item) => {
    const remaining = Number(item.remaining) || 0
    return sum + (item.recordType === '应付款项' ? -remaining : remaining)
  }, 0)
})
const summaryClass = computed(() => totalSigned.value < 0 ? 'negative' : 'positive')

const actionLabel = computed(() =>
  selectedItem.value?.recordType === '应收款项' ? '新增收款' : '新增还款')
const actionAmountSigned = computed(() => {
  if (!selectedItem.value) return 0
  const r = Number(selectedItem.value.remaining) || 0
  return selectedItem.value.recordType === '应付款项' ? -r : r
})

async function load() {
  loading.value = true
  try {
    const res = await listReceivables(activeTab.value)
    items.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error(e)
    items.value = []
  } finally {
    loading.value = false
  }
}

function changeTab(v) {
  if (activeTab.value === v) return
  activeTab.value = v
  sessionStorage.setItem(TAB_KEY, v)
  selectedId.value = null
}

function handleRowClick(item) {
  if (activeTab.value !== 'IN_PROGRESS') return
  selectedId.value = selectedId.value === item.id ? null : item.id
}

function goAddChild() {
  if (!selectedItem.value) return
  router.push(`/snap/receivables/${selectedItem.value.id}/new-child`)
}

function formatSignedAmount(n) {
  const v = Number(n || 0)
  const sign = v < 0 ? '−' : (v > 0 ? '+' : '')
  return `${sign}¥${Math.abs(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

onMounted(load)
watch(activeTab, load)
</script>

<style scoped>
.receivables-page {
  padding-bottom: 80px;
  background: #fff;
  min-height: 100vh;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
}
.back-btn, .filter-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  background: #f5f5f5;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 18px;
  cursor: pointer;
}
.page-title { flex: 1; text-align: center; font-size: 18px; font-weight: 500; }
.tabs {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}
.tab {
  flex: 1; text-align: center; padding: 12px;
  color: #999; font-size: 15px;
  cursor: pointer;
  position: relative;
}
.tab.active {
  color: #333; font-weight: 500;
}
.tab.active::after {
  content: '';
  position: absolute;
  left: 25%; right: 25%; bottom: 0;
  height: 2px; background: #4a90e2;
  border-radius: 2px;
}
.chips {
  padding: 12px 16px 4px;
  display: flex; gap: 8px;
}
.chip {
  padding: 4px 14px;
  background: #f0f0f0;
  border-radius: 12px;
  font-size: 13px;
  color: #999;
}
.chip.active {
  background: #d0e8ff;
  color: #4a90e2;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 16px;
  font-size: 15px;
  color: #666;
  border-bottom: 1px solid #eee;
  background: #fff;
}
.summary-amount { font-weight: 500; }
.summary-amount.negative { color: #e06969; }
.summary-amount.positive { color: #8fb94b; }
.empty, .loading {
  text-align: center;
  color: #999;
  padding: 60px 20px;
}
.action-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #eee;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 -2px 8px rgba(0,0,0,0.05);
  z-index: 100;
}
.action-text { font-size: 16px; color: #333; }
.action-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  background: #4a90e2;
  color: #fff;
  border: none;
  font-size: 24px;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.slide-up-enter-active, .slide-up-leave-active { transition: transform 0.25s; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(100%); }
</style>
