import { describe, it, expect, vi } from 'vitest'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import Home from '@/views/gridtrading/Home.vue'

describe('GridHome navigation paths', () => {
  it('goToCreate should navigate to /grid/create', () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/grid', component: Home },
        { path: '/grid/create', component: { template: '<div>Create</div>' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'push')

    const pinia = createPinia()

    const wrapper = mount(Home, {
      global: {
        plugins: [router, pinia, ElementPlus]
      }
    })

    wrapper.vm.goToCreate()

    // Verify navigation - this will FAIL because current code pushes to /m/create
    expect(pushSpy).toHaveBeenCalledWith('/grid/create')
  })

  it('goToMessageCenter should navigate to /grid/messages', () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/grid', component: Home },
        { path: '/grid/messages', component: { template: '<div>Messages</div>' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'push')

    const pinia = createPinia()

    const wrapper = mount(Home, {
      global: {
        plugins: [router, pinia, ElementPlus]
      }
    })

    wrapper.vm.goToMessageCenter()

    // Verify navigation - this will FAIL because current code pushes to /m/messages
    expect(pushSpy).toHaveBeenCalledWith('/grid/messages')
  })

  it('goToDetail should navigate to /grid/strategy/:id', () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/grid', component: Home },
        { path: '/grid/strategy/:id', component: { template: '<div>Detail</div>' } }
      ]
    })

    const pushSpy = vi.spyOn(router, 'push')

    const pinia = createPinia()

    const wrapper = mount(Home, {
      global: {
        plugins: [router, pinia, ElementPlus]
      }
    })

    const testStrategy = { id: 123 }
    wrapper.vm.goToDetail(testStrategy)

    // Verify navigation - this will FAIL because current code pushes to /m/strategy/123
    expect(pushSpy).toHaveBeenCalledWith('/grid/strategy/123')
  })
})
