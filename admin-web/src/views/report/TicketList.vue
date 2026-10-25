<template>
  <div>
    <div class="page-card" style="margin-bottom: 12px">
      <SearchForm :model="query" @search="reload" @reset="reset">
        <el-form-item label="对象类型">
          <el-select v-model="query.targetType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in TARGETS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提交时间">
          <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                          range-separator="至" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
      </SearchForm>
    </div>

    <div class="page-card">
      <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
                 :loading="loading" @load="load">
        <el-table-column prop="ticketNo" label="工单号" width="190" show-overflow-tooltip />
        <el-table-column prop="targetType" label="对象类型" width="100" />
        <el-table-column prop="reporterName" label="举报人" width="120" show-overflow-tooltip />
        <el-table-column prop="reasonType" label="举报原因" width="130" show-overflow-tooltip />
        <el-table-column prop="targetId" label="对象ID" width="120" />
        <el-table-column prop="reportCount" label="次数" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(row.status).type" size="small">{{ tagOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'admin:report:detail'" link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-permission="'admin:report:handle'" link type="warning" @click="openHandle(row)">处理</el-button>
          </template>
        </el-table-column>
      </PageTable>
    </div>

    <el-drawer v-model="detailVisible" :title="handling ? '处理工单' : '举报工单详情'" size="46%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="工单号">{{ detail.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{ detail.targetType }}</el-descriptions-item>
        <el-descriptions-item label="对象ID">{{ detail.targetId }}</el-descriptions-item>
        <el-descriptions-item label="举报人">{{ detail.reporterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="举报原因">{{ detail.reasonType }}</el-descriptions-item>
        <el-descriptions-item label="补充说明">{{ detail.reasonDesc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="举报凭证">
          <div v-if="detail.images">
            <img v-for="(img, i) in detail.images.split(',')" :key="i" :src="img" class="proof" />
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="次数">{{ detail.reportCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(detail.status).type" size="small">{{ tagOf(detail.status).label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理结果">{{ detail.handleResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理说明">{{ detail.handleRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detail.handlerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ detail.handledAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createdAt }}</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="handling" />

      <template v-if="handling">
        <el-form label-width="90px">
          <el-form-item label="处理状态">
            <el-select v-model="handleForm.status" style="width: 100%">
              <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理结果">
            <el-select v-model="handleForm.handleResult" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="r in HANDLE_RESULTS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理说明">
            <el-input v-model="handleForm.handleRemark" type="textarea" :rows="3" placeholder="可选，记录处置依据" />
          </el-form-item>
        </el-form>
        <div style="text-align: right">
          <el-button @click="handling = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitHandle">提交处理</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { reportApi } from '@/api/report'

const TARGETS = ['POST', 'WALL', 'GOODS', 'COMMENT', 'USER', 'MESSAGE', 'FEEDBACK']
const STATUS = [
  { value: 0, label: '待处理', type: 'warning' },
  { value: 1, label: '已处理', type: 'success' },
  { value: 2, label: '已驳回', type: 'info' }
]
const HANDLE_RESULTS = ['下架内容', '禁言作者', '封禁作者', '无违规', '其他']

function tagOf (status) {
  return STATUS.find((s) => s.value === status) || { label: '未知', type: 'info' }
}

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const range = ref(null)
const query = reactive({ targetType: null, status: null, startTime: null, endTime: null, page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})
const handling = ref(false)
const submitting = ref(false)
const handleForm = reactive({ status: 1, handleResult: '', handleRemark: '' })

watch(range, (v) => {
  query.startTime = v && v[0] ? v[0] : null
  query.endTime = v && v[1] ? v[1] : null
})

async function load () {
  loading.value = true
  try {
    const res = await reportApi.page(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function reload () {
  query.page = 1
  load()
}

function reset () {
  query.targetType = null
  query.status = null
  range.value = null
  reload()
}

async function showDetail (row) {
  detail.value = await reportApi.detail(row.id)
  handling.value = false
  detailVisible.value = true
}

function openHandle (row) {
  showDetail(row).then(() => {
    handling.value = true
    handleForm.status = 1
    handleForm.handleResult = ''
    handleForm.handleRemark = ''
  })
}

async function submitHandle () {
  submitting.value = true
  try {
    await reportApi.handle(detail.value.id, { ...handleForm })
    ElMessage.success('已提交处理')
    handling.value = false
    detailVisible.value = false
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.proof {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 6px;
  margin: 0 8px 8px 0;
}
</style>
