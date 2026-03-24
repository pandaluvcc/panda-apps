import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import CategoryGrid from '@/components/snapledger/CategoryGrid.vue'

// Vant component stubs
const vanLoading = {
  template: '<div class="van-loading">Loading...</div>'
}

const vanEmpty = {
  template: '<div class="van-empty">{{ description }}<slot /></div>',
  props: ['description']
}

const vanButton = {
  template: '<button class="van-button" @click="$emit(\'click\')"><slot /></button>',
  props: ['size'],
  emits: ['click']
}

const vanIcon = {
  template: '<i class="van-icon"></i>',
  props: ['name', 'size']
}

describe('CategoryGrid', () => {
  const mockCategories = [
    { id: 1, type: '支出', mainCategory: '饮食', subCategory: '早餐', icon: 'breakfast' },
    { id: 2, type: '支出', mainCategory: '饮食', subCategory: '午餐', icon: 'lunch' },
    { id: 3, type: '支出', mainCategory: '交通', subCategory: '地铁', icon: 'subway' },
    { id: 4, type: '收入', mainCategory: '工资', subCategory: '工资', icon: 'salary' }
  ]

  const globalStubs = {
    vanLoading,
    vanEmpty,
    vanButton,
    vanIcon
  }

  it('should show loading state', () => {
    const wrapper = mount(CategoryGrid, {
      props: { loading: true },
      global: { stubs: globalStubs }
    })

    expect(wrapper.find('.van-loading').exists()).toBe(true)
  })

  it('should show error state', () => {
    const wrapper = mount(CategoryGrid, {
      props: { error: true },
      global: { stubs: globalStubs }
    })

    expect(wrapper.find('.van-empty').exists()).toBe(true)
  })

  it('should show empty state when no categories for current type', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '转账' // No categories for this type
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    expect(wrapper.find('.van-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无分类')
  })

  it('should display main categories for current record type', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    const items = wrapper.findAll('.grid-item')
    // Should have 2 main categories: 饮食, 交通
    expect(items.length).toBe(2)
  })

  it('should filter categories by record type', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '收入'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    const items = wrapper.findAll('.grid-item')
    // Should have 1 main category: 工资
    expect(items.length).toBe(1)
  })

  it('should emit select when clicking main category with single subcategory', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // 交通 has only one subcategory: 地铁
    // 饮食 is first, 交通 is second
    const items = wrapper.findAll('.grid-item')
    await items[1].trigger('click') // Click on 交通
    await nextTick()

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toMatchObject({
      type: '支出',
      mainCategory: '交通',
      subCategory: '地铁'
    })
  })

  it('should enter sub mode when main category has multiple subcategories', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // Click on 饮食 (has 2 subcategories)
    const items = wrapper.findAll('.grid-item')
    await items[0].trigger('click')
    await nextTick()

    // Should be in sub mode
    expect(wrapper.vm.categoryStep).toBe('sub')
    expect(wrapper.vm.selectedMainCategory).toBe('饮食')

    // Should show subcategories + back button
    const subItems = wrapper.findAll('.grid-item')
    expect(subItems.length).toBe(3) // back + 早餐 + 午餐
  })

  it('should emit select when clicking subcategory', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // Click on 饮食 to enter sub mode
    const items = wrapper.findAll('.grid-item')
    await items[0].trigger('click')
    await nextTick()

    // Click on 早餐 (second item, first is back)
    const subItems = wrapper.findAll('.grid-item')
    await subItems[1].trigger('click')
    await nextTick()

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toMatchObject({
      id: 1,
      type: '支出',
      mainCategory: '饮食',
      subCategory: '早餐'
    })
  })

  it('should go back to main mode when clicking back button', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // Enter sub mode
    const items = wrapper.findAll('.grid-item')
    await items[0].trigger('click')
    await nextTick()

    expect(wrapper.vm.categoryStep).toBe('sub')

    // Click back button
    await wrapper.find('.grid-item').trigger('click')
    await nextTick()

    expect(wrapper.vm.categoryStep).toBe('main')
    expect(wrapper.vm.selectedSubCategory).toBe(null)
  })

  it('should reset to main mode when recordType changes', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // Enter sub mode
    const items = wrapper.findAll('.grid-item')
    await items[0].trigger('click')
    await nextTick()

    expect(wrapper.vm.categoryStep).toBe('sub')

    // Change record type
    await wrapper.setProps({ recordType: '收入' })
    await nextTick()

    expect(wrapper.vm.categoryStep).toBe('main')
    expect(wrapper.vm.selectedMainCategory).toBe(null)
    expect(wrapper.vm.selectedSubCategory).toBe(null)
  })

  it('should highlight selected category', async () => {
    const wrapper = mount(CategoryGrid, {
      props: {
        categories: mockCategories,
        recordType: '支出'
      },
      global: { stubs: globalStubs }
    })

    await nextTick()

    // Select a category with single subcategory
    const items = wrapper.findAll('.grid-item')
    await items[1].trigger('click') // 交通
    await nextTick()

    // Check if selected class is applied
    expect(wrapper.find('.grid-item.selected').exists()).toBe(true)
  })
})
