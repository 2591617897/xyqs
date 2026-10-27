<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="账号 / 姓名 / 手机号" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:admin:create'" type="success" :icon="Plus" @click="openCreate">新建管理员</el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="r in roleNames(row)" :key="r" size="small" type="info" style="margin-right: 4px">{{ r }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据权限" width="110">
        <template #default="{ row }">
          <el-tag :type="tagOf(DATA_SCOPE, row.dataScope).type" size="small">
            {{ tagOf(DATA_SCOPE, row.dataScope).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="超级管理员" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.superFlag === 1 ? 'danger' : 'info'" size="small">
            {{ row.superFlag === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(STATUS_NORMAL, row.status).type" size="small">
            {{ tagOf(STATUS_NORMAL, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" width="170" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:admin:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:admin:status'" link :type="row.status === 1 ? 'warning' : 'success'"
                     @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button v-permission="'admin:admin:reset-pwd'" link type="primary" @click="openReset(row)">重置密码</el-button>
          <el-button v-permission="'admin:admin:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑管理员' : '新建管理员'" width="520px"
               @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="!!editing" placeholder="4-32 位" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="editing ? '留空表示不修改' : '8-64 位，含大小写/数字/符号至少三类'" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于邮箱找回密码，留空则只能由超管重置" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-radio-group v-model="form.dataScope">
            <el-radio :value="1">全部数据</el-radio>
            <el-radio :value="2">仅辖区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
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
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { adminApi, roleApi } from '@/api/system'
import { STATUS_NORMAL, DATA_SCOPE, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const roleOptions = ref([])
const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  username: '', password: '', realName: '', phone: '', email: '',
  dataScope: 1, status: 1, roleIds: []
})
// 密码：新建必填；编辑时留空＝不修改，只在填了内容时才校验格式与强度
function validatePassword (rule, value, callback) {
  const raw = value == null ? '' : String(value)
  if (!raw.trim()) {
    return editing.value ? callback() : callback(new Error('请输入初始密码'))
  }
  if (raw.length < 8 || raw.length > 64) {
    return callback(new Error('密码长度需在 8-64 之间'))
  }
  if (form.username && raw.toLowerCase().includes(String(form.username).toLowerCase())) {
    return callback(new Error('密码不能包含账号名'))
  }
  let kinds = 0
  if (/[a-z]/.test(raw)) kinds++
  if (/[A-Z]/.test(raw)) kinds++
  if (/\d/.test(raw)) kinds++
  if (/[^A-Za-z0-9]/.test(raw)) kinds++
  if (kinds < 3) {
    return callback(new Error('密码需包含大写字母、小写字母、数字、符号中的至少三类'))
  }
  callback()
}

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  roleIds: [{ required: true, message: '请至少选择一个角色', trigger: 'change' }]
}

async function load () {
  loading.value = true
  try {
    const res = await adminApi.page(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    loading.value = false
  }
}

function reload () {
  query.page = 1
  load()
}

function reset () {
  query.keyword = ''
  query.status = null
  reload()
}

async function loadRoles () {
  try {
    const res = await roleApi.page({ size: 100 })
    roleOptions.value = res.records || []
  } catch (e) {
    roleOptions.value = []
  }
}

function roleNames (row) {
  const ids = row.roleIds || []
  return ids.map((id) => {
    const hit = roleOptions.value.find((r) => String(r.id) === String(id))
    return hit ? hit.name : String(id)
  })
}

function openCreate () {
  editing.value = null
  Object.assign(form, { username: '', password: '', realName: '', phone: '', email: '', dataScope: 1, status: 1, roleIds: [] })
  dialogVisible.value = true
  nextTick(() => formRef.value && formRef.value.clearValidate())
}

async function openEdit (row) {
  editing.value = row
  const detail = await adminApi.detail(row.id)
  Object.assign(form, {
    username: detail.username, password: '', realName: detail.realName, phone: detail.phone,
    email: detail.email || '', dataScope: detail.dataScope, status: detail.status, roleIds: detail.roleIds || []
  })
  dialogVisible.value = true
  nextTick(() => formRef.value && formRef.value.clearValidate())
}

function onDialogClosed () {
  formRef.value && formRef.value.clearValidate()
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 编辑时密码留空表示「不修改」，不要把空串发给后端
    const payload = { ...form }
    if (!String(payload.password || '').trim()) delete payload.password
    if (editing.value) {
      await adminApi.update(editing.value.id, payload)
    } else {
      await adminApi.create(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function toggleStatus (row) {
  await adminApi.changeStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  load()
}

async function openReset (row) {
  const { value } = await ElMessageBox.prompt('请输入新密码（至少 8 位）', '重置密码', {
    inputType: 'password',
    inputPattern: /^.{8,}$/,
    inputErrorMessage: '新密码至少 8 位'
  }).catch(() => ({ value: null }))
  if (!value) return
  await adminApi.resetPassword(row.id, value)
  ElMessage.success('密码已重置，该账号下次登录需修改密码')
}

async function remove (row) {
  await ElMessageBox.confirm('确定删除管理员「' + row.username + '」吗？', '危险操作', { type: 'warning' })
    .catch(() => false)
  await adminApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  loadRoles()
  load()
})
</script>
