<template>
  <div class="log-page">
    <!-- 页头 -->
    <header class="page-card log-head">
      <div class="log-head-text">
        <h2>登录日志</h2>
        <p>记录所有后台账号的登录尝试，可用于排查异常登录与密码爆破</p>
      </div>
      <div class="log-chips">
        <span class="chip chip-ok">
          <i />本页成功 <b>{{ okCount }}</b>
        </span>
        <span class="chip chip-bad">
          <i />本页失败 <b>{{ failCount }}</b>
        </span>
      </div>
    </header>

    <!-- 筛选 -->
    <div class="page-card">
      <SearchForm :model="query" @search="reload" @reset="reset">
        <el-form-item label="账号">
          <el-input v-model="query.username" placeholder="登录账号" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
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

    <!-- 列表 -->
    <div class="page-card">
      <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
                 :loading="loading" @load="load">
        <el-table-column prop="username" label="登录账号" width="170">
          <template #default="{ row }">
            <span class="account">
              <span class="acc-dot">{{ (row.username || '?').charAt(0).toUpperCase() }}</span>
              <span class="acc-name">{{ row.username || '—' }}</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column label="结果" width="98" align="center">
          <template #default="{ row }">
            <span class="status" :class="row.status === 1 ? 'ok' : 'bad'">
              <i />{{ row.status === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="失败原因" width="150">
          <template #default="{ row }">
            <span :class="row.failReason ? 'fail-reason' : 'text-muted'">{{ row.failReason || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="来源 IP" width="160">
          <template #default="{ row }">
            <span class="ip">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="ua" label="User-Agent" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="ua" :title="row.ua">{{ row.ua || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="登录时间" width="180">
          <template #default="{ row }">
            <span class="time">{{ row.createdAt || '—' }}</span>
          </template>
        </el-table-column>
      </PageTable>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { logApi } from '@/api/system'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const range = ref(null)
const query = reactive({ username: '', status: null, startTime: null, endTime: null, page: 1, size: 10 })

// 本页统计：只统计当前页，避免给出没有数据支撑的全局数字
const okCount = computed(() => rows.value.filter((r) => r.status === 1).length)
const failCount = computed(() => rows.value.filter((r) => r.status !== 1).length)

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

<style scoped>
.log-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------------- 页头 ---------------- */
.log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 20px;
  background:
    radial-gradient(420px 120px at 0% 0%, rgba(124, 99, 216, .09), transparent 70%),
    #fff;
}

.log-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -.2px;
}

.log-head p {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: var(--text-3);
}

.log-chips {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: var(--r-pill);
  font-size: 12px;
  font-weight: 500;
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.chip:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-1);
}

.chip i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.chip b {
  font-weight: 700;
  font-size: 13px;
}

.chip-ok {
  color: var(--brand-600);
  background: var(--brand-50);
  box-shadow: inset 0 0 0 1px var(--brand-100);
}

.chip-ok i {
  background: var(--brand-500);
  box-shadow: 0 0 0 3px rgba(124, 99, 216, .16);
}

.chip-bad {
  color: #C81E1E;
  background: #FDECEC;
  box-shadow: inset 0 0 0 1px #FBD5D5;
}

.chip-bad i {
  background: #EF4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, .14);
}

/* ---------------- 单元格 ---------------- */
.account {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.acc-dot {
  width: 24px;
  height: 24px;
  flex: none;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--brand-600);
  background: var(--brand-50);
  box-shadow: inset 0 0 0 1px var(--brand-100);
}

.acc-name {
  font-weight: 500;
  color: var(--text-1);
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: var(--r-pill);
  font-size: 12px;
  font-weight: 500;
}

.status i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.status.ok {
  color: var(--brand-600);
  background: var(--brand-50);
}

.status.ok i {
  background: var(--brand-500);
}

.status.bad {
  color: #C81E1E;
  background: #FDECEC;
}

.status.bad i {
  background: #EF4444;
}

.fail-reason {
  color: #C81E1E;
  font-size: 12.5px;
}

.ip {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  color: var(--text-2);
  background: var(--bg-subtle);
  border-radius: var(--r-xs);
  padding: 1px 6px;
}

.ua {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-3);
  font-size: 12.5px;
}

.time {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  color: var(--text-2);
}
</style>
