import { createRouter, createWebHistory } from 'vue-router'

// 应用首页
import AppHome from '@/views/AppHome.vue'

// 网格交易页面
import GridHome from '@/views/gridtrading/Home.vue'
import GridRecord from '@/views/gridtrading/Record.vue'
import GridHistory from '@/views/gridtrading/History.vue'
import GridStrategyCreate from '@/views/gridtrading/StrategyCreate.vue'
import GridStrategyDetail from '@/views/gridtrading/StrategyDetail.vue'
import GridMessageCenter from '@/views/gridtrading/MessageCenter.vue'

// 工具页面
import ApkFolderGenerator from '@/views/tools/ApkFolderGenerator.vue'

// 快记账页面
import SnapHome from '@/views/snapledger/Home.vue'
import SnapAddRecord from '@/views/snapledger/AddRecord.vue'
import SnapAddAccount from '@/views/snapledger/AddAccount.vue'
import SnapCalendar from '@/views/snapledger/Calendar.vue'
import SnapImport from '@/views/snapledger/Import.vue'
import SnapScan from '@/views/snapledger/Scan.vue'
import SnapStats from '@/views/snapledger/Stats.vue'
import SnapBudget from '@/views/snapledger/Budget.vue'
import SnapAccountDetail from '@/views/snapledger/AccountDetail.vue'
import SnapSubAccountList from '@/views/snapledger/SubAccountList.vue'
import SnapMore from '@/views/snapledger/More.vue'
import SnapRecurringEvents from '@/views/snapledger/RecurringEvents.vue'
import SnapInstallmentEvents from '@/views/snapledger/InstallmentEvents.vue'

const routes = [
  // 根路由 - 应用首页
  {
    path: '/',
    name: 'AppHome',
    component: AppHome,
    meta: { transition: 'page-fade' }
  },

  // ========== 网格交易路由 ==========
  {
    path: '/grid',
    name: 'GridHome',
    component: GridHome,
    meta: { module: 'gridtrading', transition: 'page-fade' }
  },
  {
    path: '/grid/create',
    name: 'GridStrategyCreate',
    component: GridStrategyCreate,
    meta: { module: 'gridtrading', transition: 'page-scale' }
  },
  {
    path: '/grid/strategy/:id',
    name: 'GridStrategyDetail',
    component: GridStrategyDetail,
    meta: { module: 'gridtrading', transition: 'page-slide' }
  },
  {
    path: '/grid/record',
    name: 'GridRecord',
    component: GridRecord,
    meta: { module: 'gridtrading', transition: 'page-fade' }
  },
  {
    path: '/grid/history',
    name: 'GridHistory',
    component: GridHistory,
    meta: { module: 'gridtrading', transition: 'page-fade' }
  },
  {
    path: '/grid/messages',
    name: 'GridMessageCenter',
    component: GridMessageCenter,
    meta: { module: 'gridtrading', transition: 'page-slide' }
  },

  // ========== 快记账路由 ==========
  {
    path: '/snap',
    name: 'SnapHome',
    component: SnapHome,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/add',
    name: 'SnapAddRecord',
    component: SnapAddRecord,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/account/add',
    name: 'SnapAddAccount',
    component: SnapAddAccount,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/account/:id',
    name: 'SnapAccountDetail',
    component: SnapAccountDetail,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/account/:id/sub-accounts',
    name: 'SnapSubAccountList',
    component: SnapSubAccountList,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/edit/:id',
    name: 'SnapEditRecord',
    component: SnapAddRecord,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/calendar',
    name: 'SnapCalendar',
    component: SnapCalendar,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/import',
    name: 'SnapImport',
    component: SnapImport,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/scan',
    name: 'SnapScan',
    component: SnapScan,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/stats',
    name: 'SnapStats',
    component: SnapStats,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/budget',
    name: 'SnapBudget',
    component: SnapBudget,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/more',
    name: 'SnapMore',
    component: SnapMore,
    meta: { module: 'snapledger', transition: 'page-fade' }
  },
  {
    path: '/snap/events/recurring',
    name: 'SnapRecurringEvents',
    component: SnapRecurringEvents,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },
  {
    path: '/snap/events/installment',
    name: 'SnapInstallmentEvents',
    component: SnapInstallmentEvents,
    meta: { module: 'snapledger', transition: 'page-slide' }
  },

  // ========== 工具路由 ==========
  {
    path: '/tools/apk-folder',
    name: 'ApkFolderGenerator',
    component: ApkFolderGenerator,
    meta: { transition: 'page-fade' }
  },

  // 404 重定向
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory('/panda-apps/'),
  routes
})

export default router
