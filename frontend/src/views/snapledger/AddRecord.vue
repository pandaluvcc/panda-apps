<template>
  <div class="add-record">
    <van-nav-bar
      :title="isEdit ? '编辑记录' : '记一笔'"
      left-arrow
      @click-left="$router.back()"
    />

    <RecordForm v-model="form" />

    <div class="actions">
      <van-button type="primary" block @click="save" :loading="saving">
        保存
      </van-button>
      <van-button v-if="isEdit" type="danger" block @click="remove" :loading="deleting">
        删除
      </van-button>
    </div>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createRecord, updateRecord, deleteRecord } from '@/api'
import RecordForm from '@/components/snapledger/RecordForm.vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const activeTab = ref(-1) // 记账页不在tab导航中，但显示底部栏

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: new Date().toISOString().split('T')[0],
  name: '',
  description: ''
})

const saving = ref(false)
const deleting = ref(false)

onMounted(async () => {
  if (isEdit.value) {
    // TODO: 加载现有记录
  }
})

async function save() {
  if (!form.value.amount || !form.value.mainCategory) {
    alert('请填写金额和分类')
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await updateRecord(route.params.id, form.value)
    } else {
      await createRecord(form.value)
    }
    router.push('/snap')
  } catch (e) {
    console.error('Failed to save:', e)
    alert('保存失败')
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!confirm('确定删除这条记录吗？')) return

  deleting.value = true
  try {
    await deleteRecord(route.params.id)
    router.push('/snap')
  } catch (e) {
    console.error('Failed to delete:', e)
    alert('删除失败')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.add-record {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 80px;
}

.actions {
  padding: 16px;
}
</style>
