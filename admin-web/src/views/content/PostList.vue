<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="标题 / 正文" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="已隐藏" :value="0" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="作者" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.authorName || row.authorId }}
        </template>
      </el-table-column>
      <el-table-column prop="channelName" label="频道" width="120" show-overflow-tooltip />
      <el-table-column prop="schoolName" label="校区" width="140" show-overflow-tooltip />
      <el-table-column label="互动" width="130">
        <template #default="{ row }">赞 {{ row.likeCount }} · 评 {{ row.commentCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(CONTENT_STATUS, row.status).type" size="small">
            {{ tagOf(CONTENT_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发布时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:content:post:detail'" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button v-permission="'admin:content:post:hide'" link :type="row.status === 1 ? 'warning' : 'success'"
                     @click="toggle(row)">
            {{ row.status === 1 ? '隐藏' : '恢复' }}
          </el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="detailVisible" title="帖子详情" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="作者">{{ detail.authorName || detail.authorId }}</el-descriptions-item>
        <el-descriptions-item label="频道">{{ detail.channelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="校区">{{ detail.schoolName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(CONTENT_STATUS, detail.status).type" size="small">
            {{ tagOf(CONTENT_STATUS, detail.status).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="正文" :span="2">
          <div class="content-box">{{ detail.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="图片" :span="2">
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
import { CONTENT_STATUS, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})
const imgList = computed(() => (detail.value.images ? detail.value.images.split(',') : []))

async function load () {
  loading.value = true
  try {
    const res = await contentApi.posts(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.status = null; reload() }

async function openDetail (row) {
  detail.value = await contentApi.postDetail(row.id)
  detailVisible.value = true
}

async function toggle (row) {
  const hide = row.status === 1
  const { value } = await ElMessageBox.prompt(
    hide ? '填写隐藏原因（可选）' : '确认恢复该帖子？', hide ? '隐藏帖子' : '恢复帖子',
    { inputPlaceholder: '如：广告骚扰', inputValue: '' }
  ).catch(() => ({ value: null }))
  if (hide && value === null) return
  await contentApi.setPostStatus(row.id, { status: hide ? 0 : 1, reason: value || (hide ? '违规内容' : '恢复展示') })
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.content-box { max-height: 220px; overflow: auto; white-space: pre-wrap; }
.img-list { display: flex; flex-wrap: wrap; gap: 8px; }
.thumb { width: 90px; height: 90px; border-radius: 4px; }
</style>
