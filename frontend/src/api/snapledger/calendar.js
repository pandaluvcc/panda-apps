import api from '../index'

export function getMonthCalendar(year, month) {
  return api.get(`/snapledger/calendar/${year}/${month}`)
}
