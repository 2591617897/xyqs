<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      素材必须先通过审核才能上架；所属广告位未上架时，素材也不能上架。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="广告位">
        <el-select v-model="query.slotId" placeholder="全部" clearable filterable style="width: 200px">
          <el-option v-for="s in slots" :key="s.id" :label="s.name + '（' + s.code + '）'" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="query.keyword" placeholder="素材标题" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="审核">
        <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in AUDIT_STATUS" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:ad:material:create'" type="success" :icon="Plus" @click="openCreate">
          新建素材
        </el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="素材图" width="110" align="center">
        <template #default="{ row }">
          <el-image :src="row.image" :preview-src-list="[row.image]" fit="cover" class="thumb" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="slotName" label="广告位" width="150" show-overflow-tooltip />
      <el-table-column label="跳转" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ LINK_TYPE[row.linkType] || '无' }}{{ row.linkValue ? ' · ' + row.linkValue : '' }}
        </template>
      </el-table-column>
      <el-table-column label="审核" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(AUDIT_STATUS, row.auditStatus).type" size="small">
            {{ tagOf(AUDIT_STATUS, row.auditStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据" width="130" align="center">
        <template #default="{ row }">曝光 {{ row.viewCount }} · 点击 {{ row.clickCount }}</template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:ad:material:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:ad:material:audit'" link type="warning" @click="openAudit(row)">审核</el-button>
          <el-button v-permission="'admin:ad:material:online'" link :type="row.status === 1 ? 'info' : 'success'"
                     @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-permission="'admin:ad:material:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑素材' : '新建素材'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="广告位" prop="slotId">
          <el-select v-model="form.slotId" filterable style="width: 100%">
            <el-option v-for="s in slots" :key="s.id" :label="s.name + '（' + s.code + '）'" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="素材图" prop="image">
          <el-upload class="uploader" :show-file-list="false" :http-request="doUpload"
                     :before-upload="beforeUpload" accept="image/*">
            <img v-if="form.image" :src="form.image" class="preview" />
            <el-icon v-else class="uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div class="hint">点击上传，支持 jpg/png/gif/webp，不超过 5MB</div>
        </el-form-item>
        <el-form-item label="跳转类型" prop="linkType">
          <el-select v-model="form.linkType" style="width: 100%">
            <el-option v-for="(label, k) in LINK_TYPE" :key="k" :label="label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.linkType !== 0" label="跳转目标" prop="linkValue">
          <el-input v-model="form.linkValue" placeholder="帖子ID / 商品ID / URL / 页面路径" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保 存</el-button>
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
import { adApi, uploadApi } from '@/api/operate'
import { AUDIT_STATUS, tagOf } from '@/utils/dict'

const LINK_TYPE = { 0: '无跳转', 1: '帖子', 2: '商品', 3: '外链', 4: '小程序页面' }

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const slots = ref([])
const query = reactive({ slotId: null, keyword: '', auditStatus: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ slotId: null, title: '', image: '', linkType: 0, linkValue: '', sort: 0 })
const rules = {
  slotId: [{ required: true, message: '请选择广告位', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  image: [{ required: true, message: '请上传素材图', trigger: 'change' }]
}

async function loadSlots () {
  const res = await adApi.slots({ size: 100 })
  slots.value = res.records || []
}

async function load () {
  loading.value = true
  try {
    const res = await adApi.materials(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.slotId = null; query.keyword = ''; query.auditStatus = null; reload() }

function beforeUpload (file) {
  const ok = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!ok) {
    ElMessage.error('只支持 jpg / png / gif / webp')
    return false
  }
  return file.size <= 5 * 1024 * 1024
}

async function doUpload ({ file }) {
  try {
    form.image = await uploadApi.image(file)
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

function openCreate () {
  editing.value = null
  Object.assign(form, { slotId: query.slotId, title: '', image: '', linkType: 0, linkValue: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    slotId: row.slotId, title: row.title, image: row.image,
    linkType: row.linkType, linkValue: row.linkValue || '', sort: row.sort
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await adApi.updateMaterial(editing.value.id, { ...form })
    } else {
      await adApi.createMaterial({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openAudit (row) {
  const pass = await ElMessageBox.confirm('「确定」= 通过（允许上架），「取消」= 驳回',
    '审核素材', { confirmButtonText: '通过', cancelButtonText: '驳回', type: 'warning' })
    .then(() => true).catch(() => false)
  let remark = null
  if (!pass) {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回', {}).catch(() => ({ value: null }))
    if (!value) return
    remark = value
  }
  await adApi.auditMaterial(row.id, pass, remark)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

async function toggle (row) {
  await adApi.setMaterialStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  load()
}

async function remove (row) {
  await ElMessageBox.confirm('删除素材「' + row.title + '」？', '危险操作', { type: 'warning' }).catch(() => false)
  await adApi.removeMaterial(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => { loadSlots(); load() })
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.thumb { width: 72px; height: 44px; border-radius: 4px; }
.uploader { width: 200px; height: 120px; border: 1px dashed #d9d9d9; border-radius: 6px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; overflow: hidden; }
.uploader-icon { font-size: 28px; color: #8c939d; }
.preview { width: 200px; height: 120px; object-fit: cover; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
