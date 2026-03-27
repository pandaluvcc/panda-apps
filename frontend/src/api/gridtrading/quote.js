import api from '../index'

/**
 * 获取单个标的实时行情
 * @param {string} symbol - 标的代码，如 sh510500
 */
export const getQuote = (symbol) => {
  return api.get(`/quotes/${symbol}`)
}

/**
 * 批量获取实时行情
 * @param {string[]} symbols - 标的代码数组
 */
export const getQuotes = (symbols) => {
  return api.get('/quotes', {
    params: {
      symbols: symbols.join(',')
    }
  })
}
