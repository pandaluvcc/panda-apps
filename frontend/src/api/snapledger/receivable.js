import api from '../index'

export function listReceivables(status = 'IN_PROGRESS', target) {
  const params = { status }
  if (target !== undefined) params.target = target
  return api.get('/snapledger/receivables', { params })
}

export function getReceivablesSummary() {
  return api.get('/snapledger/receivables/summary')
}

export function addReceivableChild(parentId, payload) {
  return api.post(`/snapledger/receivables/${parentId}/children`, payload)
}

export function createReceivable(payload) {
  return api.post('/snapledger/receivables', payload)
}

export function deleteReceivable(parentId) {
  return api.delete(`/snapledger/receivables/${parentId}`)
}

export function deleteReceivableChild(childId) {
  return api.delete(`/snapledger/receivables/children/${childId}`)
}
