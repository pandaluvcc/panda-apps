<template>
  <div class="add-account">
    <!-- 顶部导航 -->
    <div class="nav-bar">
      <button class="nav-btn" @click="handleCancel">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
      <span class="nav-title">新增账户</span>
      <button class="nav-btn confirm-nav-btn" @click="save" :disabled="saving">
        <van-loading v-if="saving" size="18" color="#1890FF" />
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      </button>
    </div>

    <div class="scroll-body">
      <!-- 账户图标 -->
      <div class="icon-section">
        <div class="account-icon-wrap">
          <svg viewBox="0 0 48 48" fill="none" class="account-svg">
            <rect x="6" y="14" width="36" height="24" rx="4" fill="#E8E8E8"/>
            <rect x="6" y="20" width="36" height="6" fill="#C8C8C8"/>
            <rect x="12" y="30" width="8" height="3" rx="1.5" fill="#AAAAAA"/>
          </svg>
        </div>
      </div>

      <!-- 余额展示 -->
      <div class="balance-display">
        <span class="balance-label">余额</span>
        <div class="balance-row">
          <span class="balance-currency">{{ form.mainCurrency }}</span>
          <span class="balance-value" :class="(form.initialBalance || 0) >= 0 ? 'amount-positive' : 'amount-negative'">
            {{ form.initialBalance || 0 }}
          </span>
        </div>
      </div>

      <!-- 备注输入 -->
      <div class="remark-section">
        <input
          v-model="form.remark"
          class="remark-input"
          placeholder="请在这里填写备注事项"
        />
      </div>

      <!-- 表单设置列表 -->
      <div class="settings-section">

        <!-- 名称 -->
        <div class="settings-row" @click="focusNameInput">
          <span class="row-label">名称</span>
          <div class="row-value-wrap">
            <input
              ref="nameInputRef"
              v-model="form.name"
              class="row-text-input"
              placeholder="尚未设置"
            />
          </div>
        </div>

        <!-- 主币种 -->
        <div class="settings-row" @click="showCurrencyPicker = true">
          <span class="row-label">主币种</span>
          <div class="row-value row-picker">
            <span>{{ form.mainCurrency }}</span>
            <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>
        </div>

        <!-- 账户分组 -->
        <div class="settings-row" @click="showGroupPicker = true">
          <span class="row-label">账户分组</span>
          <div class="row-value row-picker">
            <span>{{ form.accountGroup || '未分组' }}</span>
            <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>
        </div>

        <!-- 初始余额 -->
        <div class="settings-row">
          <span class="row-label">初始余额</span>
          <input
            v-model.number="form.initialBalance"
            class="row-number-input"
            type="number"
            placeholder="0"
            step="0.01"
          />
        </div>

        <!-- 账单周期 -->
        <div class="settings-row" @click="showBillCyclePicker = true">
          <span class="row-label">账单周期</span>
          <div class="row-value row-picker">
            <span class="bill-cycle-text">{{ billCycleDisplay }}</span>
            <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>
        </div>

        <div class="settings-divider"></div>

        <!-- 信用账户 -->
        <div class="settings-row">
          <span class="row-label">信用账户</span>
          <van-switch v-model="form.isCreditAccount" size="22px" active-color="#1890FF" />
        </div>

        <!-- 自动转存 -->
        <div class="settings-row">
          <span class="row-label">自动转存</span>
          <van-switch v-model="form.autoRollover" size="22px" active-color="#1890FF" />
        </div>

        <!-- 国外交易手续费 -->
        <div class="settings-row">
          <span class="row-label">国外交易手续费</span>
          <van-switch v-model="form.foreignTransactionFee" size="22px" active-color="#1890FF" />
        </div>

        <div class="settings-divider"></div>

        <!-- 纳入总余额 -->
        <div class="settings-row">
          <span class="row-label">纳入总余额</span>
          <van-switch v-model="form.includeInTotal" size="22px" active-color="#1890FF" />
        </div>

      </div>
    </div>

    <!-- 账户分组选择器 -->
    <AccountGroupPicker
      v-model:show="showGroupPicker"
      v-model="form.accountGroup"
    />

    <!-- 币种选择器 -->
    <van-popup v-model:show="showCurrencyPicker" position="bottom" round>
      <van-picker
        :columns="currencyColumns"
        :default-index="currencyColumns.findIndex(c => c.value === form.mainCurrency)"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
        title="主币种"
      />
    </van-popup>

    <!-- 账单周期选择器（月份选择） -->
    <van-popup v-model:show="showBillCyclePicker" position="bottom" round>
      <van-date-picker
        v-model="billCycleMonth"
        title="选择账单月份"
        :columns-type="['year', 'month']"
        @confirm="onBillCycleConfirm"
        @cancel="showBillCyclePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { createAccount } from '@/api'
import { showToast, showConfirmDialog } from 'vant'
import AccountGroupPicker from '@/components/snapledger/AccountGroupPicker.vue'
import { useAccountForm } from '@/composables/useAccountForm'

const router = useRouter()
const saving = ref(false)
const nameInputRef = ref(null)

const showGroupPicker = ref(false)
const showCurrencyPicker = ref(false)
const showBillCyclePicker = ref(false)

const { form, isDirty, validate, toPayload, snapshot } = useAccountForm()

// 默认账单周期为当月
const now = new Date()
form.billCycleStart = new Date(now.getFullYear(), now.getMonth(), 1)
form.billCycleEnd   = new Date(now.getFullYear(), now.getMonth() + 1, 0)

// 建立初始快照以确保 isDirty 从 false 开始
snapshot()

// 账单周期月份选择器值 [year, month]
const billCycleMonth = ref([
  String(now.getFullYear()),
  String(now.getMonth() + 1).padStart(2, '0')
])

const billCycleDisplay = computed(() => {
  const s = form.billCycleStart
  const e = form.billCycleEnd
  if (!s || !e) return '未设置'
  const fmt = (d) => `${d.getFullYear()}/${String(d.getMonth()+1).padStart(2,'0')}/${String(d.getDate()).padStart(2,'0')}`
  return `${fmt(s)} — ${fmt(e)}`
})

const currencyColumns = [
  { text: 'CNY - 人民币', value: 'CNY' },
  { text: 'USD - 美元', value: 'USD' },
  { text: 'EUR - 欧元', value: 'EUR' },
  { text: 'HKD - 港币', value: 'HKD' },
  { text: 'JPY - 日元', value: 'JPY' },
  { text: 'GBP - 英镑', value: 'GBP' },
]


function focusNameInput() {
  nextTick(() => nameInputRef.value?.focus())
}

function onCurrencyConfirm({ selectedOptions }) {
  form.mainCurrency = selectedOptions[0].value
  showCurrencyPicker.value = false
}

function onBillCycleConfirm({ selectedValues }) {
  const [year, month] = selectedValues.map(Number)
  form.billCycleStart = new Date(year, month - 1, 1)
  form.billCycleEnd   = new Date(year, month, 0)
  showBillCyclePicker.value = false
}

async function save() {
  const validationError = validate()
  if (validationError) {
    showToast(validationError)
    focusNameInput()
    return
  }

  saving.value = true
  try {
    const payload = {
      ...toPayload(),
      name: form.name.trim(),
      balance: form.initialBalance || 0,
      billCycleStart: form.billCycleStart?.toISOString().slice(0, 10),
      billCycleEnd:   form.billCycleEnd?.toISOString().slice(0, 10),
    }
    await createAccount(payload)
    showToast({ message: '账户已创建', type: 'success' })
    router.back()
  } catch (e) {
    console.error('Failed to create account:', e)
    showToast('创建失败，请重试')
  } finally {
    saving.value = false
  }
}

async function handleCancel() {
  if (isDirty.value) {
    try {
      await showConfirmDialog({ title: '提示', message: '确定放弃当前填写内容？' })
      router.back()
    } catch { /* 用户取消 */ }
  } else {
    router.back()
  }
}
</script>

<style scoped>
.add-account {
  min-height: 100vh;
  background: #F7F8FA;
  display: flex;
  flex-direction: column;
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
  background: #F7F8FA;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #1A1A1A;
}

.nav-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;
  border: none;
  border-radius: 50%;
  color: #555;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  transition: opacity 0.15s;
}

.nav-btn:active { opacity: 0.7; }
.nav-btn:disabled { opacity: 0.4; }
.nav-btn.confirm-nav-btn { color: #1890FF; }

.nav-btn svg {
  width: 20px;
  height: 20px;
}

/* ── 滚动区域 ── */
.scroll-body {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 32px;
}

/* ── 账户图标 ── */
.icon-section {
  display: flex;
  justify-content: center;
  padding: 20px 0 8px;
}

.account-icon-wrap {
  width: 80px;
  height: 80px;
  background: #EFEFEF;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.account-svg {
  width: 44px;
  height: 44px;
}

/* ── 余额展示 ── */
.balance-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 4px;
  gap: 2px;
}

.balance-label {
  font-size: 12px;
  color: #AAAAAA;
  letter-spacing: 0.5px;
}

.balance-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.balance-currency {
  font-size: 14px;
  font-weight: 500;
  color: #888888;
}

.balance-value {
  font-size: 42px;
  font-weight: 700;
  letter-spacing: -1px;
  line-height: 1;
}

.amount-positive { color: #00B96B; }
.amount-negative { color: #E53935; }

/* ── 备注 ── */
.remark-section {
  padding: 4px 24px 16px;
  text-align: center;
}

.remark-input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  text-align: center;
  font-size: 14px;
  color: #AAAAAA;
}

.remark-input::placeholder {
  color: #CCCCCC;
}

/* ── 设置列表 ── */
.settings-section {
  background: #FFFFFF;
  margin: 0 0 16px;
  border-top: 1px solid #F0F0F0;
  border-bottom: 1px solid #F0F0F0;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  padding: 0 20px;
  border-bottom: 1px solid #F5F5F5;
  gap: 12px;
}

.settings-row:last-child {
  border-bottom: none;
}

.row-label {
  font-size: 16px;
  color: #1A1A1A;
  white-space: nowrap;
  flex-shrink: 0;
}

.row-value {
  font-size: 15px;
  color: #888888;
  text-align: right;
}

.row-picker {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.chevron {
  width: 16px;
  height: 16px;
  color: #CCCCCC;
  flex-shrink: 0;
}

.row-value-wrap {
  flex: 1;
  text-align: right;
}

.row-text-input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  color: #1A1A1A;
  text-align: right;
}

.row-text-input::placeholder {
  color: #CCCCCC;
}

.row-number-input {
  background: transparent;
  border: none;
  outline: none;
  font-size: 15px;
  color: #1A1A1A;
  text-align: right;
  width: 120px;
}

.bill-cycle-text {
  font-size: 14px;
}

.settings-divider {
  height: 8px;
  background: #F7F8FA;
  border-top: 1px solid #F0F0F0;
  border-bottom: 1px solid #F0F0F0;
}
</style>
