<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="category-picker">
      <van-tabs v-model:active="activeTab">
        <van-tab title="支出">
          <van-grid :column-num="4">
            <van-grid-item
              v-for="cat in expenseCategories"
              :key="cat.id"
              :text="cat.mainCategory"
              @click="selectCategory(cat)"
            />
          </van-grid>
        </van-tab>
        <van-tab title="收入">
          <van-grid :column-num="4">
            <van-grid-item
              v-for="cat in incomeCategories"
              :key="cat.id"
              :text="cat.mainCategory"
              @click="selectCategory(cat)"
            />
          </van-grid>
        </van-tab>
      </van-tabs>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getCategories } from '@/api'

const props = defineProps({
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'select'])

const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

const activeTab = ref(0)
const categories = ref([])

const expenseCategories = computed(() =>
  categories.value.filter(c => c.type === '支出')
)

const incomeCategories = computed(() =>
  categories.value.filter(c => c.type === '收入')
)

watch(() => props.show, async (show) => {
  if (show && categories.value.length === 0) {
    try {
      const res = await getCategories()
      categories.value = res.data || []
    } catch (e) {
      console.error('Failed to load categories:', e)
    }
  }
})

function selectCategory(cat) {
  emit('select', cat)
  visible.value = false
}
</script>

<style scoped>
.category-picker {
  max-height: 50vh;
  overflow-y: auto;
}
</style>
