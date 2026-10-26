<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="名称">
        <el-input v-model="query.keyword" placeholder="广告位名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="位置">
        <el-select v-model="query.position" placeholder="全部" clearable style="width: 160px">
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

    <el-alert v-if="invalidRows.length" type="warning" :closable="false" show-icon class="mb12"
              title="存在小程序不会读取的广告位">
      <template #default>
        以下广告位的编码不在小程序的投放清单里，<b>无论怎么上下架都不会展示</b>，
        建议删除后按「广告位」下拉重建：
        <span v-for="r in invalidRows" :key="r.id" class="bad-code">{{ r.name }}（{{ r.code }}）</span>
      </template>
    </el-alert>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="编码" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.code }}</span>
          <el-tag v-if="!optionOf(row.code)" type="danger" size="small" effect="dark">未对应</el-tag>
          <el-tag v-else-if="!optionOf(row.code).connected" type="info" size="small">未接入</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="位置" width="130">
        <template #default="{ row }">{{ positionLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="投放范围" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(AD_SCOPE, row.scopeType).type" size="small">
            {{ tagOf(AD_SCOPE, row.scopeType).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="定向" min-width="130" show-overflow-tooltip>
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
                     :disabled="row.status !== 1 && !canOnline(row)"
                     @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-permission="'admin:ad:slot:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑广告位' : '新建广告位'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="广告位" prop="code">
          <el-select
            v-model="form.code"
            :disabled="!!editing"
            filterable
            placeholder="选择小程序里的投放位置"
            style="width: 100%"
            @change="onCodeChange"
          >
            <el-option
              v-for="o in slotOptions"
              :key="o.code"
              :value="o.code"
              :label="o.name + (o.connected ? '' : '（小程序未接入）')"
            >
              <span>{{ o.name }}</span>
              <span class="opt-code">{{ o.code }}{{ o.connected ? '' : ' · 未接入' }}</span>
            </el-option>
          </el-select>
          <div v-if="currentOption" class="form-hint">
            {{ currentOption.pageHint }}　建议尺寸 {{ currentOption.sizeHint }}
          </div>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="默认与广告位同名，可改成对内可读的名字" />
        </el-form-item>
        <el-form-item label="投放范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType">
            <el-radio :value="1">全国</el-radio>
            <el-radio :value="2">城市</el-radio>
            <el-radio :value="3">校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 2" label="城市" prop="city">
          <el-input v-model="form.city" placeholder="如：贵阳（不带「市」字，与学校库一致）" />
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
import { ref, reactive, computed, onMounted } from 'vue'
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

/**
 * 可选编码来自后端注册表（AdSlotCatalog）——小程序会去拉的编码就是这里列出的这些。
 * 编码以前是手填的，填错一个字符就永远投不出去，且后台毫无提示。
 */
const slotOptions = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  name: '', code: '', scopeType: 1, city: '', schoolId: null,
  maxCount: 3, sizeDesc: '', sort: 0
})
const rules = {
  code: [{ required: true, message: '请选择广告位', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const currentOption = computed(() => optionOf(form.code))

/** 编码不在注册表里 = 小程序不会读取（历史脏数据 / 手工插库） */
function optionOf (code) {
  if (!code) return null
  return slotOptions.value.find((o) => o.code === code) || null
}

function positionLabel (row) {
  const o = optionOf(row.code)
  if (o) return o.name
  return AD_POSITION[row.position] || row.position
}

function canOnline (row) {
  const o = optionOf(row.code)
  return !!o && o.connected
}

const invalidRows = computed(() => rows.value.filter((r) => !optionOf(r.code)))

async function loadOptions () {
  try {
    slotOptions.value = (await adApi.slotCodes()) || []
  } catch (e) {
    // 拿不到清单时不做硬失败：列表照常显示，只是下拉为空
    slotOptions.value = []
  }
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

function onCodeChange (code) {
  const o = optionOf(code)
  if (!o) return
  // 选中即带出默认值，运营不用再手打名称和尺寸
  if (!form.name || slotOptions.value.some((x) => x.name === form.name)) form.name = o.name
  if (!form.sizeDesc) form.sizeDesc = o.sizeHint
}

function openCreate () {
  editing.value = null
  Object.assign(form, {
    name: '', code: '', scopeType: 1, city: '', schoolId: null,
    maxCount: 3, sizeDesc: '', sort: 0
  })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    name: row.name, code: row.code, scopeType: row.scopeType,
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

onMounted(async () => {
  await loadOptions()
  load()
})
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.bad-code {
  display: inline-block;
  margin: 0 8px 4px 0;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(230, 162, 60, 0.12);
  color: var(--warning-600, #b88230);
  font-size: 12px;
}
.opt-code {
  float: right;
  margin-left: 16px;
  color: var(--text-3, #909399);
  font-size: 12px;
}
.form-hint {
  width: 100%;
  margin-top: 4px;
  color: var(--text-3, #909399);
  font-size: 12px;
  line-height: 1.5;
}
</style>
