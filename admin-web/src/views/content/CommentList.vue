<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="评论内容" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="归属">
        <el-select v-model="query.targetType" placeholder="全部" clearable style="width: 140px">
          <el-option label="帖子评论" value="P_COMMENT" />
          <el-option label="墙评论" value="W_COMMENT" />
          <el-option label="商品评论" value="G_COMMENT" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="content" label="评论内容" min-width="260" show-overflow-tooltip />
      <el-table-column label="归属" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ TARGET_TYPE[row.targetType] || row.targetType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="被评论内容" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.targetTitle || '-' }}</template>
      </el-table-column>
      <el-table-column label="评论人" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.userName || row.userId }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="评论时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:content:comment:delete'" link type="danger" @click="remove(row)">
            删除
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
import { contentApi } from '@/api/content'
import { TARGET_TYPE } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', targetType: null, page: 1, size: 10 })

async function load () {
  loading.value = true
  try {
    const res = await contentApi.comments(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.targetType = null; reload() }

async function remove (row) {
  const ok = await ElMessageBox.confirm('删除后不可恢复，确认删除该评论？', '危险操作', { type: 'warning' })
    .catch(() => false)
  if (!ok) return
  await contentApi.deleteComment(row.id, row.targetType, row.targetId, { reason: '违规评论' })
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
