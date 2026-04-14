<template>
  <div class="sub-account-manager">
    <!-- 子账户列表 -->
    <div v-if="subAccounts.length === 0" class="empty-tip">
      暂无子账户，点击下方按钮添加
    </div>
    <div v-else class="sub-list">
      <div
        v-for="sub in subAccounts"
        :key="sub.id"
        :class="['sub-item', { checked: selectedIds.includes(sub.id) }]"
        @click="toggleSelect(sub.id)"
      >
        <van-checkbox :modelValue="selectedIds.includes(sub.id)" @click.stop />
        <div class="sub-info">
          <span class="sub-name">{{ sub.name }}</span>
          <span class="sub-group">{{ sub.accountGroup }}</span>
        </div>
        <button class="sub-unlink" @click.stop="unlinkSingle(sub.id)">解绑</button>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="manager-footer">
      <button class="footer-btn footer-btn--add" @click="goAddSub">
        + 新增子账户
      </button>
      <div class="footer-actions">
        <button
          :class="['footer-btn', 'footer-btn--danger', { disabled: selectedIds.length === 0 }]"
          @click="batchUnlink"
          :disabled="selectedIds.length === 0"
        >
          解绑选中 ({{ selectedIds.length }})
        </button>
      </div>
    </div>

    <!-- 解绑确认弹窗 -->
    <van-dialog
      v-model:show="showConfirm"
      title="确认解绑"
      message="确定要解绑选中的子账户吗？"
      show-cancel-button
      @confirm="confirmUnlink"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getAccounts, batchUpdateSubAccounts } from '@/api'

const props = defineProps({
  masterId: {
    type: Number,
    required: true
  },
  masterName: {
    type: String,
    required: true
  }
})

const router = useRouter()
const allAccounts = ref([])
const selectedIds = ref([])
const showConfirm = ref(false)

onMounted(async () => {
  try {
    allAccounts.value = await getAccounts()
  } catch {
    allAccounts.value = []
  }
})

// 筛选出当前主账户的子账户
const subAccounts = computed(() => {
  return allAccounts.value.filter(acc =>
    acc.masterAccountName === props.masterName && !acc.isArchived
  )
})

function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx > -1) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function goAddSub() {
  router.push({
    path: '/snap/add',
    query: { masterId: props.masterId, masterName: props.masterName }
  })
}

function batchUnlink() {
  if (selectedIds.value.length === 0) return
  showConfirm.value = true
}

function unlinkSingle(id) {
  selectedIds.value = [id]
  showConfirm.value = true
}

async function confirmUnlink() {
  try {
    await batchUpdateSubAccounts(props.masterId, {
      action: 'UNLINK',
      subAccountIds: selectedIds.value
    })
    showToast('解绑成功')
    selectedIds.value = []
    // 刷新列表
    allAccounts.value = await getAccounts()
  } catch (e) {
    showToast('操作失败')
  }
}
</script>

<style scoped>
.sub-account-manager {
  padding: 12px 0;
}
.empty-tip {
  padding: 32px 16px;
  text-align: center;
  color: #999;
  font-size: 14px;
}
.sub-list {
  display: flex;
  flex-direction: column;
}
.sub-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.sub-item:active {
  background: #f0f0f0;
}
.sub-item.checked {
  background: #e8f4ff;
}
.sub-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sub-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}
.sub-group {
  font-size: 12px;
  color: #999;
}
.sub-unlink {
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid #f56c6c;
  background: #fff;
  color: #f56c6c;
  font-size: 12px;
  cursor: pointer;
}
.sub-unlink:active {
  background: #fef0f0;
}
.manager-footer {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.footer-btn {
  height: 44px;
  border-radius: 10px;
  border: none;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}
.footer-btn--add {
  background: #1989fa;
  color: #fff;
}
.footer-btn--danger {
  background: #fff;
  color: #f56c6c;
  border: 1px solid #f56c6c;
}
.footer-btn--danger.disabled {
  opacity: 0.5;
  pointer-events: none;
}
</style>
