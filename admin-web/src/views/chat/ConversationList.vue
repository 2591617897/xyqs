<template>
  <div class="page-card">
    <el-alert type="danger" :closable="false" show-icon class="tip">
      聊天记录属于高度敏感数据。本页与消息查询的每一次访问都会写入操作日志，请勿用于与工作无关的查看。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="参与人昵称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="query.userId" placeholder="按用户筛选" clearable style="width: 180px" />
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="id" label="会话ID" width="180" show-overflow-tooltip />
      <el-table-column label="参与人" min-width="220">
        <template #default="{ row }">
          {{ row.userNameA || row.userA }} ↔ {{ row.userNameB || row.userB }}
        </template>
      </el-table-column>
      <el-table-column label="关联商品" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.goodsId ? (row.goodsTitle || row.goodsId) : '-' }}</template>
      </el-table-column>
      <el-table-column label="匿名" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.anon === 1" size="small" type="info">匿名</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="最后一条" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastContent || '-' }}</template>
      </el-table-column>
      <el-table-column prop="msgCount" label="消息数" width="90" align="center" />
      <el-table-column prop="lastTime" label="最后活跃" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:chat:message:list'" link type="primary" @click="viewMessages(row)">
            查看消息
          </el-button>
        </template>
      </el-table-column>
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { chatApi } from '@/api/user'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', userId: null, page: 1, size: 10 })

async function load () {
  loading.value = true
  try {
    const res = await chatApi.conversations(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.userId = null; reload() }

function viewMessages (row) {
  router.push({ path: '/chat/messages', query: { conversationId: row.id } })
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
</style>
