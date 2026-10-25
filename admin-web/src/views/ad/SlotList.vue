<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="名称">
        <el-input v-model="query.keyword" placeholder="广告位名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="位置">
        <el-select v-model="query.position" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(label, k) in AD_POSITION" :key="k" :label="label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:ad:slot:create'" type="success" :icon="Plus" @click="openCreate">
          新建广告位
        </el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="code" label="编码" width="150" show-overflow-tooltip />
      <el-table-column label="位置" width="120">
        <template #default="{ row }">{{ AD_POSITION[row.position] || row.position }}</template>
      </el-table-column>
      <el-table-column label="投放范围" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(AD_SCOPE, row.scopeType).type" size="small">
            {{ tagOf(AD_SCOPE, row.scopeType).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="定向" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.scopeType === 2 ? row.city : (row.scopeType === 3 ? row.schoolId : '全国') }}
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
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:ad:slot:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:ad:slot:update'" link type="warning" @click="openAudit(row)">审核</el-button>
          <el-button v-permission="'admin:ad:slot:status'" link :type="row.status === 1 ? 'info' : 'success'"
                     @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-permission="'admin:ad:slot:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑广告位' : '新建广告位'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" :disabled="!!editing" placeholder="小程序按此编码拉取" />
        </el-form-item>
        <el-form-item label="位置" prop="position">
          <el-select v-model="form.position" style="width: 100%">
            <el-option v-for="(label, k) in AD_POSITION" :key="k" :label="label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="投放范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType">
            <el-radio :value="1">全国</el-radio>
            <el-radio :value="2">城市</el-radio>
            <el-radio :value="3">校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 2" label="城市" prop="city">
          <el-input v-model="form.city" placeholder="如：贵阳市" />
        </el-form-item>
        <el-form-item v-if="form.scopeType === 3" label="校区ID" prop="schoolId">
          <el-input v-model.number="form.schoolId" placeholder="t_school.id" />
        </el-form-item>
        <el-form-item label="素材上限" prop="maxCount">
          <el-input-number v-model="form.maxCount" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="尺寸说明">
          <el-input v-model="form.sizeDesc" placeholder="如 750x300" />
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
import { adApi } from '@/api/operate'
import { AD_POSITION, AD_SCOPE, AUDIT_STATUS, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', position: null, status: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  name: '', code: '', position: 1, scopeType: 1, city: '', schoolId: null,
  maxCount: 3, sizeDesc: '', sort: 0
})
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

async function load () {
  loading.value = true
  try {
    const res = await adApi.slots(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.position = null; query.status = null; reload() }

function openCreate () {
  editing.value = null
  Object.assign(form, {
    name: '', code: '', position: 1, scopeType: 1, city: '', schoolId: null,
    maxCount: 3, sizeDesc: '', sort: 0
  })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    name: row.name, code: row.code, position: row.position, scopeType: row.scopeType,
    city: row.city || '', schoolId: row.schoolId, maxCount: row.maxCount,
    sizeDesc: row.sizeDesc || '', sort: row.sort
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await adApi.updateSlot(editing.value.id, { ...form })
    } else {
      await adApi.createSlot({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openAudit (row) {
  const pass = await ElMessageBox.confirm('选择「确定」表示审核通过，允许上架；「取消」表示驳回',
    '审核广告位', { confirmButtonText: '通过', cancelButtonText: '驳回', type: 'warning' })
    .then(() => true).catch(() => false)
  let remark = null
  if (!pass) {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回', {}).catch(() => ({ value: null }))
    if (!value) return
    remark = value
  }
  await adApi.auditSlot(row.id, pass, remark)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

async function toggle (row) {
  await adApi.setSlotStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  load()
}

async function remove (row) {
  await ElMessageBox.confirm('删除广告位「' + row.name + '」？(需先清空其下素材)', '危险操作', { type: 'warning' })
    .catch(() => false)
  await adApi.removeSlot(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
