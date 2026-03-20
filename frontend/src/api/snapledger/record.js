import api from '../index'

export function getRecords(page = 0, size = 20) {
  return api.get('/snapledger/records', { params: { page, size } })
}

export function getRecordsByDate(date) {
  return api.get(`/snapledger/records/date/${date}`)
}

export function getRecordsByMonth(year, month) {
  return api.get(`/snapledger/records/month/${year}/${month}`)
}

export function createRecord(data) {
  return api.post('/snapledger/records', data)
}

export function updateRecord(id, data) {
  return api.put(`/snapledger/records/${id}`, data)
}

export function deleteRecord(id) {
  return api.delete(`/snapledger/records/${id}`)
}
