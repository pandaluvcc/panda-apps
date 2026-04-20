import api from '../index'

/**
 * List installment events by status.
 * @param {string} status - ACTIVE or ENDED
 */
export function listInstallmentEvents(status = 'ACTIVE') {
  return api.get('/snapledger/installment-events', { params: { status } })
}

export function getInstallmentEvent(id) {
  return api.get(`/snapledger/installment-events/${id}`)
}

export function redetectInstallments() {
  return api.post('/snapledger/installment-events/detect')
}
