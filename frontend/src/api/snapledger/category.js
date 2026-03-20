import api from '../index'

export function getCategories() {
  return api.get('/snapledger/categories')
}

export function getCategoriesByType(type) {
  return api.get(`/snapledger/categories/type/${type}`)
}

export function getMainCategories(recordType) {
  return api.get(`/snapledger/categories/main-categories/${recordType}`)
}

export function getSubCategories(mainCategory) {
  return api.get(`/snapledger/categories/sub-categories/${encodeURIComponent(mainCategory)}`)
}

export function createCategory(data) {
  return api.post('/snapledger/categories', data)
}
