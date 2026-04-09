import api from '../index'

export function getAccountGroups() {
  return api.get('/snapledger/account-groups')
}

export function createAccountGroup(name) {
  return api.post('/snapledger/account-groups', { name })
}

export function deleteAccountGroup(id) {
  return api.delete(`/snapledger/account-groups/${id}`)
}
