<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      只展示「审核中」的用户。通过后 t_user.verify_status=2，驳回=3 且必须填写原因（用户可见）。
    </el-alert>

    <div class="toolbar">
      <el-button v-permission="'admin:user:verify:list'" :icon="Clock" @click="openLogs">审核记录</el-button>
    </div>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.avatar">{{ (row.nickname || '?').slice(0, 1) }}</el-avatar>
            <span>{{ row.nickname || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="schoolName" label="认证学校" width="180" show-overflow-tooltip />
      <el-table-column label="提交信息" min-width="180">
        <template #default="{ row }">
          {{ row.realName || '-' }} · {{ row.studentNo || '未填学号' }}
        </template>
      </el-table-column>
      <el-table-column label="证明材料" width="120" align="center">
        <template #default="{ row }">
          <el-image v-if="row.verifyImg" :src="row.verifyImg" :preview-src-list="[row.verifyImg]"
                    fit="cover" class="thumb" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="提交时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:user:verify'" link type="success" @click="handle(row, true)">
            通过
          </el-button>
          <el-button v-permission="'admin:user:verify'" link type="danger" @click="handle(row, false)">
            驳回
          </el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-drawer v-model="logVisible" title="实名审核记录" size="520px">
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createdAt"
                          :type="l.toStatus === 2 ? 'success' : 'danger'">
          {{ l.userName }}（{{ l.schoolName || '未选学校' }}）→
          {{ l.toStatus === 2 ? '通过' : '驳回' }}
          <div v-if="l.reason" class="reason">原因：{{ l.reason }}</div>
          <div class="operator">操作人：{{ l.adminName || '-' }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!logs.length" description="暂无审核记录" :image-size="70" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import PageTable from '@/components/PageTable.vue'
import { userApi } from '@/api/user'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })

const logVisible = ref(false)
const logs = ref([])

async function load () {
  loading.value = true
  try {
    const res = await userApi.verifyPending(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function handle (row, pass) {
  let reason = null
  if (!pass) {
    const { value } = await ElMessageBox.prompt('填写驳回原因（用户可见）', '驳回认证', {
      inputPlaceholder: '如：学生证照片模糊，无法辨认'
    }).catch(() => ({ value: null }))
    if (!value) return
    reason = value
  } else {
    const ok = await ElMessageBox.confirm('确认通过「' + row.nickname + '」的实名认证？', '审核', { type: 'warning' })
      .catch(() => false)
    if (!ok) return
  }
  await userApi.verify(row.id, pass, reason)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

async function openLogs () {
  const res = await userApi.verifyLogs({ size: 50 })
  logs.value = res.records || []
  logVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.toolbar { margin-bottom: 12px; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.thumb { width: 72px; height: 72px; border-radius: 4px; }
.reason { color: #f56c6c; font-size: 13px; margin-top: 4px; }
.operator { color: #909399; font-size: 12px; margin-top: 2px; }
</style>
