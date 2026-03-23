import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import CategoryPicker from '@/components/snapledger/CategoryPicker.vue'

// Mock getCategories API
vi.mock('@/api', () => ({
  getCategories: vi.fn()
}))

import { getCategories } from '@/api'

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
  props: ['size'],
  emits: ['click']
}

describe('CategoryPicker', () => {
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
    vanButton
  }

  beforeEach(() => {
    vi.clearAllMocks()
    getCategories.mockResolvedValue([...mockCategories])
  })

  // Helper to mount and trigger show
  async function mountWithShow(options = {}) {
    const wrapper = mount(CategoryPicker, {
      props: { show: false, ...options.props },
      global: { stubs: globalStubs }
    })
    await nextTick()
    await wrapper.setProps({ show: true })
    await nextTick()
    return wrapper
  }

  it('should load categories when shown', async () => {
    await mountWithShow()
    expect(getCategories).toHaveBeenCalled()
  })

  it('should emit select event when subcategory is selected', async () => {
    const wrapper = await mountWithShow()

    // Wait for categories to load
    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    // Click on main category "饮食"
    await wrapper.findAll('.van-grid-item')[0].trigger('click')
    await nextTick()

    // Should be in sub mode, click on first subcategory (after "返回")
    const subItems = wrapper.findAll('.van-grid-item')
    await subItems[1].trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toMatchObject({
      mainCategory: '饮食',
      subCategory: '早餐'
    })
  })

  it('should reset to main mode when tab changes', async () => {
    const wrapper = await mountWithShow()

    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    // Select a main category
    await wrapper.findAll('.van-grid-item')[0].trigger('click')
    expect(wrapper.vm.categoryStep).toBe('sub')

    // Change tab
    wrapper.vm.activeTabIndex = 1 // Switch to "收入"
    wrapper.vm.onTabChange()
    await nextTick()

    expect(wrapper.vm.categoryStep).toBe('main')
    expect(wrapper.vm.selectedMainCategory).toBeNull()
  })

  it('should sync recordType prop with active tab', async () => {
    const wrapper = await mountWithShow({ props: { recordType: '收入' } })

    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    expect(wrapper.vm.activeTabIndex).toBe(1) // "收入" is at index 1
  })

  it('should emit update:recordType when tab changes', async () => {
    const wrapper = await mountWithShow()

    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    wrapper.vm.activeTabIndex = 1
    wrapper.vm.onTabChange()
    await nextTick()

    expect(wrapper.emitted('update:recordType')).toBeTruthy()
    expect(wrapper.emitted('update:recordType')[0][0]).toBe('收入')
  })

  it('should show all four record types in tabs', async () => {
    const wrapper = await mountWithShow()

    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    expect(wrapper.vm.recordTypes).toEqual(['支出', '收入', '转账', '应收账款'])
  })

  it('should deduplicate main categories', async () => {
    const wrapper = await mountWithShow()

    await vi.waitFor(() => {
      expect(wrapper.vm.categories.length).toBe(4)
    })

    // "饮食" appears twice in mock data but should only appear once in mainCategories
    const mainCatNames = wrapper.vm.mainCategories.map(c => c.name)
    expect(mainCatNames).toContain('饮食')
    expect(mainCatNames).toContain('交通')
    expect(mainCatNames.filter(n => n === '饮食').length).toBe(1)
  })
})
