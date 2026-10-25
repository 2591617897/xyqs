<template>
  <div class="page-card">
    <el-alert type="warning" :closable="false" show-icon class="tip">
      这棵树决定数据权限：管理员被指派为某节点负责人后，只能看到该节点展开出的校区数据。
      删除节点前会校验其下是否还有子节点或负责人。
    </el-alert>

    <div class="toolbar">
      <el-button v-permission="'admin:region:create'" type="success" :icon="Plus" @click="openCreate(null)">
        新建顶级节点
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tree" row-key="id" border default-expand-all
              :tree-props="{ children: 'children' }">
      <el-table-column prop="name" label="名称" min-width="200" />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(REGION_TYPE, row.type).type" size="small">
            {{ tagOf(REGION_TYPE, row.type).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="层级" width="90" align="center">
        <template #default="{ row }">{{ LEVEL[row.level] || row.level }}</template>
      </el-table-column>
      <el-table-column label="定向" min-width="200">
        <template #default="{ row }">
          <span v-if="row.type === 2">{{ schoolLabel(row) }}</span>
          <span v-else>{{ [row.province, row.city].filter(Boolean).join(' / ') || '全国' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:region:create'" link type="primary" @click="openCreate(row)">
            加子节点
          </el-button>
          <el-button v-permission="'admin:region:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:region:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有组织节点，先建一个地区" :image-size="80" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑节点' : '新建节点'" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：贵阳市 / 贵州大学" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio :value="1">地区</el-radio>
            <el-radio :value="2">校区</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.type === 1">
          <el-form-item label="省份" prop="province">
            <el-select v-model="form.province" filterable clearable placeholder="选择省份（取自学校字典）"
                       style="width:100%" :loading="areaLoading" @change="onProvinceChange">
              <el-option v-for="a in areas" :key="a.province" :label="a.province" :value="a.province" />
            </el-select>
          </el-form-item>
          <el-form-item label="城市">
            <el-select v-model="form.city" filterable clearable :disabled="!form.province"
                       :placeholder="form.province ? '不选＝全省辖区' : '请先选择省份'"
                       style="width:100%" @change="fillName">
              <el-option v-for="c in cityOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="层级">
            <span class="level-hint">{{ levelHint }}</span>
          </el-form-item>
        </template>

        <el-form-item v-else label="关联校区" prop="refSchoolId">
          <el-select v-model="form.refSchoolId" filterable clearable :loading="schoolLoading"
                     placeholder="搜索学校名称（如 贵州大学）" style="width:100%" @change="onSchoolChange">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id">
              <span>{{ s.name }}</span>
              <span class="opt-meta">{{ [s.province, s.city].filter(Boolean).join(' / ') }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { regionApi } from '@/api/operate'
import { REGION_TYPE, tagOf } from '@/utils/dict'

const LEVEL = { 1: '省', 2: '市', 3: '校区' }

const tree = ref([])
const loading = ref(false)

/** 省 → 市 选项：来自后端 t_school 去重结果，不从写死的行政区划里取，避免拼错导致辖区为空 */
const areas = ref([])
const areaLoading = ref(false)
/** 校区选项：供「关联校区」下拉，也用来把树里的 refSchoolId 反查成学校名 */
const schools = ref([])
const schoolLoading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const parent = ref(null)
const formRef = ref()
const form = reactive({
  parentId: 0, name: '', type: 1, level: 1, province: '', city: '', refSchoolId: null, sort: 0, status: 1
})

const parentName = computed(() => (parent.value ? parent.value.name : '（顶级节点）'))

const cityOptions = computed(() => {
  const hit = areas.value.find(a => a.province === form.province)
  return hit ? hit.cities : []
})

/** 层级不再手选：选了城市就是市级，只选省就是省级，校区节点固定第 3 层 */
const levelHint = computed(() => {
  if (form.type === 2) return '校区（第 3 层）'
  return form.city ? '市（第 2 层）' : '省（第 1 层，不选城市即为整省辖区）'
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  province: [{
    trigger: 'change',
    validator: (rule, value, cb) => (form.type === 1 && !value ? cb(new Error('请选择省份')) : cb())
  }],
  refSchoolId: [{
    trigger: 'change',
    validator: (rule, value, cb) => (form.type === 2 && !value ? cb(new Error('请选择关联校区')) : cb())
  }]
}

async function load () {
  loading.value = true
  try {
    tree.value = await regionApi.tree() || []
  } finally {
    loading.value = false
  }
}

async function loadAreas () {
  areaLoading.value = true
  try {
    areas.value = await regionApi.areas() || []
  } catch (e) {
    // 后端没重启时 /regions/areas 会 404，省市下拉会是空的、地区节点就没法保存
    ElMessage.warning('省市字典加载失败，请确认 campus-admin 已重启')
  } finally {
    areaLoading.value = false
  }
}

async function loadSchools () {
  schoolLoading.value = true
  try {
    schools.value = await regionApi.schools({}) || []
  } catch (e) {
    // 校区下拉为空时不影响地区节点，树里会退回显示校区 ID
  } finally {
    schoolLoading.value = false
  }
}

function schoolLabel (row) {
  const hit = schools.value.find(s => String(s.id) === String(row.refSchoolId))
  return hit ? hit.name : ('校区ID ' + row.refSchoolId)
}

function fillName () {
  if (form.name) return
  form.name = form.city || form.province || ''
}

function onProvinceChange () {
  // 换省份后原城市可能不属于新省份，直接清掉，避免存进一个匹配不到的省市组合
  if (form.city && !cityOptions.value.includes(form.city)) {
    form.city = ''
  }
  fillName()
}

function onTypeChange (type) {
  if (type === 2) {
    form.level = 3
  } else {
    form.level = form.city ? 2 : 1
  }
}

function onSchoolChange (val) {
  const hit = schools.value.find(s => String(s.id) === String(val))
  if (!hit) return
  form.province = hit.province || ''
  form.city = hit.city || ''
  if (!form.name) form.name = hit.name
}

function openCreate (row) {
  editing.value = null
  parent.value = row
  Object.assign(form, {
    parentId: row ? row.id : 0, name: '', type: row && row.type === 2 ? 2 : 1,
    level: row ? Math.min(3, row.level + 1) : 1,
    province: row ? row.province : '', city: row ? row.city : '',
    refSchoolId: null, sort: 0, status: 1
  })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  parent.value = null
  Object.assign(form, {
    parentId: row.parentId, name: row.name, type: row.type, level: row.level,
    province: row.province || '', city: row.city || '', refSchoolId: row.refSchoolId,
    sort: row.sort, status: row.status
  })
  dialogVisible.value = true
}

/** 提交前按当前选择把 level 归一，避免出现「类型=校区、层级=市」这种无效组合 */
function buildPayload () {
  const level = form.type === 2 ? 3 : (form.city ? 2 : 1)
  return { ...form, level }
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await regionApi.update(editing.value.id, buildPayload())
    } else {
      await regionApi.create(buildPayload())
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function remove (row) {
  await ElMessageBox.confirm('删除节点「' + row.name + '」？', '危险操作', { type: 'warning' }).catch(() => false)
  await regionApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadAreas()
  loadSchools()
})
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.toolbar { margin-bottom: 12px; }
.level-hint { color: var(--text-3); font-size: 13px; }
.opt-meta { float: right; color: var(--text-3); font-size: 12px; }
</style>
