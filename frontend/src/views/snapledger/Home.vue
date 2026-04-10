<template>
  <div class="accounts-overview">
    <!-- 顶部导航 -->
    <div class="nav-bar">
      <button class="nav-icon-btn" @click="amountVisible = !amountVisible">
        <svg v-if="amountVisible" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
          <line x1="1" y1="1" x2="23" y2="23"/>
        </svg>
      </button>
      <span class="nav-title">账户总览</span>
      <button class="nav-icon-btn" @click="$router.push('/snap/account/add')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
      </button>
    </div>

    <!-- 净值汇总 -->
    <div class="summary-section">
      <div class="currency-label">指定 CNY</div>
      <div class="net-worth" :class="netWorth >= 0 ? 'amount-positive' : 'amount-negative'">
        {{ amountVisible ? formatK(netWorth) : '****' }}
      </div>
      <div class="assets-debts-row">
        <span class="stat-item">
          总资产
          <span class="amount-positive">{{ amountVisible ? formatK(totalAssets) : '****' }}</span>
        </span>
        <span class="stat-divider"></span>
        <span class="stat-item">
          总负债
          <span class="amount-negative">{{ amountVisible ? formatK(Math.abs(totalDebts)) : '****' }}</span>
        </span>
      </div>
    </div>

    <!-- 账户分组列表 -->
    <div class="groups-container">
      <div v-if="loading" class="loading-state">
        <van-loading color="#999" size="20" />
      </div>

      <div v-else-if="accountGroups.length === 0" class="empty-state">
        <svg viewBox="0 0 64 64" fill="none" class="empty-icon">
          <circle cx="32" cy="32" r="28" stroke="#E0E0E0" stroke-width="2"/>
          <path d="M20 32h24M32 20v24" stroke="#E0E0E0" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p>暂无账户</p>
        <button class="add-account-btn" @click="$router.push('/snap/add')">添加账户</button>
      </div>

      <template v-else>
        <div v-for="group in accountGroups" :key="group.name" class="group-block">
          <div class="group-row" @click="toggleGroup(group.name)">
            <div class="group-left">
              <span class="expand-btn">{{ expandedGroups[group.name] ? '−' : '+' }}</span>
              <span class="group-name">{{ group.name }}</span>
              <span class="group-count">({{ group.accounts.length }})</span>
            </div>
            <span :class="['group-balance', group.balance >= 0 ? 'amount-positive' : 'amount-negative']">
              {{ amountVisible ? formatBalanceSign(group.balance) : '****' }}
            </span>
          </div>

          <transition name="slide-down">
            <div v-if="expandedGroups[group.name]" class="account-items">
              <div
                v-for="acc in group.accounts"
                :key="acc.id"
                class="account-row"
                style="cursor: pointer"
                @click="$router.push('/snap/account/' + acc.id)"
              >
                <span class="account-name">{{ acc.name }}</span>
                <span :class="['account-balance', (acc.balance || 0) >= 0 ? 'amount-positive' : 'amount-negative']">
                  {{ amountVisible ? formatBalanceSign(acc.balance || 0) : '****' }}
                </span>
              </div>
            </div>
          </transition>
        </div>
      </template>
    </div>

    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { getAccounts } from '@/api'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const activeTab = ref(0)
const amountVisible = ref(true)
const loading = ref(true)
const accounts = ref([])
const expandedGroups = reactive({})

// 分组排序权重
const GROUP_ORDER = ['第三方支付', '现金', '银行', '信用卡', '证券户', '基金', '投资', '贷款']

onMounted(async () => {
  try {
    const res = await getAccounts()
    accounts.value = res || []
  } catch (e) {
    console.error('Failed to load accounts:', e)
  } finally {
    loading.value = false
  }
})

// 账户分组逻辑
const accountGroups = computed(() => {
  const groupMap = {}

  for (const acc of accounts.value) {
    const isArchived = acc.isArchived === true
    const groupKey = isArchived ? '归档' : (acc.accountGroup || '其他')

    if (!groupMap[groupKey]) {
      groupMap[groupKey] = { name: groupKey, accounts: [], balance: 0 }
    }
    groupMap[groupKey].accounts.push(acc)
    if (acc.includeInTotal !== false || isArchived) {
      groupMap[groupKey].balance += acc.balance || 0
    }
  }

  // 排序：已知分组按 GROUP_ORDER，其他按字母，"其他"倒数第二，"归档"最后
  return Object.values(groupMap).sort((a, b) => {
    if (a.name === '归档') return 1
    if (b.name === '归档') return -1
    if (a.name === '其他') return 1
    if (b.name === '其他') return -1
    const ai = GROUP_ORDER.indexOf(a.name)
    const bi = GROUP_ORDER.indexOf(b.name)
    if (ai !== -1 && bi !== -1) return ai - bi
    if (ai !== -1) return -1
    if (bi !== -1) return 1
    return a.name.localeCompare(b.name)
  })
})

// 总资产：includeInTotal=true 且余额为正
const totalAssets = computed(() =>
  accounts.value
    .filter(a => a.includeInTotal !== false && !a.isArchived && (a.balance || 0) > 0)
    .reduce((sum, a) => sum + (a.balance || 0), 0)
)

// 总负债：includeInTotal=true 且余额为负
const totalDebts = computed(() =>
  accounts.value
    .filter(a => a.includeInTotal !== false && !a.isArchived && (a.balance || 0) < 0)
    .reduce((sum, a) => sum + (a.balance || 0), 0)
)

// 净值
const netWorth = computed(() => totalAssets.value + totalDebts.value)

function toggleGroup(name) {
  expandedGroups[name] = !expandedGroups[name]
}

function formatK(val) {
  const n = Number(val) || 0
  if (Math.abs(n) >= 10000) {
    return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'K'
  }
  return '¥' + n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function formatBalanceSign(val) {
  const n = Number(val) || 0
  const abs = Math.abs(n)
  const fmt = abs >= 10000
    ? (abs / 1000).toFixed(2).replace(/\.?0+$/, '') + 'K'
    : abs.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  if (n > 0) return '+¥' + fmt
  if (n < 0) return '−¥' + fmt
  return '¥0.00'
}
</script>

<style scoped>
.accounts-overview {
  min-height: 100vh;
  background: #F7F8FA;
  padding-bottom: 80px;
}

/* ── 顶部导航 ── */
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
  background: #F4F4F4;
  border: none;
  border-radius: 50%;
  color: #555;
  cursor: pointer;
  transition: background 0.15s;
}

.nav-icon-btn:active {
  background: #E8E8E8;
}

.nav-icon-btn svg {
  width: 20px;
  height: 20px;
}

/* ── 净值汇总 ── */
.summary-section {
  background: #FFFFFF;
  padding: 24px 20px 20px;
  margin-bottom: 8px;
}

.currency-label {
  font-size: 13px;
  color: #888888;
  margin-bottom: 4px;
  letter-spacing: 0.3px;
}

.net-worth {
  font-size: 44px;
  font-weight: 700;
  letter-spacing: -1px;
  line-height: 1.1;
  margin-bottom: 10px;
}

.assets-debts-row {
  display: flex;
  align-items: center;
  gap: 0;
  font-size: 13px;
  color: #666666;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.stat-item span {
  font-size: 14px;
  font-weight: 500;
}

.stat-divider {
  width: 1px;
  height: 14px;
  background: #E0E0E0;
  margin: 0 14px;
}

/* ── 颜色 ── */
.amount-positive { color: #00B96B; }
.amount-negative { color: #E53935; }

/* ── 分组列表 ── */
.groups-container {
  background: #FFFFFF;
}

.group-block {
  border-bottom: 1px solid #F2F2F2;
}

.group-block:last-child {
  border-bottom: none;
}

.group-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 52px;
  cursor: pointer;
  transition: background 0.1s;
}

.group-row:active {
  background: #FAFAFA;
}

.group-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.expand-btn {
  font-size: 18px;
  font-weight: 400;
  color: #AAAAAA;
  width: 20px;
  text-align: center;
  line-height: 1;
}

.group-name {
  font-size: 16px;
  font-weight: 500;
  color: #1A1A1A;
}

.group-count {
  font-size: 13px;
  color: #AAAAAA;
}

.group-balance {
  font-size: 16px;
  font-weight: 500;
}

/* ── 展开的账户行 ── */
.account-items {
  background: #FAFAFA;
  overflow: hidden;
}

.account-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 44px;
  height: 44px;
  border-top: 1px solid #F2F2F2;
}

.account-name {
  font-size: 14px;
  color: #555555;
}

.account-balance {
  font-size: 14px;
}

/* ── 展开动画 ── */
.slide-down-enter-active {
  animation: slideDown 0.2s ease-out;
}

.slide-down-leave-active {
  animation: slideDown 0.15s ease-in reverse;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── 加载 / 空态 ── */
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

.add-account-btn {
  margin-top: 4px;
  padding: 8px 24px;
  background: #1A1A1A;
  color: #FFFFFF;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}
</style>
