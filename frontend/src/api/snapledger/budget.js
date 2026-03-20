import api from '../index'

/**
 * Get budget for specified month
 * @param {number} year
 * @param {number} month
 * @returns {Promise} Budget data with spent/remaining
 */
export function getBudget(year, month) {
  return api.get(`/snapledger/budget/${year}/${month}`)
}

/**
 * Set or update budget
 * @param {Object} data - { year, month, amount }
 * @returns {Promise} Updated budget
 */
export function setBudget(data) {
  return api.post('/snapledger/budget', data)
}
