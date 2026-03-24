import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, ref, watch } from 'vue'
import RecordForm from '@/components/snapledger/RecordForm.vue'

// Mock getAccounts API
vi.mock('@/api', () => ({
  getAccounts: vi.fn()
}))

import { getAccounts } from '@/api'

// Vant component stubs
const vanPopup = {
  template: '<div class="van-popup"><slot /></div>',
  props: ['show']
}

const vanPicker = {
  template: '<div class="van-picker"><slot /></div>',
  emits: ['confirm', 'cancel']
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

const vanTag = {
  template: '<span class="van-tag"><slot /></span>',
  props: ['type', 'size']
}

const vanIcon = {
  template: '<i class="van-icon"></i>',
  props: ['name', 'size']
}

const vanButton = {
  template: '<button class="van-button" @click="$emit(\'click\')"><slot /></button>',
  props: ['type', 'size'],
  emits: ['click']
}

describe('RecordForm', () => {
  const globalStubs = {
    vanPopup,
    vanPicker,
    vanDatePicker,
    vanTimePicker,
    vanTag,
    vanIcon,
    vanButton
  }

  beforeEach(() => {
    vi.clearAllMocks()
    getAccounts.mockResolvedValue([])
  })

  it('should not cause recursive updates when modelValue changes', async () => {
    // This test verifies the fix for the recursive update bug
    // The bug was caused by:
    // 1. watch(form, { deep: true }) emits update:modelValue
    // 2. watch(props.modelValue, { immediate: true, deep: true }) updates form
    // 3. This creates an infinite loop because deep: true triggers on nested changes

    let updateCount = 0
    const maxAllowedUpdates = 10 // Should be much less in normal operation

    const initialModelValue = {
      recordType: '支出',
      amount: '',
      mainCategory: '',
      subCategory: '',
      account: '',
      date: '2024-01-01',
      time: '',
      name: '',
      project: '',
      merchant: '',
      count: null,
      tags: [],
      description: ''
    }

    // Create a wrapper variable that can be accessed in the callback
    let wrapperRef = null

    const wrapper = mount(RecordForm, {
      props: {
        modelValue: initialModelValue,
        'onUpdate:modelValue': (newValue) => {
          updateCount++
          if (updateCount <= maxAllowedUpdates && wrapperRef) {
            // Simulate parent updating the prop
            wrapperRef.setProps({ modelValue: newValue })
          }
        }
      },
      global: {
        stubs: globalStubs
      }
    })

    wrapperRef = wrapper

    await nextTick()

    // Wait for any potential recursive updates to complete
    await new Promise(resolve => setTimeout(resolve, 200))
    await nextTick()

    // If there's a recursive update bug, updateCount would be very high
    expect(updateCount).toBeLessThan(maxAllowedUpdates)
  })

  it('should not trigger update when same values are synced back', async () => {
    // This is the core issue: when props.modelValue changes,
    // the watch updates form.value which triggers emit,
    // but the values are the same - this should not cause infinite loop

    const initialModelValue = {
      recordType: '支出',
      amount: '100',
      mainCategory: '饮食',
      subCategory: '早餐',
      account: '',
      date: '2024-01-01',
      time: '',
      name: '',
      project: '',
      merchant: '',
      count: null,
      tags: [],
      description: ''
    }

    const wrapper = mount(RecordForm, {
      props: {
        modelValue: initialModelValue
      },
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Clear emitted events from mount
    const emittedBefore = wrapper.emitted('update:modelValue')?.length || 0

    // Set the same value again (simulating parent re-render)
    await wrapper.setProps({ modelValue: { ...initialModelValue } })
    await nextTick()

    const emittedAfter = wrapper.emitted('update:modelValue')?.length || 0

    // Should not emit again for same values
    expect(emittedAfter).toBe(emittedBefore)
  })

  it('should sync recordType from parent to form', async () => {
    const wrapper = mount(RecordForm, {
      props: {
        modelValue: {
          recordType: '支出',
          amount: '',
          mainCategory: '',
          subCategory: '',
          account: '',
          date: '2024-01-01',
          time: '',
          name: '',
          project: '',
          merchant: '',
          count: null,
          tags: [],
          description: ''
        }
      },
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Update from parent
    await wrapper.setProps({
      modelValue: {
        ...wrapper.props('modelValue'),
        recordType: '收入'
      }
    })
    await nextTick()

    expect(wrapper.vm.form.recordType).toBe('收入')
  })

  it('should emit update:modelValue when form changes', async () => {
    const wrapper = mount(RecordForm, {
      props: {
        modelValue: {
          recordType: '支出',
          amount: '',
          mainCategory: '',
          subCategory: '',
          account: '',
          date: '2024-01-01',
          time: '',
          name: '',
          project: '',
          merchant: '',
          count: null,
          tags: [],
          description: ''
        }
      },
      global: {
        stubs: globalStubs
      }
    })

    await nextTick()

    // Clear any initial emits
    wrapper.clearEmitted?.()

    // Change form value
    wrapper.vm.form.amount = '100'
    await nextTick()

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })
})
