import api from '../index'

/**
 * Upload image for OCR recognition
 * @param {File} file - Image file
 * @returns {Promise} OCR result with recognized transaction details
 */
export function recognizeImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/snapledger/ocr', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * Confirm and save OCR recognized record
 * @param {Object} data - Record data to save
 * @returns {Promise} Saved record
 */
export function confirmOcr(data) {
  return api.post('/snapledger/ocr/confirm', data)
}
