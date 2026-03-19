import api from '../index'

export function getAccounts() {
  return api.get('/snapledger/accounts')
}

export function createAccount(data) {
  return api.post('/snapledger/accounts', data)
}
