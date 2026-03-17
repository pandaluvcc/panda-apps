import { describe, it, expect, vi } from 'vitest'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import StrategyCreate from '@/views/gridtrading/StrategyCreate.vue'
import { createStrategy } from '@/api/gridtrading/strategy'
import { ocrCreateStrategy } from '@/api/gridtrading/ocr'

// Mock the API calls
vi.mock('@/api/gridtrading/strategy', () => ({
  createStrategy: vi.fn()
}))

vi.mock('@/api/gridtrading/ocr', () => ({
  ocrCreateStrategy: vi.fn()
}))

describe('StrategyCreate navigation', () => {
  it('after successful creation should navigate to /grid/strategy/:id', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/grid/create', component: StrategyCreate },
        { path: '/grid/strategy/:id', component: { template: '<div>Detail</div>' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'replace')

    const pinia = createPinia()

    // Mock successful API response
    const mockStrategyId = 123
    createStrategy.mockResolvedValue({
      data: {
        id: mockStrategyId
      }
    })

    const wrapper = mount(StrategyCreate, {
      global: {
        plugins: [router, pinia, ElementPlus]
      }
    })

    // Set form data to make it valid
    wrapper.vm.form.name = 'Test Strategy'
    wrapper.vm.form.symbol = 'BTC/USDT'
    wrapper.vm.form.basePrice = 10000
    wrapper.vm.form.amountPerGrid = 100

    // Submit form
    await wrapper.vm.handleSubmit()

    // Wait for setTimeout
    await new Promise(resolve => setTimeout(resolve, 350))

    // Verify that router.replace was called with correct path
    expect(pushSpy).toHaveBeenCalledWith(`/grid/strategy/${mockStrategyId}`)
  })

  it('after successful OCR import should navigate to /grid/strategy/:id', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/grid/create', component: StrategyCreate },
        { path: '/grid/strategy/:id', component: { template: '<div>Detail</div>' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'replace')

    const pinia = createPinia()

    // Mock successful API response
    const mockStrategyId = 456
    ocrCreateStrategy.mockResolvedValue({
      data: {
        id: mockStrategyId
      }
    })

    const wrapper = mount(StrategyCreate, {
      global: {
        plugins: [router, pinia, ElementPlus]
      }
    })

    // Set form data and add import files
    wrapper.vm.form.name = 'OCR Test Strategy'
    wrapper.vm.form.symbol = 'ETH/USDT'
    wrapper.vm.importFiles = [new File([''], 'test1.png'), new File([''], 'test2.png')]

    // Submit import
    await wrapper.vm.submitImport()

    // Wait for setTimeout
    await new Promise(resolve => setTimeout(resolve, 350))

    // Verify that router.replace was called with correct path
    expect(pushSpy).toHaveBeenCalledWith(`/grid/strategy/${mockStrategyId}`)
  })
})
