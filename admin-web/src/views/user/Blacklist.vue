<template>
  <div class="page-card">
    <el-alert type="warning" :closable="false" show-icon class="tip">
      拉黑会同步把账号置为封禁（t_user.status=0）；解除拉黑会恢复账号可用。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="昵称">
        <el-input v-model="query.keyword" placeholder="用户昵称" clearable style="width: 200px" />
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="userId" label="用户ID" width="180" show-overflow-tooltip />
      <el-table-column prop="userName" label="昵称" min-width="160" show-overflow-tooltip />
      <el-table-column label="原因" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ [row.reasonType, row.reason].filter(Boolean).join('：') || '-' }}</template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column prop="createdAt" label="拉黑时间" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:user:blacklist'" link type="success" @click="release(row)">
            解除
          </el-button>
        </template>
      </el-table-column>
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { userApi } from '@/api/user'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', page: 1, size: 10 })

async function load () {
  loading.value = true
  try {
    const res = await userApi.blacklistPage(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; reload() }

async function release (row) {
  const ok = await ElMessageBox.confirm('解除后该账号恢复可用，确认？', '解除拉黑', { type: 'warning' })
    .catch(() => false)
  if (!ok) return
  await userApi.releaseBlacklist(row.userId)
  ElMessage.success('已解除')
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
</style>
