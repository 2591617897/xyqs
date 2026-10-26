<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="标题">
        <el-input v-model="query.keyword" placeholder="公告标题" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in NOTICE_STATUS" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <el-form-item label="弹窗">
        <el-select v-model="query.popupMode" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(v, k) in POPUP_MODE" :key="k" :label="v" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:notice:create'" type="success" :icon="Plus" @click="openCreate">
          新建公告
        </el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">{{ NOTICE_TYPE[row.type] || row.type }}</template>
      </el-table-column>
      <el-table-column label="投放对象" width="130" align="center">
        <template #default="{ row }">{{ NOTICE_TARGET[row.targetType] || row.targetType }}</template>
      </el-table-column>
      <el-table-column label="级别" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="LEVEL_TYPE[row.level]" size="small">{{ LEVEL[row.level] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="弹窗" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="POPUP_MODE_TYPE[row.popupMode] || 'info'" size="small" effect="plain">
            {{ POPUP_MODE[row.popupMode] || POPUP_MODE[0] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).type" size="small">{{ statusOf(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="送达 / 确认" width="120" align="center">
        <template #default="{ row }">
          <el-tooltip placement="top"
                      content="送达 = 弹窗实际弹出过的登录用户数；确认 = 其中点了「我知道了」的人数。游客弹窗不落库，不计入。">
            <span class="stat">{{ row.shownCount || 0 }} / {{ row.confirmCount || 0 }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">
          <span :class="{ reserving: isReserving(row) }">{{ row.publishAt || '-' }}</span>
          <el-tag v-if="isReserving(row)" size="small" type="warning" effect="plain" style="margin-left: 4px">
            预约
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="自动下线" width="170">
        <template #default="{ row }">{{ row.offlineAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:notice:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 1" v-permission="'admin:notice:publish'" link type="success"
                     @click="publish(row)">发布</el-button>
          <el-button v-if="row.status === 1" v-permission="'admin:notice:publish'" link type="info"
                     @click="offline(row)">下线</el-button>
          <el-button v-permission="'admin:notice:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑公告' : '新建公告'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">系统公告</el-radio>
            <el-radio :value="2">运营活动</el-radio>
            <el-radio :value="3">违规警示</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="投放对象" prop="targetType">
          <el-radio-group v-model="form.targetType">
            <el-radio :value="1">后台管理员</el-radio>
            <el-radio :value="2">小程序全体</el-radio>
            <el-radio :value="3">指定校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetType === 3" label="校区ID">
          <el-input v-model.number="form.schoolId" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">普通</el-radio>
            <el-radio :value="2">重要</el-radio>
            <el-radio :value="3">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="小程序弹窗">
          <el-radio-group v-model="form.popupMode" :disabled="form.targetType === 1">
            <el-radio :value="0">不弹窗</el-radio>
            <el-radio :value="1">每次进入</el-radio>
            <el-radio :value="2">每天一次</el-radio>
            <el-radio :value="3">确认收到后不再显示</el-radio>
          </el-radio-group>
          <div class="field-hint">
            {{ form.targetType === 1
              ? '投放对象为「后台管理员」时公告不会进入小程序，无法开启弹窗。'
              : POPUP_HINT[form.popupMode] }}
          </div>
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="form.publishAt" type="datetime" placeholder="留空 = 点「发布」时立即生效"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          <div class="field-hint">填未来时间即<b>预约发布</b>：先点「发布」，到点自动出现在小程序，无需再操作。</div>
        </el-form-item>
        <el-form-item label="自动下线">
          <el-date-picker v-model="form.offlineAt" type="datetime" placeholder="留空 = 不自动下线，需手动点「下线」"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          <div class="field-hint">到点后小程序立即不可见；后台状态由定时任务在 5 分钟内同步为「已下线」。</div>
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保 存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { noticeApi } from '@/api/operate'
import { NOTICE_STATUS, tagOf } from '@/utils/dict'

const NOTICE_TYPE = { 1: '系统公告', 2: '运营活动', 3: '违规警示' }
const NOTICE_TARGET = { 1: '后台管理员', 2: '小程序全体', 3: '指定校区' }
const LEVEL = { 1: '普通', 2: '重要', 3: '紧急' }
const LEVEL_TYPE = { 1: 'info', 2: 'warning', 3: 'danger' }

/**
 * 小程序弹窗频次。0 是默认值 —— 等于旧行为（只在首页公告条露出），
 * 这样历史公告不需要迁移，也不会因为加了字段就突然开始打扰用户。
 */
const POPUP_MODE = { 0: '不弹窗', 1: '每次进入', 2: '每天一次', 3: '确认后不再弹' }
const POPUP_MODE_TYPE = { 0: 'info', 1: 'warning', 2: 'success', 3: 'primary' }
const POPUP_HINT = {
  0: '只在首页顶部公告条展示，不打断用户。',
  1: '每次打开小程序弹一次（切换页面、返回首页不会重复弹）。',
  2: '同一用户当天只弹一次，第二天再进入会再弹。',
  3: '用户点「我知道了」后长期不再弹出，适合必须送达的规则通知。'
}

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', status: null, popupMode: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  title: '', content: '', type: 1, targetType: 1, schoolId: null, level: 1, popupMode: 0,
  publishAt: null, offlineAt: null
})
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

// 投放给后台管理员的公告不进小程序，弹窗配置要跟着归零，
// 否则提交时会被后端以「不能开启小程序弹窗」挡回来
watch(() => form.targetType, (v) => {
  if (v === 1) form.popupMode = 0
})

async function load () {
  loading.value = true
  try {
    const res = await noticeApi.page(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.status = null; query.popupMode = null; reload() }

/** 后端时间是 yyyy-MM-dd HH:mm:ss，iOS 不认空格分隔，换 T 再解析 */
function parseTime (s) {
  const t = new Date(String(s || '').replace(' ', 'T')).getTime()
  return isNaN(t) ? 0 : t
}

/** 已发布但发布时间在未来 = 预约中。不标出来的话运营会以为已经上线了 */
function isReserving (row) {
  return !!row && row.status === 1 && !!row.publishAt && parseTime(row.publishAt) > Date.now()
}

function statusOf (row) {
  if (isReserving(row)) return { label: '预约中', type: 'warning' }
  return tagOf(NOTICE_STATUS, row.status)
}

function openCreate () {
  editing.value = null
  Object.assign(form, {
    title: '', content: '', type: 1, targetType: 1, schoolId: null, level: 1, popupMode: 0,
    publishAt: null, offlineAt: null
  })
  dialogVisible.value = true
}

async function openEdit (row) {
  editing.value = row
  const d = await noticeApi.detail(row.id)
  Object.assign(form, {
    title: d.title, content: d.content, type: d.type,
    targetType: d.targetType, schoolId: d.schoolId, level: d.level,
    popupMode: d.popupMode == null ? 0 : d.popupMode,
    publishAt: d.publishAt || null,
    offlineAt: d.offlineAt || null
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await noticeApi.update(editing.value.id, { ...form })
    } else {
      await noticeApi.create({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function publish (row) {
  await noticeApi.publish(row.id)
  ElMessage.success('已发布')
  load()
}

async function offline (row) {
  await noticeApi.offline(row.id)
  ElMessage.success('已下线')
  load()
}

async function remove (row) {
  await ElMessageBox.confirm('删除公告「' + row.title + '」？', '危险操作', { type: 'warning' }).catch(() => false)
  await noticeApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
/* 弹窗频次的解释文案：选项本身只有 4 个字，运营很难判断后果，补一行说明 */
.field-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

/* 送达/确认是给运营看的指标，用等宽数字避免列宽跳动 */
.stat {
  font-variant-numeric: tabular-nums;
  color: #606266;
  cursor: help;
}

/* 预约中的发布时间弱化显示，真正的提示在旁边的「预约」标签上 */
.reserving {
  color: #e6a23c;
}
</style>
