<template>
  <div class="calendar-header">
    <div class="icon-btn" @click="onBellClick">
      <van-icon name="bell" size="22" />
    </div>
    <div class="title" @click="$emit('click-title')">
      {{ formattedDate }}
    </div>
    <div class="icon-btn" @click="onSearchClick">
      <van-icon name="search" size="22" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  selectedDate: { type: Date, required: true }
})

defineEmits(['click-title'])

const formattedDate = computed(() => {
  const d = props.selectedDate
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}/${m}/${day}`
})

function onBellClick() {
  showToast('通知功能开发中')
}

function onSearchClick() {
  showToast('搜索功能开发中')
}
</script>

<style scoped>
.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  background: var(--bg-white);
}

.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--bg-white);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-primary);
}

.icon-btn:active {
  background: var(--bg-light);
}

.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}
</style>
