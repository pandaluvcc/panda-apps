import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CalendarGrid from '@/components/snapledger/CalendarGrid.vue'

const stubs = {
  VanIcon: { template: '<i class="van-icon"></i>', props: ['name', 'size'] }
}

function mountGrid(props = {}) {
  return mount(CalendarGrid, {
    props: {
      year: 2026,
      month: 4,
      days: [],
      selectedDate: new Date(2026, 3, 23),
      ...props
    },
    global: { stubs }
  })
}

describe('CalendarGrid', () => {
  describe('网格填充', () => {
    it('2026/4 前置填充 3 天（3/29-3/31），后置填充 2 天（5/1-5/2）', () => {
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      expect(cells.length).toBe(35)

      expect(cells[0].text()).toBe('29')
      expect(cells[0].classes()).toContain('out-of-month')

      expect(cells[3].text()).toBe('4月')
      expect(cells[3].classes()).not.toContain('out-of-month')

      expect(cells[34].text()).toBe('02')
      expect(cells[34].classes()).toContain('out-of-month')
    })

    it('2026/1 前补 4 天跨年到 2025/12', () => {
      const wrapper = mountGrid({
        year: 2026, month: 1,
        selectedDate: new Date(2026, 0, 1)
      })
      const cells = wrapper.findAll('.day-cell')
      expect(cells[0].text()).toBe('28')
      expect(cells[0].classes()).toContain('out-of-month')
      expect(cells[4].text()).toBe('1月')
    })

    it('2025/12 后补 3 天跨年到 2026/1', () => {
      const wrapper = mountGrid({
        year: 2025, month: 12,
        selectedDate: new Date(2025, 11, 31)
      })
      const cells = wrapper.findAll('.day-cell')
      const last = cells[cells.length - 1]
      expect(last.text()).toBe('03')
      expect(last.classes()).toContain('out-of-month')
    })
  })

  describe('日期着色 class', () => {
    it('工作日+有记录 → weekday-strong', () => {
      const wrapper = mountGrid({
        days: [{ date: '2026-04-20', recordCount: 2, income: 0, expense: 0 }]
      })
      const cells = wrapper.findAll('.day-cell')
      const apr20 = cells.find(c => c.text() === '20')
      expect(apr20.classes()).toContain('weekday-strong')
    })

    it('工作日+无记录 → weekday-weak', () => {
      const wrapper = mountGrid({ days: [] })
      const apr21 = wrapper.findAll('.day-cell').find(c => c.text() === '21')
      expect(apr21.classes()).toContain('weekday-weak')
    })

    it('周日+无记录 → sunday-weak', () => {
      const wrapper = mountGrid({ days: [] })
      const apr5 = wrapper.findAll('.day-cell').find(c => c.text() === '05')
      expect(apr5.classes()).toContain('sunday-weak')
    })

    it('周六+非当月 → saturday-faint', () => {
      const wrapper = mountGrid()
      // 4/2 和 5/2 都渲染为 '02'，需要用 out-of-month class 筛出 5/2
      const may2 = wrapper.findAll('.day-cell').find(
        c => c.text() === '02' && c.classes().includes('out-of-month')
      )
      expect(may2.classes()).toContain('saturday-faint')
    })

    it('非当月填充位不可点击（emit select 被阻止）', async () => {
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      await cells[0].trigger('click')
      expect(wrapper.emitted('select')).toBeFalsy()
    })

    it('当月日期点击 → emit select(Date)', async () => {
      const wrapper = mountGrid()
      const apr15 = wrapper.findAll('.day-cell').find(c => c.text() === '15')
      await apr15.trigger('click')
      const emitted = wrapper.emitted('select')
      expect(emitted).toBeTruthy()
      expect(emitted[0][0]).toBeInstanceOf(Date)
      expect(emitted[0][0].getDate()).toBe(15)
    })
  })

  describe('今日圆圈', () => {
    it('当日是今天 → 加 is-today class', () => {
      const today = new Date()
      const wrapper = mountGrid({
        year: today.getFullYear(),
        month: today.getMonth() + 1,
        selectedDate: today
      })
      const cells = wrapper.findAll('.day-cell')
      const todayCell = cells.find(c => {
        const t = today.getDate() === 1
          ? `${today.getMonth() + 1}月`
          : String(today.getDate()).padStart(2, '0')
        return c.text() === t && !c.classes().includes('out-of-month')
      })
      expect(todayCell.classes()).toContain('is-today')
    })

    it('今日 = 选中时，只显示 is-today，不叠加 is-selected', () => {
      const today = new Date()
      const wrapper = mountGrid({
        year: today.getFullYear(),
        month: today.getMonth() + 1,
        selectedDate: today
      })
      const cells = wrapper.findAll('.day-cell')
      const todayCell = cells.find(c => {
        const t = today.getDate() === 1
          ? `${today.getMonth() + 1}月`
          : String(today.getDate()).padStart(2, '0')
        return c.text() === t && !c.classes().includes('out-of-month')
      })
      expect(todayCell.classes()).toContain('is-today')
      expect(todayCell.classes()).not.toContain('is-selected')
    })
  })

  describe('月首标记', () => {
    it('非当月填充位的 1 号不显示"X月"', () => {
      const wrapper = mountGrid()
      const cells = wrapper.findAll('.day-cell')
      const may1 = cells[cells.length - 2]
      expect(may1.classes()).toContain('out-of-month')
      expect(may1.text()).toBe('01')
    })

    it('六周月（当月跨 6 周）渲染 42 格', () => {
      const wrapper = mountGrid({
        year: 2026, month: 8,
        selectedDate: new Date(2026, 7, 1)
      })
      const cells = wrapper.findAll('.day-cell')
      expect(cells.length).toBe(42)
    })
  })
})
