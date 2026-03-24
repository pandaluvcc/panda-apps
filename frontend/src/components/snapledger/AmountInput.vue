<template>
  <div class="amount-input">
    <div class="currency-badge">CNY</div>
    <input
      type="number"
      class="amount-field"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      placeholder="0"
    />
    <div class="amount-buttons">
      <button class="amount-btn" @mousedown="startDecrement" @mouseup="stopTimer" @mouseleave="stopTimer">
        <van-icon name="minus" size="16" />
      </button>
      <button class="amount-btn" @mousedown="startIncrement" @mouseup="stopTimer" @mouseleave="stopTimer">
        <van-icon name="plus" size="16" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' }
})

const emit = defineEmits(['update:modelValue'])

const timer = ref(null)

function increment() {
  const val = Number(props.modelValue) || 0
  emit('update:modelValue', val + 1)
}

function decrement() {
  const val = Number(props.modelValue) || 0
  emit('update:modelValue', Math.max(0, val - 1))
}

function startIncrement() {
  increment()
  timer.value = setInterval(increment, 100)
}

function startDecrement() {
  decrement()
  timer.value = setInterval(decrement, 100)
}

function stopTimer() {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}
</script>

<style scoped>
.amount-input {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #FFFFFF;
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
}

.currency-badge {
  flex-shrink: 0;
  padding: 8px;
  background: #F5F5F5;
  border-radius: 4px;
  font-size: 14px;
  color: #666666;
}

.amount-field {
  flex: 1;
  min-width: 0;
  font-size: 28px;
  font-weight: 600;
  color: #000000;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 12px;
  text-align: right;
  box-sizing: border-box;
}

.amount-field::placeholder {
  color: #CCCCCC;
}

.amount-buttons {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

.amount-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  color: #666666;
  cursor: pointer;
}

.amount-btn:active {
  background: #E0E0E0;
}
</style>
