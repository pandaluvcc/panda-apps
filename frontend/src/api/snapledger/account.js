import api from '../index'

export function getAccounts() {
  return api.get('/snapledger/accounts')
}

export function createAccount(data) {
  return api.post('/snapledger/accounts', data)
}

export function getAccount(id) {
  return api.get(`/snapledger/accounts/${id}`)
}

export function updateAccount(id, data) {
  return api.put(`/snapledger/accounts/${id}`, data)
}

export function getAccountTransactions(id, startDate, endDate) {
  return api.get(`/snapledger/accounts/${id}/transactions`, {
    params: { startDate, endDate }
  })
}

export function getAccountSummary(id, startDate, endDate) {
  return api.get(`/snapledger/accounts/${id}/summary`, {
    params: { startDate, endDate }
  })
}

export function batchUpdateSubAccounts(masterId, data) {
  return api.put(`/snapledger/accounts/${masterId}/sub-accounts/batch`, data)
}
