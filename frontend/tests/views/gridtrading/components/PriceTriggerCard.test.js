import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PriceTriggerCard from '@/views/gridtrading/components/PriceTriggerCard.vue'

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

describe('PriceTriggerCard', () => {
  let wrapper
  let ElMessage

  beforeEach(async () => {
    ElMessage = (await import('element-plus')).ElMessage
    ElMessage.warning.mockClear()
  })

  const mountComponent = (props = {}) => {
    return mount(PriceTriggerCard, {
      props: {
        priceInput: '',
        ...props
      },
      global: {
        plugins: [ElementPlus]
      }
    })
  }

  describe('handleExecute validation', () => {
    it('should show warning and NOT emit execute when price is empty', async () => {
      wrapper = mountComponent({ priceInput: '' })

      // 找到执行按钮并点击
      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      // 应该显示警告
      expect(ElMessage.warning).toHaveBeenCalledWith('请输入价格')
      // 不应该触发 execute 事件
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is undefined', async () => {
      wrapper = mountComponent({ priceInput: undefined })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入价格')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is null', async () => {
      wrapper = mountComponent({ priceInput: null })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入价格')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is whitespace only', async () => {
      wrapper = mountComponent({ priceInput: '   ' })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入价格')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is zero as string', async () => {
      wrapper = mountComponent({ priceInput: '0' })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入有效的价格（必须大于 0）')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is zero as number', async () => {
      wrapper = mountComponent({ priceInput: 0 })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入价格')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should show warning and NOT emit execute when price is negative', async () => {
      wrapper = mountComponent({ priceInput: '-10' })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      expect(ElMessage.warning).toHaveBeenCalledWith('请输入有效的价格（必须大于 0）')
      expect(wrapper.emitted('execute')).toBeUndefined()
    })

    it('should emit execute with valid price when price is valid', async () => {
      wrapper = mountComponent({ priceInput: '100.5' })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      // 不应该显示警告
      expect(ElMessage.warning).not.toHaveBeenCalled()
      // 应该触发 execute 事件
      expect(wrapper.emitted('execute')).toBeTruthy()
      expect(wrapper.emitted('execute')[0]).toEqual(['100.5'])
    })

    it('should emit execute with valid price when price is valid number', async () => {
      wrapper = mountComponent({ priceInput: 100.5 })

      const executeBtn = wrapper.find('.execute-btn')
      await executeBtn.trigger('click')

      // 不应该显示警告
      expect(ElMessage.warning).not.toHaveBeenCalled()
      // 应该触发 execute 事件
      expect(wrapper.emitted('execute')).toBeTruthy()
      expect(wrapper.emitted('execute')[0]).toEqual([100.5])
    })
  })
})
