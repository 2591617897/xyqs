<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="标题 / 描述" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="在售" :value="1" />
          <el-option label="已下架" :value="0" />
          <el-option label="已售出" :value="2" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="价格" width="110">
        <template #default="{ row }">¥ {{ row.price }}</template>
      </el-table-column>
      <el-table-column label="卖家" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.sellerName || row.sellerId }}</template>
      </el-table-column>
      <el-table-column prop="schoolName" label="校区" width="140" show-overflow-tooltip />
      <el-table-column prop="viewCount" label="浏览" width="90" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(GOODS_STATUS, row.status).type" size="small">
            {{ tagOf(GOODS_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发布时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:content:goods:detail'" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button v-permission="'admin:content:goods:off-shelf'" link
                     :type="row.status === 1 ? 'warning' : 'success'"
                     :disabled="row.status === 2"
                     @click="toggle(row)">
            {{ row.status === 1 ? '强制下架' : '恢复在售' }}
          </el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="detailVisible" title="商品详情" width="720px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="价格">¥ {{ detail.price }}</el-descriptions-item>
        <el-descriptions-item label="卖家">{{ detail.sellerName || detail.sellerId }}</el-descriptions-item>
        <el-descriptions-item label="校区">{{ detail.schoolName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(GOODS_STATUS, detail.status).type" size="small">
            {{ tagOf(GOODS_STATUS, detail.status).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          <div class="content-box">{{ detail.description || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="图片" :span="2">
          <div v-if="imgList.length" class="img-list">
            <el-image v-for="u in imgList" :key="u" :src="u" :preview-src-list="imgList" fit="cover" class="thumb" />
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
import { GOODS_STATUS, tagOf } from '@/utils/dict'

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
    const res = await contentApi.goods(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.status = null; reload() }

async function openDetail (row) {
  detail.value = await contentApi.goodsDetail(row.id)
  detailVisible.value = true
}

async function toggle (row) {
  const off = row.status === 1
  const { value } = await ElMessageBox.prompt(
    off ? '填写下架原因（可选）' : '确认恢复该商品为在售？', off ? '强制下架' : '恢复在售',
    { inputPlaceholder: '如：虚假商品', inputValue: '' }
  ).catch(() => ({ value: null }))
  if (off && value === null) return
  await contentApi.setGoodsStatus(row.id, { status: off ? 0 : 1, reason: value || (off ? '违规商品' : '恢复在售') })
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
