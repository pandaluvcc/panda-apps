import api from '../index'

/**
 * Get monthly statistics
 * @param {number} year
 * @param {number} month
 * @returns {Promise} Monthly stats with category breakdown
 */
export function getMonthlyStats(year, month) {
  return api.get(`/snapledger/stats/monthly/${year}/${month}`)
}

/**
 * Get category statistics
 * @param {number} year
 * @param {number} month
 * @param {string} type - 'income' or 'expense'
 * @returns {Promise} Category stats list
 */
export function getCategoryStats(year, month, type) {
  return api.get(`/snapledger/stats/category/${year}/${month}/${type}`)
}

/**
 * Get yearly statistics
 * @param {number} year
 * @returns {Promise} List of monthly stats
 */
export function getYearlyStats(year) {
  return api.get(`/snapledger/stats/yearly/${year}`)
}
