<template>
  <div>
    <div class="page-card" style="margin-bottom: 12px">
      <SearchForm :model="query" @search="reload" @reset="reset">
        <el-form-item label="账号">
          <el-input v-model="query.username" placeholder="操作账号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                          range-separator="至" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
      </SearchForm>
    </div>

    <div class="page-card">
      <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
                 :loading="loading" @load="load">
        <el-table-column prop="username" label="操作账号" width="140" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="operation" label="操作" width="160" />
        <el-table-column prop="uri" label="接口" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ip" label="来源IP" width="140" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(RESULT, row.result).type" size="small">{{ tagOf(RESULT, row.result).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" />
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'admin:log:operation:detail'" link type="primary" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </PageTable>
    </div>

    <el-drawer v-model="detailVisible" title="操作日志详情" size="45%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="操作账号">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.operation }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ detail.httpMethod }} {{ detail.uri }}</el-descriptions-item>
        <el-descriptions-item label="来源IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="tagOf(RESULT, detail.result).type" size="small">
            {{ tagOf(RESULT, detail.result).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.ua }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre class="log-params">{{ detail.params }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ detail.errorMsg || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { logApi } from '@/api/system'
import { RESULT, tagOf } from '@/utils/dict'

const MODULES = ['AUTH', 'CONTENT', 'USER', 'AD', 'REPORT', 'SYSTEM', 'NOTICE', 'CHAT', 'SENSITIVE']

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const range = ref(null)
const query = reactive({ username: '', module: null, result: null, startTime: null, endTime: null, page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})

watch(range, (v) => {
  query.startTime = v && v[0] ? v[0] : null
  query.endTime = v && v[1] ? v[1] : null
})

async function load () {
  loading.value = true
  try {
    const res = await logApi.operations(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    loading.value = false
  }
}

function reload () {
  query.page = 1
  load()
}

function reset () {
  query.username = ''
  query.module = null
  query.result = null
  range.value = null
  reload()
}

async function showDetail (row) {
  detail.value = await logApi.operationDetail(row.id)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.log-params {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  color: #475569;
}
</style>
