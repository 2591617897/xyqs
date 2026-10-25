<template>
  <div>
    <div class="page-card" style="margin-bottom: 12px">
      <SearchForm :model="query" @search="reload" @reset="reset">
        <el-form-item label="账号">
          <el-input v-model="query.username" placeholder="登录账号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录时间">
          <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                          range-separator="至" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
      </SearchForm>
    </div>

    <div class="page-card">
      <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
                 :loading="loading" @load="load">
        <el-table-column prop="username" label="登录账号" width="160" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" width="140" />
        <el-table-column prop="ip" label="来源IP" width="150" />
        <el-table-column prop="ua" label="User-Agent" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="登录时间" width="180" />
      </PageTable>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { logApi } from '@/api/system'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const range = ref(null)
const query = reactive({ username: '', status: null, startTime: null, endTime: null, page: 1, size: 10 })

watch(range, (v) => {
  query.startTime = v && v[0] ? v[0] : null
  query.endTime = v && v[1] ? v[1] : null
})

async function load () {
  loading.value = true
  try {
    const res = await logApi.logins(query)
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
  query.status = null
  range.value = null
  reload()
}

onMounted(load)
</script>
