<template>
  <div class="page-card">
    <!-- 指标卡 -->
    <div class="stat-grid">
      <StatCard label="校友群总数" :value="stats.groupTotal" icon="ChatSquare" tone="brand" />
      <StatCard label="群成员总数" :value="stats.memberTotal" icon="User" tone="green" :delay="60" />
      <StatCard label="今日群消息" :value="stats.todayMessages" icon="Message" tone="amber" :delay="120" />
      <StatCard label="活跃群（24h）" :value="stats.activeGroups" icon="TrendCharts" tone="blue" :delay="180" />
    </div>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="群名/学校">
        <el-input v-model="query.keyword" placeholder="如 贵州大学" clearable style="width: 220px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable
      :data="rows"
      :total="total"
      v-model:page="query.page"
      v-model:size="query.size"
      :loading="loading"
      @load="load"
    >
      <el-table-column label="群名" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="cell-main">{{ row.name }}</div>
          <div class="cell-sub">{{ row.schoolName || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" label="成员" width="90" align="right" />
      <el-table-column prop="todayCount" label="今日消息" width="100" align="right" />
      <el-table-column label="夜间静音" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.nightEnabled === 1" size="small" type="info">{{ row.nightRange }}</el-tag>
          <span v-else class="cell-muted">已关闭</span>
        </template>
      </el-table-column>
      <el-table-column label="发言频率" width="130">
        <template #default="{ row }">
          <span class="cell-muted">{{ row.rateGapSec > 0 ? row.rateGapSec + ' 秒/条' : '自适应' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastMsgTime" label="最新消息" width="150">
        <template #default="{ row }">{{ row.lastMsgTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <!-- 群详情抽屉：三 tab + 群配置。
         为什么用抽屉而不是独立路由页：后台所有路由都由菜单表下发，
         加一条「隐藏菜单」会直接出现在侧栏（后端菜单树没有过滤 visible），
         抽屉既避免污染侧栏，也少一次来回跳转。 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail.name || '群详情'"
      size="86%"
      append-to-body
      destroy-on-close
      @closed="onDetailClosed"
    >
      <el-tabs v-model="tab" class="detail-tabs">
        <!-- ============ 群配置 ============ -->
        <el-tab-pane label="群配置" name="config">
          <el-form :model="cfg" label-width="120px" class="cfg-form">
            <el-form-item label="群名">
              <el-input v-model="cfg.name" maxlength="64" show-word-limit style="width: 320px" />
            </el-form-item>
            <el-form-item label="所属学校">
              <span class="cell-sub">{{ detail.schoolName || '—' }} · {{ detail.memberCount }} 人</span>
            </el-form-item>
            <el-form-item label="群公告">
              <el-input v-model="cfg.notice" type="textarea" :rows="4" maxlength="500" show-word-limit />
            </el-form-item>

            <el-divider content-position="left">发言频率</el-divider>
            <el-form-item label="发言间隔">
              <el-input-number v-model="cfg.rateGapSec" :min="0" :max="600" :step="5" />
              <span class="form-tip">秒 / 条。0 = 按群规模自适应（≤500 人 10 秒，&gt;5000 人 60 秒）</span>
            </el-form-item>
            <el-form-item label="每日上限">
              <el-input-number v-model="cfg.rateDayMax" :min="0" :max="500" :step="5" />
              <span class="form-tip">0 = 按群规模自适应</span>
            </el-form-item>

            <el-divider content-position="left">夜间静默（可按群单独设置）</el-divider>
            <el-form-item label="开启">
              <el-switch v-model="cfg.nightEnabled" :active-value="1" :inactive-value="0" />
              <span class="form-tip">关闭后本群 24 小时可发言；群管理员始终豁免</span>
            </el-form-item>
            <el-form-item label="静默时段">
              <el-time-select v-model="cfg.nightStart" start="00:00" step="00:30" end="23:30" style="width: 130px" />
              <span class="form-dash">至</span>
              <el-time-select v-model="cfg.nightEnd" start="00:00" step="00:30" end="23:30" style="width: 130px" />
              <span class="form-tip">默认 00:30–06:30。改早会砍掉大学生 23:00–00:30 的活跃时段，谨慎</span>
            </el-form-item>

            <el-divider content-position="left">状态</el-divider>
            <el-form-item label="群状态">
              <el-radio-group v-model="cfg.status">
                <el-radio :value="1">正常</el-radio>
                <el-radio :value="0">停用（只读）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="变更原因">
              <el-input v-model="cfg.reason" placeholder="会写进操作日志，便于追溯" style="width: 420px" />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'admin:group:update'" type="primary" :loading="saving" @click="saveConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ============ 成员 ============ -->
        <el-tab-pane :label="'成员 (' + detail.memberCount + ')'" name="members">
          <div class="tab-toolbar">
            <el-input v-model="memberQuery.keyword" placeholder="搜索昵称或用户ID" clearable style="width: 220px" @keyup.enter="loadMembers(1)" />
            <el-button type="primary" @click="loadMembers(1)">查询</el-button>
          </div>
          <el-table :data="members" v-loading="memberLoading" border stripe size="default">
            <el-table-column label="成员" min-width="200">
              <template #default="{ row }">
                <div class="cell-main">{{ row.nickname || ('校友' + row.userId) }}</div>
                <div class="cell-sub">ID {{ row.userId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.role === 1" size="small" type="success">管理员</el-tag>
                <span v-else class="cell-muted">成员</span>
              </template>
            </el-table-column>
            <el-table-column label="认证" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.verifyStatus === 2" size="small" type="primary">已认证</el-tag>
                <span v-else class="cell-muted">未认证</span>
              </template>
            </el-table-column>
            <el-table-column prop="joinAt" label="加入时间" width="150" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.muted" size="small" type="danger">禁言中</el-tag>
                <span v-else class="cell-muted">正常</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-permission="'admin:group:member:role'"
                  link
                  type="primary"
                  @click="toggleRole(row)"
                >{{ row.role === 1 ? '取消管理员' : '设为管理员' }}</el-button>
                <el-button
                  v-if="row.muted"
                  v-permission="'admin:group:member:mute'"
                  link
                  type="success"
                  @click="unmute(row)"
                >解除禁言</el-button>
                <el-button
                  v-else
                  v-permission="'admin:group:member:mute'"
                  link
                  type="warning"
                  @click="openMute(row)"
                >禁言</el-button>
                <el-button
                  v-permission="'admin:group:member:kick'"
                  link
                  type="danger"
                  @click="kick(row)"
                >踢出</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination
              :current-page="memberQuery.page"
              :page-size="memberQuery.size"
              :total="Number(memberTotal) || 0"
              layout="total, prev, pager, next"
              background
              @current-change="loadMembers"
            />
          </div>
        </el-tab-pane>

        <!-- ============ 消息 ============ -->
        <el-tab-pane label="消息" name="messages">
          <div class="tab-toolbar">
            <el-input v-model="msgQuery.keyword" placeholder="按内容搜索" clearable style="width: 240px" @keyup.enter="loadMessages(1)" />
            <el-button type="primary" @click="loadMessages(1)">查询</el-button>
            <span class="cell-muted">删除为软删（deleted=1），可追溯，不做物理删除</span>
          </div>
          <el-table :data="messages" v-loading="msgLoading" border stripe size="default">
            <el-table-column label="发送者" width="180">
              <template #default="{ row }">
                <div class="cell-main">{{ row.senderName || '—' }}</div>
                <div class="cell-sub">ID {{ row.senderId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="内容" min-width="300" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="{ 'cell-deleted': row.deleted === 1 }">{{ row.content }}</span>
                <el-tag v-if="row.atAll" size="small" type="warning" style="margin-left: 6px">@全体</el-tag>
                <el-tag v-if="row.deleted === 1" size="small" type="info" style="margin-left: 6px">已删除</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.deleted !== 1"
                  v-permission="'admin:group:message:delete'"
                  link
                  type="danger"
                  @click="removeMessage(row)"
                >删除</el-button>
                <span v-else class="cell-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination
              :current-page="msgQuery.page"
              :page-size="msgQuery.size"
              :total="Number(msgTotal) || 0"
              layout="total, prev, pager, next"
              background
              @current-change="loadMessages"
            />
          </div>
        </el-tab-pane>

        <!-- ============ 禁言记录 ============ -->
        <el-tab-pane label="禁言记录" name="mutes">
          <div class="tab-toolbar">
            <el-switch v-model="muteQuery.activeOnly" active-text="只看生效中" @change="loadMutes(1)" />
            <el-button type="primary" @click="loadMutes(1)">刷新</el-button>
          </div>
          <el-table :data="mutes" v-loading="muteLoading" border stripe size="default">
            <el-table-column label="用户" min-width="180">
              <template #default="{ row }">
                <div class="cell-main">{{ row.nickname || ('校友' + row.userId) }}</div>
                <div class="cell-sub">ID {{ row.userId }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="startAt" label="开始" width="150" />
            <el-table-column label="结束" width="150">
              <template #default="{ row }">
                <span v-if="row.endAt">{{ row.endAt }}</span>
                <el-tag v-else size="small" type="danger">永久</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reason || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'danger' : 'info'" size="small">
                  {{ row.status === 1 ? '生效中' : '已解除' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 1"
                  v-permission="'admin:group:member:mute'"
                  link
                  type="success"
                  @click="unmuteById(row)"
                >解除</el-button>
                <span v-else class="cell-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-row">
            <el-pagination
              :current-page="muteQuery.page"
              :page-size="muteQuery.size"
              :total="Number(muteTotal) || 0"
              layout="total, prev, pager, next"
              background
              @current-change="loadMutes"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 禁言弹窗：必须 append-to-body，否则会被顶栏 backdrop-filter 裁切 -->
    <el-dialog v-model="muteVisible" title="禁言成员" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="用户">
          <span class="cell-main">{{ muteTarget.nickname || muteTarget.userId }}</span>
        </el-form-item>
        <el-form-item label="时长">
          <el-select v-model="muteForm.hours" style="width: 180px">
            <el-option label="1 小时" :value="1" />
            <el-option label="24 小时" :value="24" />
            <el-option label="7 天" :value="168" />
            <el-option label="永久" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="muteForm.reason" type="textarea" :rows="2" maxlength="128" placeholder="如：发布广告 / 人身攻击" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="muteVisible = false">取消</el-button>
        <el-button type="danger" :loading="muteSaving" @click="doMute">确定禁言</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import SearchForm from '@/components/SearchForm.vue'
import StatCard from '@/components/StatCard.vue'
import { groupApi } from '@/api/group'

const stats = reactive({ groupTotal: 0, memberTotal: 0, todayMessages: 0, activeGroups: 0 })

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

const detailVisible = ref(false)
const detail = reactive({ id: '', name: '', schoolName: '', memberCount: 0 })
const tab = ref('config')
const saving = ref(false)
const cfg = reactive({
  name: '',
  notice: '',
  rateGapSec: 0,
  rateDayMax: 0,
  nightEnabled: 1,
  nightStart: '00:30',
  nightEnd: '06:30',
  status: 1,
  reason: ''
})

const members = ref([])
const memberTotal = ref(0)
const memberLoading = ref(false)
const memberQuery = reactive({ keyword: '', page: 1, size: 10 })

const messages = ref([])
const msgTotal = ref(0)
const msgLoading = ref(false)
const msgQuery = reactive({ keyword: '', page: 1, size: 20 })

const mutes = ref([])
const muteTotal = ref(0)
const muteLoading = ref(false)
const muteQuery = reactive({ activeOnly: false, page: 1, size: 10 })

const muteVisible = ref(false)
const muteSaving = ref(false)
const muteTarget = reactive({ userId: '', nickname: '' })
const muteForm = reactive({ hours: 24, reason: '' })

onMounted(() => {
  load()
  loadStats()
})

async function loadStats () {
  try {
    const res = await groupApi.stats()
    Object.assign(stats, res || {})
  } catch (e) {
    // 统计失败不阻塞列表
  }
}

async function load () {
  loading.value = true
  try {
    const res = await groupApi.page(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () {
  query.page = 1
  load()
  loadStats()
}

function reset () {
  query.keyword = ''
  query.status = null
  reload()
}

async function openDetail (row) {
  Object.assign(detail, {
    id: row.id,
    name: row.name,
    schoolName: row.schoolName,
    memberCount: row.memberCount
  })
  detailVisible.value = true
  tab.value = 'config'
  await loadConfig(row.id)
  loadMembers(1)
  loadMessages(1)
  loadMutes(1)
}

async function loadConfig (id) {
  const d = await groupApi.detail(id)
  if (!d) return
  Object.assign(detail, {
    name: d.name,
    schoolName: d.schoolName,
    memberCount: d.memberCount
  })
  Object.assign(cfg, {
    name: d.name || '',
    notice: d.notice || '',
    rateGapSec: Number(d.rateGapSec) || 0,
    rateDayMax: Number(d.rateDayMax) || 0,
    nightEnabled: Number(d.nightEnabled) === 0 ? 0 : 1,
    nightStart: d.nightStart || '00:30',
    nightEnd: d.nightEnd || '06:30',
    status: Number(d.status) === 0 ? 0 : 1,
    reason: ''
  })
}

function onDetailClosed () {
  // 关闭抽屉时刷新列表：配置改了、成员数变了都要在列表上体现
  load()
  loadStats()
}

async function saveConfig () {
  saving.value = true
  try {
    await groupApi.update(detail.id, { ...cfg })
    ElMessage.success('已保存')
    cfg.reason = ''
    await loadConfig(detail.id)
  } finally {
    saving.value = false
  }
}

// ---------------- 成员 ----------------

async function loadMembers (page) {
  if (page) memberQuery.page = page
  memberLoading.value = true
  try {
    const res = await groupApi.members(detail.id, memberQuery)
    members.value = res.records || []
    memberTotal.value = res.total || 0
  } finally {
    memberLoading.value = false
  }
}

async function toggleRole (row) {
  const next = row.role === 1 ? 0 : 1
  try {
    await groupApi.setRole(detail.id, row.userId, next)
    ElMessage.success(next === 1 ? '已设为管理员' : '已取消管理员')
    loadMembers()
  } catch (e) {
    // 上限 5 人的报错由后端返回（如「一个群最多 5 名管理员」）
  }
}

function openMute (row) {
  muteTarget.userId = row.userId
  muteTarget.nickname = row.nickname || ''
  muteForm.hours = 24
  muteForm.reason = ''
  muteVisible.value = true
}

async function doMute () {
  if (!muteForm.reason.trim()) {
    ElMessage.warning('请填写禁言原因（会展示给用户）')
    return
  }
  muteSaving.value = true
  try {
    await groupApi.mute(detail.id, {
      userId: muteTarget.userId,
      hours: muteForm.hours,
      reason: muteForm.reason.trim()
    })
    ElMessage.success('已禁言')
    muteVisible.value = false
    loadMembers()
    loadMutes(1)
  } finally {
    muteSaving.value = false
  }
}

async function unmute (row) {
  await groupApi.unmute(detail.id, row.userId)
  ElMessage.success('已解除禁言')
  loadMembers()
  loadMutes(1)
}

async function unmuteById (row) {
  await groupApi.unmute(detail.id, row.userId)
  ElMessage.success('已解除禁言')
  loadMutes()
}

async function kick (row) {
  await ElMessageBox.confirm(
    `确定把「${row.nickname || row.userId}」移出本群吗？被踢出后该用户不能自行重新加入。`,
    '踢出成员',
    { type: 'warning', confirmButtonText: '踢出', cancelButtonText: '取消' }
  )
  await groupApi.kick(detail.id, row.userId)
  ElMessage.success('已踢出')
  loadMembers()
  loadConfig(detail.id)
}

// ---------------- 消息 ----------------

async function loadMessages (page) {
  if (page) msgQuery.page = page
  msgLoading.value = true
  try {
    const res = await groupApi.messages(detail.id, msgQuery)
    messages.value = res.records || []
    msgTotal.value = res.total || 0
  } finally {
    msgLoading.value = false
  }
}

async function removeMessage (row) {
  await ElMessageBox.confirm('删除后群成员将看不到这条消息（软删，可追溯）。确定删除？', '删除消息', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await groupApi.deleteMessage(detail.id, row.id)
  ElMessage.success('已删除')
  loadMessages()
}

// ---------------- 禁言记录 ----------------

async function loadMutes (page) {
  if (page) muteQuery.page = page
  muteLoading.value = true
  try {
    const res = await groupApi.mutes(detail.id, muteQuery)
    mutes.value = res.records || []
    muteTotal.value = res.total || 0
  } finally {
    muteLoading.value = false
  }
}
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.cell-main {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1, #1a2520);
}
.cell-sub {
  font-size: 12px;
  color: var(--text-3, #8a9a93);
  margin-top: 2px;
}
.cell-muted {
  font-size: 12px;
  color: var(--text-3, #8a9a93);
}
.cell-deleted {
  color: var(--text-3, #8a9a93);
  text-decoration: line-through;
}
.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.cfg-form {
  max-width: 760px;
}
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-3, #8a9a93);
}
.form-dash {
  margin: 0 8px;
  color: var(--text-3, #8a9a93);
}
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
