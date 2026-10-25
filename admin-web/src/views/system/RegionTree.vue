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
      <el-table-column label="定向" min-width="160">
        <template #default="{ row }">
          {{ row.type === 2 ? ('校区ID ' + row.refSchoolId) : ([row.province, row.city].filter(Boolean).join(' / ') || '-') }}
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑节点' : '新建节点'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">地区</el-radio>
            <el-radio :value="2">校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="层级" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">省</el-radio>
            <el-radio :value="2">市</el-radio>
            <el-radio :value="3">校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.type === 1">
          <el-form-item label="省份">
            <el-input v-model="form.province" placeholder="如：贵州省（需与 t_school.province 一致）" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="form.city" placeholder="如：贵阳市（需与 t_school.city 一致）" />
          </el-form-item>
        </template>
        <el-form-item v-else label="关联校区ID" prop="refSchoolId">
          <el-input v-model.number="form.refSchoolId" placeholder="t_school.id" />
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

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const parent = ref(null)
const formRef = ref()
const form = reactive({
  parentId: 0, name: '', type: 1, level: 1, province: '', city: '', refSchoolId: null, sort: 0, status: 1
})
const rules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const parentName = computed(() => (parent.value ? parent.value.name : '（顶级节点）'))

async function load () {
  loading.value = true
  try {
    tree.value = await regionApi.tree() || []
  } finally {
    loading.value = false
  }
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

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await regionApi.update(editing.value.id, { ...form })
    } else {
      await regionApi.create({ ...form })
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

onMounted(load)
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.toolbar { margin-bottom: 12px; }
</style>
