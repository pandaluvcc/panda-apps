<template>
  <div class="sub-account-list">
    <!-- Top nav -->
    <div class="nav-bar">
      <button class="nav-icon-btn" @click="handleBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 18l-6-6 6-6"/>
        </svg>
      </button>
      <span class="nav-title">{{ masterName }}的子账户</span>
      <div style="width: 40px"/>
    </div>

    <!-- Sub account list -->
    <div v-if="loading" class="loading-state">
      <van-loading color="#409eff" size="24" />
    </div>

    <div v-else-if="subAccounts.length === 0" class="empty-state">
      <svg viewBox="0 0 64 64" fill="none" class="empty-icon">
        <circle cx="32" cy="32" r="28" stroke="#E0E0E0" stroke-width="2"/>
        <path d="M20 32h24M32 20v24" stroke="#E0E0E0" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <p>暂无子账户</p>
      <button class="add-btn" @click="goAddSub">添加子账户</button>
    </div>

    <div v-else class="list-container">
      <div
        v-for="sub in subAccounts"
        :key="sub.id"
        class="sub-account-card"
        @click="goToAccount(sub.id)"
      >
        <div class="sub-header">
          <span class="sub-name">{{ sub.name }}</span>
          <span class="sub-group">{{ sub.accountGroup }}</span>
        </div>
        <div class="sub-footer">
          <span class="sub-balance" :class="sub.balance >= 0 ? 'balance-positive' : 'balance-negative'">
            {{ formatBalance(sub.balance) }}
          </span>
          <span class="nav-arrow">›</span>
        </div>
      </div>
    </div>

    <!-- Add button (fab) -->
    <button v-if="subAccounts.length > 0" class="fab-add" @click="goAddSub">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccounts } from '@/api'

const route = useRoute()
const router = useRouter()

const masterId = Number(route.params.id)
const masterName = ref('')
const subAccounts = ref([])
const loading = ref(true)

// Format balance
function formatBalance(val) {
  const n = Number(val) || 0
  const sign = n >= 0 ? '+' : ''
  return sign + '¥' + n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function handleBack() {
  router.back()
}

function goToAccount(id) {
  router.push(`/snap/account/${id}`)
}

function goAddSub() {
  router.push({
    path: '/snap/add',
    query: { masterId, masterName: masterName.value }
  })
}

onMounted(async () => {
  try {
    const accounts = await getAccounts()
    // 通过 masterId 找到主账户名称（如果 route 中只有 id）
    const master = accounts.find(a => a.id === masterId)
    if (master) {
      masterName.value = master.name
    }
    // 筛选子账户
    subAccounts.value = accounts
      .filter(a => a.masterAccountName === masterName.value && !a.isArchived)
      .sort((a, b) => (a.sortOrder || 999) - (b.sortOrder || 999))
  } catch (e) {
    console.error('Failed to load sub accounts:', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.sub-account-list {
  min-height: 100vh;
  background: #F7F8FA;
  padding-bottom: 80px;
}

.nav-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 16px;
  background: #FFFFFF;
  border-bottom: 1px solid #F0F0F0;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #1A1A1A;
}

.nav-icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  color: #333;
}

.nav-icon-btn svg {
  width: 22px;
  height: 22px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
  gap: 12px;
  color: #AAAAAA;
  font-size: 14px;
}

.empty-icon {
  width: 56px;
  height: 56px;
}

.add-btn {
  margin-top: 4px;
  padding: 8px 24px;
  background: #1A1A1A;
  color: #FFFFFF;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}

.list-container {
  padding: 12px;
}

.sub-account-card {
  background: #FFF;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  transition: background 0.15s;
}

.sub-account-card:active {
  background: #F7F8FA;
}

.sub-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sub-name {
  font-size: 16px;
  font-weight: 500;
  color: #1A1A1A;
}

.sub-group {
  font-size: 12px;
  color: #999;
}

.sub-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sub-balance {
  font-size: 16px;
  font-weight: 600;
}

.balance-positive { color: #00B96B; }
.balance-negative { color: #E53935; }

.nav-arrow {
  font-size: 24px;
  color: #BBB;
}

.fab-add {
  position: fixed;
  bottom: 80px;
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #1989FA;
  color: #FFF;
  border: none;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.4);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fab-add svg {
  width: 24px;
  height: 24px;
}

.fab-add:active {
  background: #1677d6;
  transform: scale(0.95);
}
</style>
