import api from '../index'

/**
 * List recurring events by status.
 * @param {string} status - ACTIVE or ENDED
 */
export function listRecurringEvents(status = 'ACTIVE') {
  return api.get('/snapledger/recurring-events', { params: { status } })
}

export function getRecurringEvent(id) {
  return api.get(`/snapledger/recurring-events/${id}`)
}

export function createRecurringEvent(data) {
  return api.post('/snapledger/recurring-events', data)
}

export function updateEntireRecurringEvent(id, data) {
  return api.put(`/snapledger/recurring-events/${id}`, data)
}

export function updateFromPeriod(id, fromPeriod, data) {
  return api.put(`/snapledger/recurring-events/${id}/from-period/${fromPeriod}`, data)
}

export function endRecurringEvent(id) {
  return api.post(`/snapledger/recurring-events/${id}/end`)
}

export function deleteRecurringEvent(id) {
  return api.delete(`/snapledger/recurring-events/${id}`)
}
