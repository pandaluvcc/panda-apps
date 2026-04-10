// frontend/src/composables/useAccountForm.js
import { reactive, ref, computed } from 'vue'

export function useAccountForm() {
  const form = reactive({
    name: '',
    mainCurrency: 'CNY',
    accountGroup: '第三方支付',
    initialBalance: 0,
    billCycleStart: null,
    billCycleEnd: null,
    isCreditAccount: false,
    autoRollover: false,
    foreignTransactionFee: false,
    includeInTotal: true,
    remark: ''
  })

  // Track original values to detect unsaved changes
  const originalJson = ref(JSON.stringify(form))
  const isDirty = computed(() => JSON.stringify(form) !== originalJson.value)

  function snapshot() {
    originalJson.value = JSON.stringify(form)
  }

  function loadFromAccount(account) {
    form.name = account.name || ''
    form.mainCurrency = account.mainCurrency || 'CNY'
    form.accountGroup = account.accountGroup || '第三方支付'
    form.initialBalance = account.initialBalance ?? 0
    form.billCycleStart = account.billCycleStart || null
    form.billCycleEnd = account.billCycleEnd || null
    form.isCreditAccount = account.isCreditAccount || false
    form.autoRollover = account.autoRollover || false
    form.foreignTransactionFee = account.foreignTransactionFee || false
    form.includeInTotal = account.includeInTotal !== false
    form.remark = account.remark || ''
    snapshot()
  }

  function toPayload() {
    return {
      name: form.name,
      mainCurrency: form.mainCurrency,
      accountGroup: form.accountGroup,
      initialBalance: Number(form.initialBalance),
      billCycleStart: form.billCycleStart instanceof Date
        ? form.billCycleStart.toISOString().slice(0, 10)
        : form.billCycleStart || null,
      billCycleEnd: form.billCycleEnd instanceof Date
        ? form.billCycleEnd.toISOString().slice(0, 10)
        : form.billCycleEnd || null,
      isCreditAccount: form.isCreditAccount,
      autoRollover: form.autoRollover,
      foreignTransactionFee: form.foreignTransactionFee,
      includeInTotal: form.includeInTotal,
      remark: form.remark
    }
  }

  function validate() {
    if (!form.name || !form.name.trim()) {
      return '账户名称不能为空'
    }
    return null
  }

  return { form, isDirty, snapshot, loadFromAccount, toPayload, validate }
}
