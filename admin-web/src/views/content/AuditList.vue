<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      每一次内容处置都会在这里留痕；原内容被删除后仍可凭快照追溯，是申诉时的唯一证据。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="对象类型">
        <el-select v-model="query.targetType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(label, key) in TARGET_TYPE" :key="key" :label="label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="query.action" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(label, key) in ACTION" :key="key" :label="label" :value="key" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="对象" width="150">
        <template #default="{ row }">
          {{ TARGET_TYPE[row.targetType] || row.targetType }} · {{ row.targetId }}
        </template>
      </el-table-column>
      <el-table-column label="动作" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" size="small">{{ ACTION[row.action] || row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容快照" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.title || row.contentBrief || '-' }}</template>
      </el-table-column>
      <el-table-column label="作者" width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.authorName || row.authorId || '-' }}</template>
      </el-table-column>
      <el-table-column label="原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ [row.reasonType, row.reason].filter(Boolean).join('：') || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="adminName" label="操作人" width="120" />
      <el-table-column prop="createdAt" label="操作时间" width="170" />
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { contentApi } from '@/api/content'
import { TARGET_TYPE } from '@/utils/dict'

const ACTION = { HIDE: '隐藏', SHOW: '恢复', DELETE: '删除', PASS: '通过', REJECT: '驳回', WARN: '警告' }
const ACTION_TYPE = { HIDE: 'danger', SHOW: 'success', DELETE: 'danger', PASS: 'success', REJECT: 'warning', WARN: 'warning' }

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ targetType: null, action: null, page: 1, size: 10 })

function actionType (a) { return ACTION_TYPE[a] || 'info' }

async function load () {
  loading.value = true
  try {
    const res = await contentApi.audits(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.targetType = null; query.action = null; reload() }

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
</style>
