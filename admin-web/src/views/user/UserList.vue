<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="昵称 / 手机号" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="认证状态">
        <el-select v-model="query.verifyStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in VERIFY_STATUS" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <el-form-item label="账号状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="已封禁" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="处置">
        <el-select v-model="query.punish" placeholder="全部" clearable style="width: 130px">
          <el-option label="禁言中" value="muted" />
          <el-option label="黑名单" value="blacklisted" />
        </el-select>
      </el-form-item>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.avatar">{{ (row.nickname || '?').slice(0, 1) }}</el-avatar>
            <div>
              <div>{{ row.nickname || '-' }}</div>
              <div class="sub">{{ row.phone || '未绑定手机' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="schoolName" label="校区" width="150" show-overflow-tooltip />
      <el-table-column label="认证" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(VERIFY_STATUS, row.verifyStatus).type" size="small">
            {{ tagOf(VERIFY_STATUS, row.verifyStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(USER_STATUS, row.status).type" size="small">
            {{ tagOf(USER_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处置中" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.muted === 1" size="small" type="warning" style="margin-right: 4px">
            禁言至 {{ row.muteUntil || '永久' }}
          </el-tag>
          <el-tag v-if="row.blacklisted === 1" size="small" type="danger">黑名单</el-tag>
          <span v-if="row.muted !== 1 && row.blacklisted !== 1">-</span>
        </template>
      </el-table-column>
      <el-table-column label="粉丝" width="80" align="center">
        <template #default="{ row }">{{ row.fansCount }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="170" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:user:detail'" link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-permission="'admin:user:ban'" link :type="row.status === 1 ? 'danger' : 'success'"
                     @click="toggleBan(row)">
            {{ row.status === 1 ? '封禁' : '解封' }}
          </el-button>
          <el-button v-permission="'admin:user:mute'" link :type="row.muted === 1 ? 'success' : 'warning'"
                     @click="row.muted === 1 ? unmute(row) : openMute(row)">
            {{ row.muted === 1 ? '解禁' : '禁言' }}
          </el-button>
          <el-button v-permission="'admin:user:blacklist'" link type="danger"
                     :disabled="row.blacklisted === 1" @click="openBlacklist(row)">拉黑</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <!-- 用户详情 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="760px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="昵称">{{ detail.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="校区">{{ detail.schoolName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证状态">
          <el-tag :type="tagOf(VERIFY_STATUS, detail.verifyStatus).type" size="small">
            {{ tagOf(VERIFY_STATUS, detail.verifyStatus).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ detail.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ detail.studentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="tagOf(USER_STATUS, detail.status).type" size="small">
            {{ tagOf(USER_STATUS, detail.status).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="禁言到期">{{ detail.muteUntil || '未禁言' }}</el-descriptions-item>
        <el-descriptions-item label="内容量" :span="2">
          帖子 {{ stats.posts }} · 表白墙 {{ stats.wall }} · 商品 {{ stats.goods }} · 意向单 {{ stats.orders }}
        </el-descriptions-item>
        <el-descriptions-item label="认证材料" :span="2">
          <el-image v-if="detail.verifyImg" :src="detail.verifyImg" :preview-src-list="[detail.verifyImg]"
                    preview-teleported :z-index="3000" fit="cover" class="verify-img" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="个性签名" :span="2">{{ detail.bio || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 禁言 -->
    <el-dialog v-model="muteVisible" title="禁言用户" width="480px">
      <el-form :model="muteForm" label-width="90px">
        <el-form-item label="范围">
          <el-select v-model="muteForm.scope" style="width: 100%">
            <el-option v-for="(v, k) in MUTE_SCOPE" :key="k" :label="v.label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长">
          <el-select v-model="muteForm.minutes" style="width: 100%">
            <el-option label="1 小时" :value="60" />
            <el-option label="1 天" :value="1440" />
            <el-option label="7 天" :value="10080" />
            <el-option label="30 天" :value="43200" />
            <el-option label="永久" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="muteForm.reason" type="textarea" :rows="3" placeholder="请填写禁言原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="muteVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitMute">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { userApi } from '@/api/user'
import { VERIFY_STATUS, USER_STATUS, MUTE_SCOPE, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', verifyStatus: null, status: null, punish: null, page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref({})
const stats = reactive({ posts: 0, wall: 0, goods: 0, orders: 0 })

const muteVisible = ref(false)
const muteTarget = ref(null)
const muteForm = reactive({ scope: 1, minutes: 1440, reason: '' })

async function load () {
  loading.value = true
  try {
    const params = { ...query }
    if (query.punish === 'muted') params.muted = 1
    if (query.punish === 'blacklisted') params.blacklisted = 1
    delete params.punish
    const res = await userApi.page(params)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () {
  query.keyword = ''; query.verifyStatus = null; query.status = null; query.punish = null
  reload()
}

async function openDetail (row) {
  detail.value = await userApi.detail(row.id)
  Object.assign(stats, await userApi.stats(row.id))
  detailVisible.value = true
}

async function toggleBan (row) {
  if (row.status === 1) {
    const { value } = await ElMessageBox.prompt('填写封禁原因', '封禁用户', { inputPlaceholder: '如：多次发布违规内容' })
      .catch(() => ({ value: null }))
    if (!value) return
    await userApi.ban(row.id, value)
  } else {
    await userApi.unban(row.id)
  }
  ElMessage.success('操作成功')
  load()
}

function openMute (row) {
  muteTarget.value = row
  Object.assign(muteForm, { scope: 1, minutes: 1440, reason: '' })
  muteVisible.value = true
}

async function submitMute () {
  if (!muteForm.reason) {
    ElMessage.warning('请填写禁言原因')
    return
  }
  await userApi.mute(muteTarget.value.id, { ...muteForm })
  ElMessage.success('已禁言')
  muteVisible.value = false
  load()
}

async function unmute (row) {
  await userApi.unmute(row.id)
  ElMessage.success('已解除禁言')
  load()
}

async function openBlacklist (row) {
  const { value } = await ElMessageBox.prompt('填写拉黑原因', '拉黑用户', { inputPlaceholder: '如：恶意骚扰他人' })
    .catch(() => ({ value: null }))
  if (!value) return
  await userApi.blacklist(row.id, value)
  ElMessage.success('已加入黑名单，账号同时被封禁')
  load()
}

onMounted(load)
</script>

<style scoped>
.user-cell { display: flex; align-items: center; gap: 10px; }
.sub { font-size: 12px; color: #909399; }
.verify-img { width: 160px; height: 120px; border-radius: 4px; }
</style>
