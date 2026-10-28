<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="标题 / 要求" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px">
          <el-option label="求物" :value="1" />
          <el-option label="求帮忙" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="进行中" :value="1" />
          <el-option label="已下架" :value="0" />
          <el-option label="已完成" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="发布者ID">
        <el-input v-model="query.userId" placeholder="按用户过滤" clearable style="width: 150px" />
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(DEMAND_TYPE, row.type).type" size="small">
            {{ tagOf(DEMAND_TYPE, row.type).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="预算" width="100">
        <template #default="{ row }">{{ row.budget === null ? '面议' : '¥ ' + row.budget }}</template>
      </el-table-column>
      <el-table-column label="分类" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.categoryName || '-' }}</template>
      </el-table-column>
      <el-table-column label="发布者" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.authorName || row.userId }}</template>
      </el-table-column>
      <el-table-column prop="schoolName" label="校区" width="130" show-overflow-tooltip />
      <el-table-column label="回应" width="80" align="center">
        <template #default="{ row }">{{ row.replyCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(DEMAND_STATUS, row.status).type" size="small">
            {{ tagOf(DEMAND_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发布时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:content:demand:detail'" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button v-permission="'admin:content:demand:off-shelf'" link
                     :type="row.status === 1 ? 'warning' : 'success'"
                     :disabled="row.status === 2"
                     @click="toggle(row)">
            {{ row.status === 1 ? '强制下架' : '恢复进行中' }}
          </el-button>
          <el-button v-permission="'admin:content:demand:delete'" link type="danger" @click="remove(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="detailVisible" title="需求详情" width="720px" append-to-body>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="tagOf(DEMAND_TYPE, detail.type).type" size="small">
            {{ tagOf(DEMAND_TYPE, detail.type).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预算">
          {{ detail.budget === null ? '面议' : '¥ ' + detail.budget }}
        </el-descriptions-item>
        <el-descriptions-item label="分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布者">{{ detail.authorName || detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="校区">{{ detail.schoolName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="期望时间">{{ detail.expectTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="交接地点">{{ detail.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="可见范围">
          {{ detail.visibility === 'city' ? '同市可见' : '仅本校' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(DEMAND_STATUS, detail.status).type" size="small">
            {{ tagOf(DEMAND_STATUS, detail.status).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="浏览">{{ detail.viewCount }}</el-descriptions-item>
        <el-descriptions-item label="回应">{{ detail.replyCount }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="具体要求" :span="2">
          <div class="content-box">{{ detail.description || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="参考图" :span="2">
          <div v-if="imgList.length" class="img-list">
            <el-image v-for="u in imgList" :key="u" :src="u" :preview-src-list="imgList"
                      preview-teleported :z-index="3000" fit="cover" class="thumb" />
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { contentApi } from '@/api/content'
import { DEMAND_TYPE, DEMAND_STATUS, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', type: null, status: null, userId: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})
const imgList = computed(() => (detail.value.images ? detail.value.images.split(',') : []))

async function load () {
  loading.value = true
  try {
    // 空字符串不进查询参数：后端 userId 是 Long，传 '' 会转成 0
    const params = { ...query }
    if (!params.userId) delete params.userId
    const res = await contentApi.demands(params)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () {
  query.keyword = ''
  query.type = null
  query.status = null
  query.userId = ''
  reload()
}

async function openDetail (row) {
  detail.value = await contentApi.demandDetail(row.id)
  detailVisible.value = true
}

async function toggle (row) {
  const off = row.status === 1
  const { value } = await ElMessageBox.prompt(
    off ? '填写下架原因（可选）' : '确认恢复该需求为进行中？', off ? '强制下架' : '恢复进行中',
    { inputPlaceholder: '如：虚假需求', inputValue: '' }
  ).catch(() => ({ value: null }))
  if (off && value === null) return
  await contentApi.setDemandStatus(row.id, {
    status: off ? 0 : 1,
    reason: value || (off ? '违规需求' : '恢复进行中')
  })
  ElMessage.success('操作成功')
  load()
}

async function remove (row) {
  const { value } = await ElMessageBox.prompt(
    '删除后不可恢复，该需求下的回应也会一并删除。填写删除原因（可选）', '删除需求',
    { inputPlaceholder: '如：诈骗信息', inputValue: '' }
  ).catch(() => ({ value: null }))
  if (value === null) return
  await contentApi.deleteDemand(row.id, { reason: value || '违规需求' })
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.content-box { max-height: 220px; overflow: auto; white-space: pre-wrap; }
.img-list { display: flex; flex-wrap: wrap; gap: 8px; }
.thumb { width: 90px; height: 90px; border-radius: 4px; }
</style>
