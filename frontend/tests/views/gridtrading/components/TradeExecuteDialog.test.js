import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TradeExecuteDialog from '@/views/gridtrading/components/TradeExecuteDialog.vue'

// Mock ElMessage
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      warning: vi.fn()
    }
  }
})

describe('TradeExecuteDialog', () => {
  let wrapper
  let ElMessage

  beforeEach(async () => {
    ElMessage = (await import('element-plus')).ElMessage
    ElMessage.warning.mockClear()
  })

  const mountComponent = (props = {}) => {
    return mount(TradeExecuteDialog, {
      props: {
        modelValue: true,
        price: '100.5',
        defaultQuantity: null,
        ...props
      },
      global: {
        plugins: [ElementPlus]
      }
    })
  }

  describe('default quantity population', () => {
    it('should populate quantity input with defaultQuantity when dialog opens in manual mode', () => {
      wrapper = mountComponent({ defaultQuantity: 700 })

      // 组件内部 vm 可以访问到 quantityInput
      // 验证默认值已经设置
      expect(wrapper.vm.quantityInput).toBe('700')
    })

    it('should populate quantity input with defaultQuantity as string when dialog opens', () => {
      wrapper = mountComponent({ defaultQuantity: '700' })

      expect(wrapper.vm.quantityInput).toBe('700')
    })

    it('should leave quantity input empty when defaultQuantity is null', () => {
      wrapper = mountComponent({ defaultQuantity: null })

      expect(wrapper.vm.quantityInput).toBe('')
    })

    it('should leave quantity input empty when defaultQuantity is zero', () => {
      wrapper = mountComponent({ defaultQuantity: 0 })

      expect(wrapper.vm.quantityInput).toBe('')
    })
  })
})
