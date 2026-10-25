<template>
  <div class="dashboard">
    <!-- 页头 -->
    <header class="dash-head">
      <div class="dash-head-text">
        <h2>数据概览</h2>
        <p>
          {{ todayText }}
          <span class="dot-sep" />
          共 {{ overview.userTotal }} 位用户 · {{ overview.postTotal + overview.wallTotal }} 条内容
        </p>
      </div>
      <div class="dash-head-actions">
        <el-button :icon="Refresh" :loading="refreshing" @click="refresh">刷新</el-button>
        <el-button v-permission="'admin:dashboard:rebuild'" type="primary" :icon="MagicStick"
                   :loading="rebuilding" @click="rebuild">
          重算昨日指标
        </el-button>
      </div>
    </header>

    <!-- 核心指标 -->
    <section class="stat-grid">
      <StatCard v-for="(c, i) in cards" :key="c.label" :label="c.label" :value="c.value" :today="c.today"
                :icon="c.icon" :tone="c.tone" :delay="i * 55" />
    </section>

    <!-- 趋势 + 待办 -->
    <section class="dash-row">
      <div class="panel chart-panel">
        <div class="panel-head">
          <div>
            <h3>近 7 日内容趋势</h3>
            <p class="panel-sub">新增用户 / 帖子 / 表白墙</p>
          </div>
          <div class="chart-legend">
            <span v-for="s in series" :key="s.key" class="legend-item">
              <i :style="{ background: s.color }" />{{ s.label }}
            </span>
          </div>
        </div>

        <div v-if="trendDays.length" class="chart-plot">
          <div class="grid-lines" aria-hidden="true" />
          <div class="groups">
            <div v-for="(d, i) in trendDays" :key="d.date" class="group"
                 :title="`${d.date}｜用户 ${d.users}｜帖子 ${d.posts}｜表白墙 ${d.wall}`">
              <div class="bars">
                <span class="bar" :style="barStyle(d.users, i, 0)" />
                <span class="bar" :style="barStyle(d.posts, i, 1)" />
                <span class="bar" :style="barStyle(d.wall, i, 2)" />
              </div>
              <span class="x-label">{{ d.label }}</span>
            </div>
          </div>
        </div>
        <div v-else class="chart-empty">
          <el-icon :size="22"><TrendCharts /></el-icon>
          <span>暂无日表数据，重算昨日指标后即可看到趋势</span>
        </div>

        <div class="chart-foot">
          <span>近 7 日累计新增 <b>{{ trendTotal }}</b> 条</span>
          <span class="chart-foot-hint">悬停柱子查看当日明细</span>
        </div>
      </div>

      <div class="panel todo-panel">
        <div class="panel-head">
          <div>
            <h3>待办提醒</h3>
            <p class="panel-sub">需要人工介入的事项</p>
          </div>
          <span class="todo-badge" :class="{ 'is-zero': pendingTotal === 0 }">{{ pendingTotal }}</span>
        </div>

        <div class="todo-list">
          <button v-for="t in todos" :key="t.label" type="button" class="todo-item" @click="router.push(t.path)">
            <span class="todo-icon" :class="'tone-' + t.tone">
              <el-icon :size="15"><component :is="t.icon" /></el-icon>
            </span>
            <span class="todo-text">
              <span class="todo-label">{{ t.label }}</span>
              <span class="todo-sub">{{ t.sub }}</span>
            </span>
            <span class="todo-num" :class="'tone-' + t.tone">
              <CountUp :value="t.value" :duration="700" />
            </span>
            <el-icon class="todo-arrow" :size="12"><ArrowRight /></el-icon>
          </button>
        </div>

        <div class="todo-foot">
          <span class="live-dot" />数据每 10 分钟自动同步
        </div>
      </div>
    </section>

    <!-- 日表 -->
    <section class="page-card table-card">
      <div class="panel-head">
        <div>
          <h3>日表明细</h3>
          <p class="panel-sub">物化统计，按天聚合</p>
        </div>
      </div>
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
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Refresh, MagicStick, ArrowRight, TrendCharts,
  User, ChatLineSquare, Star, ShoppingBag, Tickets, Message,
  Warning, UserFilled, ChatDotRound, CircleClose
} from '@element-plus/icons-vue'
import PageTable from '@/components/PageTable.vue'
import StatCard from '@/components/StatCard.vue'
import CountUp from '@/components/CountUp.vue'
import { dashboardApi } from '@/api/operate'

const router = useRouter()

const overview = reactive({
  userTotal: 0, postTotal: 0, wallTotal: 0, goodsTotal: 0, orderTotal: 0, messageTotal: 0,
  todayUsers: 0, todayPosts: 0, todayWall: 0, todayGoods: 0, todayOrders: 0,
  pendingReport: 0, pendingVerify: 0, mutedNow: 0, bannedNow: 0, todayAudit: 0
})

const cards = computed(() => [
  { label: '用户', value: overview.userTotal, today: overview.todayUsers, icon: User, tone: 'brand' },
  { label: '帖子', value: overview.postTotal, today: overview.todayPosts, icon: ChatLineSquare, tone: 'sky' },
  { label: '表白墙', value: overview.wallTotal, today: overview.todayWall, icon: Star, tone: 'rose' },
  { label: '商品', value: overview.goodsTotal, today: overview.todayGoods, icon: ShoppingBag, tone: 'amber' },
  { label: '意向单', value: overview.orderTotal, today: overview.todayOrders, icon: Tickets, tone: 'violet' },
  { label: '私信', value: overview.messageTotal, icon: Message, tone: 'teal' }
])

const todos = computed(() => [
  { label: '待处理举报', sub: '用户举报工单', value: overview.pendingReport, icon: Warning, tone: 'amber', path: '/report' },
  { label: '待审核实名', sub: '学籍认证申请', value: overview.pendingVerify, icon: UserFilled, tone: 'sky', path: '/user/verify' },
  { label: '禁言中用户', sub: '处罚生效期内', value: overview.mutedNow, icon: ChatDotRound, tone: 'violet', path: '/user/mutes' },
  { label: '黑名单', sub: '永久封禁账号', value: overview.bannedNow, icon: CircleClose, tone: 'rose', path: '/user/blacklist' }
])

const pendingTotal = computed(() => overview.pendingReport + overview.pendingVerify)

const todayText = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${week}`
})

const daily = ref([])
const dailyTotal = ref(0)
const dailyLoading = ref(false)
const dailyQuery = reactive({ page: 1, size: 10 })
const rebuilding = ref(false)
const refreshing = ref(false)
const chartReady = ref(false)

const series = [
  { key: 'users', label: '用户', color: '#7C63D8' },
  { key: 'posts', label: '帖子', color: '#3B7BF0' },
  { key: 'wall', label: '表白墙', color: '#E4568B' }
]

// 取最近 7 天并转成升序（接口按日期倒序返回）
const trendDays = computed(() => {
  const list = (daily.value || []).slice(0, 7).slice().reverse()
  return list.map((d) => ({
    date: d.statDate || '',
    label: (d.statDate || '').slice(5).replace('-', '/'),
    users: d.newUsers || 0,
    posts: d.newPosts || 0,
    wall: d.newWall || 0
  }))
})

const trendMax = computed(() =>
  Math.max(1, ...trendDays.value.flatMap((d) => [d.users, d.posts, d.wall]))
)

const trendTotal = computed(() =>
  trendDays.value.reduce((sum, d) => sum + d.users + d.posts + d.wall, 0)
)

function barStyle (value, index, order) {
  const pct = chartReady.value ? Math.max(value > 0 ? 4 : 0, (value / trendMax.value) * 100) : 0
  return {
    height: pct + '%',
    background: series[order].color,
    transitionDelay: index * 60 + order * 45 + 'ms'
  }
}

async function loadOverview () {
  try {
    const res = await dashboardApi.overview()
    Object.assign(overview, res || {})
  } catch (e) {
    // 提示已在拦截器统一处理
  }
}

async function loadDaily () {
  dailyLoading.value = true
  chartReady.value = false
  try {
    const res = await dashboardApi.daily(dailyQuery)
    daily.value = res.records || []
    dailyTotal.value = res.total || 0
    await nextTick()
    chartReady.value = true
  } finally {
    dailyLoading.value = false
  }
}

async function refresh () {
  refreshing.value = true
  try {
    await Promise.all([loadOverview(), loadDaily()])
  } finally {
    refreshing.value = false
  }
}

async function rebuild () {
  rebuilding.value = true
  try {
    await dashboardApi.rebuild()
    ElMessage.success('昨日指标已重算')
    await refresh()
  } finally {
    rebuilding.value = false
  }
}

onMounted(() => {
  loadOverview()
  loadDaily()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------------- 页头 ---------------- */
.dash-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 2px 2px 0;
}

.dash-head h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -.2px;
  color: var(--text-1);
}

.dash-head p {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--text-3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot-sep {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--line-strong);
}

.dash-head-actions {
  display: flex;
  align-items: center;
}

/* ---------------- 指标卡 ---------------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

/* ---------------- 面板 ---------------- */
.dash-row {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, .9fr);
  gap: 14px;
}

.panel {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-1);
  padding: 16px 18px;
  animation: cardIn var(--dur-slow) var(--ease) backwards;
  transition: box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.panel:hover {
  box-shadow: var(--shadow-2);
  border-color: var(--line);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.panel-head h3 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-1);
}

.panel-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--text-3);
}

/* ---------------- 柱状图 ---------------- */
.chart-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-2);
}

.legend-item i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  display: inline-block;
}

.chart-plot {
  position: relative;
  flex: 1;
  min-height: 176px;
  margin-top: 6px;
}

.grid-lines {
  position: absolute;
  inset: 0 0 18px;
  background-image: linear-gradient(to top, rgba(15, 23, 42, .06) 1px, transparent 1px);
  background-size: 100% 25%;
  border-bottom: 1px solid var(--line-soft);
  pointer-events: none;
}

.groups {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  gap: 6px;
  height: 100%;
}

.group {
  position: relative;
  flex: 1 1 0;
  max-width: 132px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  border-radius: var(--r-sm);
  transition: background var(--dur) var(--ease);
}

.group:hover {
  background: rgba(124, 99, 216, .06);
}

.bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
  flex: 1;
  width: 100%;
  min-height: 0;
}

.bar {
  width: 9px;
  max-width: 22%;
  border-radius: 4px 4px 2px 2px;
  height: 0;
  transition: height .72s var(--ease), filter var(--dur);
  will-change: height;
}

.group:hover .bar {
  filter: saturate(1.2) brightness(1.06);
}

.x-label {
  flex: none;
  margin-top: 7px;
  font-size: 11px;
  color: var(--text-3);
  transition: color var(--dur);
}

.group:hover .x-label {
  color: var(--brand-500);
  font-weight: 600;
}

.chart-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px dashed var(--line-soft);
  font-size: 12px;
  color: var(--text-3);
}

.chart-foot b {
  color: var(--brand-500);
  font-size: 13px;
}

.chart-foot-hint {
  opacity: .8;
}

.chart-empty {
  flex: 1;
  min-height: 132px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 12.5px;
  border: 1px dashed var(--line);
  border-radius: var(--r-md);
  background: var(--bg-subtle);
}

/* ---------------- 待办 ---------------- */
.todo-badge {
  min-width: 24px;
  height: 24px;
  padding: 0 8px;
  border-radius: var(--r-pill);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #B45309;
  background: #FEF5E7;
  box-shadow: inset 0 0 0 1px #F8DCB4;
}

.todo-badge.is-zero {
  color: var(--brand-600);
  background: var(--brand-50);
  box-shadow: inset 0 0 0 1px var(--brand-100);
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
  flex: 1;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  background: var(--bg-subtle);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: all var(--dur) var(--ease);
}

.todo-item:hover {
  background: #fff;
  border-color: var(--line);
  box-shadow: var(--shadow-1);
  transform: translateX(2px);
}

.todo-item:active {
  transform: scale(.995);
}

.todo-icon {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 9px;
  display: grid;
  place-items: center;
}

.todo-icon.tone-amber { color: #B45309; background: #FEF5E7; }
.todo-icon.tone-sky { color: #3B7BF0; background: #E9F0FE; }
.todo-icon.tone-violet { color: #6366F1; background: #ECEDFE; }
.todo-icon.tone-rose { color: #E4568B; background: #FDE8F0; }

.todo-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.todo-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}

.todo-sub {
  font-size: 11px;
  color: var(--text-3);
}

.todo-num {
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -.3px;
}

.todo-num.tone-amber { color: #D97706; }
.todo-num.tone-sky { color: #3B7BF0; }
.todo-num.tone-violet { color: #6366F1; }
.todo-num.tone-rose { color: #E4568B; }

.todo-arrow {
  color: var(--text-3);
  opacity: 0;
  transform: translateX(-4px);
  transition: all var(--dur) var(--ease);
}

.todo-item:hover .todo-arrow {
  opacity: 1;
  transform: none;
  color: var(--brand-500);
}

.todo-foot {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--line-soft);
  font-size: 11.5px;
  color: var(--text-3);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #C9B7FF;
  animation: pulseRing 2.4s ease-out infinite;
}

/* ---------------- 日表 ---------------- */
.table-card {
  padding-bottom: 14px;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .dash-row {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
