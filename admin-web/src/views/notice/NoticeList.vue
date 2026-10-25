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
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(NOTICE_STATUS, row.status).type" size="small">
            {{ tagOf(NOTICE_STATUS, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishAt" label="发布时间" width="170" />
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
import { ref, reactive, onMounted } from 'vue'
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

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ title: '', content: '', type: 1, targetType: 1, schoolId: null, level: 1 })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

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
function reset () { query.keyword = ''; query.status = null; reload() }

function openCreate () {
  editing.value = null
  Object.assign(form, { title: '', content: '', type: 1, targetType: 1, schoolId: null, level: 1 })
  dialogVisible.value = true
}

async function openEdit (row) {
  editing.value = row
  const d = await noticeApi.detail(row.id)
  Object.assign(form, {
    title: d.title, content: d.content, type: d.type,
    targetType: d.targetType, schoolId: d.schoolId, level: d.level
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
