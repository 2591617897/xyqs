<template>
  <div class="page-card">
    <el-alert type="danger" :closable="false" show-icon class="tip">
      聊天记录属于高度敏感数据，查询动作会被审计。删除消息为软删（deleted=1），可被追溯。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="会话ID">
        <el-input v-model="query.conversationId" placeholder="从会话页跳转会自动带入" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="发送者">
        <el-input v-model="query.senderId" placeholder="用户ID" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="消息内容" clearable style="width: 180px" />
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="id" label="消息ID" width="180" show-overflow-tooltip />
      <el-table-column label="发送者" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.senderName || row.senderId }}</template>
      </el-table-column>
      <el-table-column label="接收者" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.receiverName || row.receiverId }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">{{ MSG_TYPE[row.type] || row.type }}</template>
      </el-table-column>
      <el-table-column label="内容" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ deleted: row.deleted === 1 }">{{ row.content }}</span>
          <el-tag v-if="row.deleted === 1" size="small" type="info" style="margin-left: 6px">已删除</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发送时间" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.deleted !== 1" v-permission="'admin:chat:message:delete'" link type="danger"
                     @click="remove(row)">删除</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { chatApi } from '@/api/user'

const MSG_TYPE = { text: '文本', image: '图片', location: '定位', system: '系统' }

const route = useRoute()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  conversationId: route.query.conversationId || null,
  senderId: null,
  keyword: '',
  page: 1,
  size: 10
})

async function load () {
  loading.value = true
  try {
    const res = await chatApi.messages(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.conversationId = null; query.senderId = null; query.keyword = ''; reload() }

async function remove (row) {
  const { value } = await ElMessageBox.prompt('填写删除原因（会写入审计流水）', '删除消息', {
    inputPlaceholder: '如：涉嫌诈骗'
  }).catch(() => ({ value: null }))
  if (!value) return
  await chatApi.deleteMessage(row.id, value)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.deleted { color: #c0c4cc; text-decoration: line-through; }
</style>
