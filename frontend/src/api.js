/**
 * API 统一入口
 * 各业务模块 API 按目录隔离
 */

// 导出 axios 实例
export { default } from '@/api/index'

// ========== 网格交易 API ==========
export * from '@/api/gridtrading/strategy'
export * from '@/api/gridtrading/grid'
export * from '@/api/gridtrading/trade'
export * from '@/api/gridtrading/ocr'
export * from '@/api/gridtrading/suggestion'

// ========== 快记账 API（待开发） ==========
// export * from '@/api/snapledger/account'
// export * from '@/api/snapledger/record'
