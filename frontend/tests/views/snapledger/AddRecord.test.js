import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import AddRecord from '@/views/snapledger/AddRecord.vue'

// Mock APIs
vi.mock('@/api', () => ({
  createRecord: vi.fn(),
  updateRecord: vi.fn(),
  deleteRecord: vi.fn(),
  getRecordById: vi.fn(),
  getCategories: vi.fn(),
  getAccounts: vi.fn()
}))

import { createRecord, getCategories, getAccounts } from '@/api'

// Mock vue-router
const mockPush = vi.fn()
const mockBack = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush,
    back: mockBack
  }),
  useRoute: () => ({
    params: {}
  })
}))

// Mock vant
vi.mock('vant', () => ({
  showConfirmDialog: vi.fn().mockResolvedValue(true),
  showToast: vi.fn()
}))

// Vant component stubs
const vanPopup = {
  template: '<div class="van-popup"><slot /></div>',
  props: ['show']
}

const vanTabs = {
  template: '<div class="van-tabs"><slot /></div>',
  props: ['active'],
  emits: ['change']
}

const vanTab = {
  template: '<div class="van-tab"><slot /></div>',
  props: ['title']
}

const vanGrid = {
  template: '<div class="van-grid"><slot /></div>',
  props: ['columnNum']
}

const vanGridItem = {
  template: '<div class="van-grid-item" @click="$emit(\'click\')"><slot /></div>',
  props: ['icon', 'text'],
  emits: ['click']
}

const vanLoading = {
  template: '<div class="van-loading">Loading...</div>'
}

const vanEmpty = {
  template: '<div class="van-empty"><slot /></div>',
  props: ['description']
}

const vanButton = {
  template: '<button class="van-button" @click="$emit(\'click\')"><slot /></button>',
  props: ['type', 'size', 'block'],
  emits: ['click']
}

const vanIcon = {
  template: '<i class="van-icon"></i>',
  props: ['name', 'size']
}

const vanTag = {
  template: '<span class="van-tag"><slot /></span>',
  props: ['type', 'size']
}

const vanDatePicker = {
  template: '<div class="van-date-picker"><slot /></div>',
  props: ['modelValue'],
  emits: ['confirm', 'cancel']
}

const vanTimePicker = {
  template: '<div class="van-time-picker"><slot /></div>',
  props: ['modelValue'],
  emits: ['confirm', 'cancel']
}

const vanPicker = {
  template: '<div class="van-picker"><slot /></div>',
  emits: ['confirm', 'cancel']
}

describe('AddRecord - Integration Test', () => {
  const mockCategories = [
    { id: 1, type: '支出', mainCategory: '饮食', subCategory: '早餐', icon: 'breakfast' },
    { id: 2, type: '支出', mainCategory: '饮食', subCategory: '午餐', icon: 'lunch' },
    { id: 3, type: '支出', mainCategory: '交通', subCategory: '地铁', icon: 'subway' },
    { id: 4, type: '收入', mainCategory: '工资', subCategory: '工资', icon: 'salary' }
  ]

  const globalStubs = {
    vanPopup,
    vanTabs,
    vanTab,
    vanGrid,
    vanGridItem,
    vanLoading,
    vanEmpty,
    vanButton,
    vanIcon,
    vanTag,
    vanDatePicker,
    vanTimePicker,
    vanPicker
  }

  beforeEach(() => {
    vi.clearAllMocks()
    getCategories.mockResolvedValue([...mockCategories])
    getAccounts.mockResolvedValue([])
    createRecord.mockResolvedValue({ id: 1 })
  })

  it('should complete the full add record flow', async () => {
    const wrapper = mount(AddRecord, {
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Wait for categories to load
    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    // Step 1: Verify initial state
    expect(wrapper.vm.form.recordType).toBe('支出')
    expect(wrapper.vm.form.amount).toBe('')
    expect(wrapper.vm.form.mainCategory).toBe('')

    // Step 2: Select a category (simulate CategoryGrid select event)
    await wrapper.vm.onCategorySelect({
      id: 1,
      type: '支出',
      mainCategory: '饮食',
      subCategory: '早餐',
      icon: 'breakfast'
    })
    await nextTick()

    expect(wrapper.vm.form.mainCategory).toBe('饮食')
    expect(wrapper.vm.form.subCategory).toBe('早餐')

    // Step 3: Enter amount
    wrapper.vm.form.amount = '25.5'
    await nextTick()

    expect(wrapper.vm.form.amount).toBe('25.5')

    // Step 4: Save the record
    await wrapper.vm.save()
    await nextTick()

    // Verify createRecord was called with correct data
    expect(createRecord).toHaveBeenCalled()
    const callData = createRecord.mock.calls[0][0]
    expect(callData.recordType).toBe('支出')
    expect(callData.mainCategory).toBe('饮食')
    expect(callData.subCategory).toBe('早餐')
    expect(callData.amount).toBe('25.5')

    // Verify navigation after save
    expect(mockPush).toHaveBeenCalledWith('/snap')
  })

  it('should show error when saving without amount', async () => {
    const { showToast } = await import('vant')

    const wrapper = mount(AddRecord, {
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Select category but no amount
    await wrapper.vm.onCategorySelect({
      id: 1,
      type: '支出',
      mainCategory: '饮食',
      subCategory: '早餐',
      icon: 'breakfast'
    })
    await nextTick()

    // Try to save without amount
    await wrapper.vm.save()

    expect(showToast).toHaveBeenCalledWith('请填写金额')
    expect(createRecord).not.toHaveBeenCalled()
  })

  it('should show error when saving without category', async () => {
    const { showToast } = await import('vant')

    const wrapper = mount(AddRecord, {
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Enter amount but no category
    wrapper.vm.form.amount = '100'
    await nextTick()

    // Try to save without category
    await wrapper.vm.save()

    expect(showToast).toHaveBeenCalledWith('请选择分类')
    expect(createRecord).not.toHaveBeenCalled()
  })

  it('should change record type and clear category', async () => {
    const wrapper = mount(AddRecord, {
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Select a category first
    await wrapper.vm.onCategorySelect({
      id: 1,
      type: '支出',
      mainCategory: '饮食',
      subCategory: '早餐',
      icon: 'breakfast'
    })
    await nextTick()

    expect(wrapper.vm.form.mainCategory).toBe('饮食')

    // Change record type
    wrapper.vm.changeRecordType('收入')
    await nextTick()

    expect(wrapper.vm.form.recordType).toBe('收入')
    expect(wrapper.vm.form.mainCategory).toBe('')
    expect(wrapper.vm.form.subCategory).toBe('')
  })

  it('should handle CategoryGrid with subcategories', async () => {
    const wrapper = mount(AddRecord, {
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Wait for categories to load
    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    // Find CategoryGrid component
    const categoryGrid = wrapper.findComponent({ name: 'CategoryGrid' })
    expect(categoryGrid.exists()).toBe(true)

    // Verify categories are passed correctly
    expect(categoryGrid.props('categories')).toEqual(mockCategories)
    expect(categoryGrid.props('recordType')).toBe('支出')
  })
})
