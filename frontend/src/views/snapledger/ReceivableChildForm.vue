<template>
  <div class="child-form-page">
    <div class="page-header">
      <van-icon name="cross" class="back-btn" @click="$router.back()" />
      <span class="page-title">{{ titleText }}</span>
      <van-icon name="success" class="submit-btn" :class="{ disabled: !canSubmit }" @click="submit" />
    </div>

    <div v-if="parent" class="parent-card">
      <div class="parent-icon" :style="{ background: isReceivable ? '#8fd7c6' : '#d78fa8' }">
        <span>¤</span>
      </div>
      <div class="parent-name">{{ parent.name }}</div>
      <div class="parent-amount" :class="isReceivable ? 'green' : 'red'">
        ¥{{ formatAmount(parent.absAmount) }}
      </div>
    </div>

    <div v-if="parent" class="fields">
      <div class="field">
        <label>金额</label>
        <input
          class="amount-input"
          v-model="form.amount"
          type="number"
          step="0.01"
          :max="parent.remaining"
          placeholder="0.00"
        />
      </div>
      <div class="field">
        <label>名称</label>
        <input :value="parent.name" disabled />
      </div>
      <div class="field">
        <label>账户</label>
        <select v-model="form.account">
          <option v-for="a in accounts" :key="a.id" :value="a.name">{{ a.name }}</option>
        </select>
      </div>
      <div class="field row">
        <div class="half">
          <label>日期</label>
          <input type="date" v-model="form.date" />
        </div>
        <div class="half">
          <label>时间</label>
          <input type="time" v-model="form.time" />
        </div>
      </div>
      <div class="field">
        <label>备注</label>
        <textarea v-model="form.description" rows="2"></textarea>
      </div>
    </div>

    <div v-if="parent" class="footer">
      剩余款项：¥{{ formatAmount(remaining) }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listReceivables, addReceivableChild } from '@/api/snapledger/receivable'
import { getAccounts } from '@/api'

const route = useRoute()
const router = useRouter()

const parent = ref(null)
const accounts = ref([])
const form = ref({
  amount: '',
  account: '',
  date: new Date().toISOString().substring(0, 10),
  time: new Date().toTimeString().substring(0, 5),
  description: ''
})

const isReceivable = computed(() => parent.value?.recordType === '应收款项')
const titleText = computed(() => isReceivable.value ? '新增收款' : '新增还款')

const remaining = computed(() => {
  if (!parent.value) return 0
  const r = Number(parent.value.remaining) || 0
  const amt = Number(form.value.amount) || 0
  return Math.max(0, r - amt)
})

const canSubmit = computed(() => {
  if (!parent.value) return false
  const a = Number(form.value.amount)
  return a > 0 && a <= Number(parent.value.remaining || 0)
})

async function load() {
  const parentId = Number(route.params.parentId)
  const list = await listReceivables('IN_PROGRESS')
  parent.value = (list || []).find(i => i.id === parentId)
  if (!parent.value) {
    alert('主记录不存在或已完成')
    router.back()
    return
  }
  form.value.amount = parent.value.remaining
  try {
    accounts.value = (await getAccounts()) || []
  } catch {
    accounts.value = []
  }
  form.value.account = parent.value.account
}

async function submit() {
  if (!canSubmit.value) return
  try {
    await addReceivableChild(parent.value.id, {
      account: form.value.account,
      amount: Number(form.value.amount),
      date: form.value.date,
      time: form.value.time + ':00',
      description: form.value.description
    })
    router.back()
  } catch (e) {
    alert('提交失败：' + (e?.message || e))
  }
}

function formatAmount(n) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(load)
</script>

<style scoped>
.child-form-page {
  background: #fff;
  min-height: 100vh;
  padding-bottom: 60px;
}
.page-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
}
.back-btn, .submit-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  background: #f5f5f5;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 18px;
  cursor: pointer;
}
.submit-btn.disabled { opacity: 0.35; pointer-events: none; }
.page-title { flex: 1; text-align: center; font-size: 18px; font-weight: 500; }
.parent-card {
  text-align: center;
  padding: 24px 16px 16px;
}
.parent-icon {
  width: 60px; height: 60px;
  border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff;
  font-size: 28px;
}
.parent-name {
  margin-top: 8px;
  font-size: 13px;
  color: #555;
}
.parent-amount {
  margin-top: 2px;
  font-size: 14px;
}
.parent-amount.green { color: #8fb94b; }
.parent-amount.red { color: #e06969; }
.fields {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 12px; color: #999; }
.field input, .field select, .field textarea {
  padding: 10px 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  font-size: 16px;
  background: #fafafa;
}
.field input:disabled { color: #999; }
.amount-input { font-size: 22px !important; font-weight: 500; }
.field.row { flex-direction: row; gap: 12px; }
.field.row .half { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.footer {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  padding: 14px;
  text-align: center;
  color: #999;
  border-top: 1px solid #eee;
  background: #fff;
  font-size: 13px;
}
</style>
