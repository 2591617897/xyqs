<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      level 决定命中后的行为：仅记录（留痕不拦）/ 替换成 *** / 禁止发布。
      增删改会自动刷新服务内存缓存，如怀疑未生效可手动点「刷新缓存」。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="敏感词" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="(v, k) in SENSITIVE_LEVEL" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:sensitive:create'" type="success" :icon="Plus" @click="openCreate">
          新建
        </el-button>
        <el-button v-permission="'admin:sensitive:reload'" :icon="Refresh" @click="reloadCache">刷新缓存</el-button>
        <el-button :icon="Search" @click="checkVisible = true">试检测</el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="word" label="敏感词" min-width="160" />
      <el-table-column label="等级" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(SENSITIVE_LEVEL, row.level).type" size="small">
            {{ tagOf(SENSITIVE_LEVEL, row.level).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="替换为" width="120">
        <template #default="{ row }">{{ row.level === 2 ? (row.replaceTo || '***') : '-' }}</template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="hitCount" label="命中次数" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:sensitive:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:sensitive:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑敏感词' : '新建敏感词'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="form.word" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">仅记录</el-radio>
            <el-radio :value="2">替换</el-radio>
            <el-radio :value="3">禁止发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.level === 2" label="替换为" prop="replaceTo">
          <el-input v-model="form.replaceTo" placeholder="默认 ***" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" clearable style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保 存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="敏感词试检测" width="520px">
      <el-input v-model="checkText" type="textarea" :rows="4" placeholder="输入待检测文本" />
      <div v-if="hits.length" class="hits">
        命中 {{ hits.length }} 个：
        <el-tag v-for="h in hits" :key="h.id" size="small" type="danger" style="margin-right: 6px">
          {{ h.word }}
        </el-tag>
      </div>
      <div v-else-if="checked" class="safe">未命中，文本可正常发布</div>
      <template #footer>
        <el-button @click="checkVisible = false">关 闭</el-button>
        <el-button type="primary" @click="doCheck">检 测</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { sensitiveApi } from '@/api/operate'
import { SENSITIVE_LEVEL, tagOf } from '@/utils/dict'

const CATEGORIES = ['色情', '暴力', '政治', '广告', '诈骗', '其他']

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', level: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ word: '', level: 2, replaceTo: '***', category: '', status: 1 })
const rules = { word: [{ required: true, message: '请输入敏感词', trigger: 'blur' }] }

const checkVisible = ref(false)
const checkText = ref('')
const hits = ref([])
const checked = ref(false)

async function load () {
  loading.value = true
  try {
    const res = await sensitiveApi.page(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.level = null; reload() }

function openCreate () {
  editing.value = null
  Object.assign(form, { word: '', level: 2, replaceTo: '***', category: '', status: 1 })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    word: row.word, level: row.level, replaceTo: row.replaceTo || '***',
    category: row.category, status: row.status
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await sensitiveApi.update(editing.value.id, { ...form })
    } else {
      await sensitiveApi.create({ ...form })
    }
    ElMessage.success('保存成功，缓存已刷新')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function remove (row) {
  await ElMessageBox.confirm('删除敏感词「' + row.word + '」？', '危险操作', { type: 'warning' }).catch(() => false)
  await sensitiveApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function reloadCache () {
  const n = await sensitiveApi.reload()
  ElMessage.success('缓存已刷新，当前启用 ' + n + ' 条')
}

async function doCheck () {
  if (!checkText.value) return
  hits.value = await sensitiveApi.check(checkText.value) || []
  checked.value = true
}

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.hits { margin-top: 12px; }
.safe { margin-top: 12px; color: #67c23a; }
</style>
