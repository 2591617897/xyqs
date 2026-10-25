<template>
  <div class="page-card">
    <div class="head">
      <span class="title">数据概览</span>
      <el-button v-permission="'admin:dashboard:rebuild'" type="primary" :icon="Refresh" :loading="rebuilding"
                 @click="rebuild">
        重算昨日指标
      </el-button>
    </div>

    <el-row :gutter="14" class="cards">
      <el-col v-for="c in cards" :key="c.label" :span="4">
        <el-card shadow="hover" class="stat">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value">{{ c.value }}</div>
          <div v-if="c.today !== undefined" class="stat-today">今日 +{{ c.today }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat todo">
          <div class="stat-label">待处理举报</div>
          <div class="stat-value warn">{{ overview.pendingReport }}</div>
          <el-button link type="primary" @click="$router.push('/report')">去处理</el-button>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat todo">
          <div class="stat-label">待审核实名</div>
          <div class="stat-value warn">{{ overview.pendingVerify }}</div>
          <el-button link type="primary" @click="$router.push('/user/verify')">去审核</el-button>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat todo">
          <div class="stat-label">禁言中</div>
          <div class="stat-value">{{ overview.mutedNow }}</div>
          <el-button link type="primary" @click="$router.push('/user/mutes')">查看</el-button>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat todo">
          <div class="stat-label">黑名单</div>
          <div class="stat-value danger">{{ overview.bannedNow }}</div>
          <el-button link type="primary" @click="$router.push('/user/blacklist')">查看</el-button>
        </el-card>
      </el-col>
    </el-row>

    <div class="section-title">日表（物化统计）</div>
    <PageTable :data="daily" :total="dailyTotal" v-model:page="dailyQuery.page" v-model:size="dailyQuery.size"
               :loading="dailyLoading" @load="loadDaily">
      <el-table-column prop="statDate" label="日期" width="140" />
      <el-table-column prop="newUsers" label="新增用户" width="110" align="center" />
      <el-table-column prop="newPosts" label="新增帖子" width="110" align="center" />
      <el-table-column prop="newWall" label="新增表白墙" width="120" align="center" />
      <el-table-column prop="newGoods" label="新增商品" width="110" align="center" />
      <el-table-column prop="newOrders" label="新增意向单" width="120" align="center" />
      <el-table-column prop="newMessages" label="新增私信" width="110" align="center" />
      <el-table-column prop="auditCount" label="内容处置" width="110" align="center" />
      <el-table-column prop="reportCount" label="新增举报" width="110" align="center" />
      <el-table-column prop="handledReport" label="已处理举报" width="120" align="center" />
    </PageTable>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageTable from '@/components/PageTable.vue'
import { dashboardApi } from '@/api/operate'

const overview = reactive({
  userTotal: 0, postTotal: 0, wallTotal: 0, goodsTotal: 0, orderTotal: 0, messageTotal: 0,
  todayUsers: 0, todayPosts: 0, todayWall: 0, todayGoods: 0, todayOrders: 0,
  pendingReport: 0, pendingVerify: 0, mutedNow: 0, bannedNow: 0, todayAudit: 0
})

const cards = computed(() => [
  { label: '用户', value: overview.userTotal, today: overview.todayUsers },
  { label: '帖子', value: overview.postTotal, today: overview.todayPosts },
  { label: '表白墙', value: overview.wallTotal, today: overview.todayWall },
  { label: '商品', value: overview.goodsTotal, today: overview.todayGoods },
  { label: '意向单', value: overview.orderTotal, today: overview.todayOrders },
  { label: '私信', value: overview.messageTotal }
])

const daily = ref([])
const dailyTotal = ref(0)
const dailyLoading = ref(false)
const dailyQuery = reactive({ page: 1, size: 10 })
const rebuilding = ref(false)

async function loadOverview () {
  Object.assign(overview, await dashboardApi.overview())
}

async function loadDaily () {
  dailyLoading.value = true
  try {
    const res = await dashboardApi.daily(dailyQuery)
    daily.value = res.records || []
    dailyTotal.value = res.total || 0
  } finally {
    dailyLoading.value = false
  }
}

async function rebuild () {
  rebuilding.value = true
  try {
    await dashboardApi.rebuild()
    ElMessage.success('昨日指标已重算')
    loadDaily()
  } finally {
    rebuilding.value = false
  }
}

onMounted(() => { loadOverview(); loadDaily() })
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.title { font-size: 16px; font-weight: 600; }
.cards { margin-bottom: 16px; }
.stat { text-align: center; }
.stat-label { font-size: 13px; color: #909399; }
.stat-value { font-size: 26px; font-weight: 600; margin: 6px 0; }
.stat-value.warn { color: #e6a23c; }
.stat-value.danger { color: #f56c6c; }
.stat-today { font-size: 12px; color: #67c23a; }
.todo { padding-bottom: 8px; }
.section-title { font-size: 15px; font-weight: 600; margin: 8px 0 12px; }
</style>
