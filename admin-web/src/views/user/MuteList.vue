<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      这里是禁言的事实源。到期记录由定时任务自动解除，也可以手动扫描一次。
    </el-alert>

    <div class="toolbar">
      <el-button v-permission="'admin:user:mute'" type="primary" :icon="Refresh" @click="scanExpired">
        扫描到期禁言
      </el-button>
    </div>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="userId" label="用户ID" width="180" show-overflow-tooltip />
      <el-table-column label="范围" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(MUTE_SCOPE, row.scope).type" size="small">
            {{ tagOf(MUTE_SCOPE, row.scope).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时长" width="110" align="center">
        <template #default="{ row }">{{ row.minutes === 0 ? '永久' : row.minutes + ' 分钟' }}</template>
      </el-table-column>
      <el-table-column label="有效期" width="320">
        <template #default="{ row }">{{ row.startAt }} → {{ row.endAt }}</template>
      </el-table-column>
      <el-table-column label="原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ [row.reasonType, row.reason].filter(Boolean).join('：') || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small">
            {{ row.status === 1 ? '生效中' : '已解除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" v-permission="'admin:user:mute'" link type="success"
                     @click="unmute(row)">解除</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageTable from '@/components/PageTable.vue'
import { userApi } from '@/api/user'
import { MUTE_SCOPE, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })

async function load () {
  loading.value = true
  try {
    const res = await userApi.mutes(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function unmute (row) {
  await userApi.unmute(row.userId, row.scope)
  ElMessage.success('已解除禁言')
  load()
}

async function scanExpired () {
  const n = await userApi.releaseExpiredMutes()
  ElMessage.success(n > 0 ? '已解除 ' + n + ' 条到期禁言' : '没有到期未解除的禁言')
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.toolbar { margin-bottom: 12px; }
</style>
