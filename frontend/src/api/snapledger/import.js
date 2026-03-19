import api from '../index'

export function importCsv(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/snapledger/import/csv', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
