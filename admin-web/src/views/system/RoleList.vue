<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="角色名称 / 编码" clearable style="width: 200px" />
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:role:create'" type="success" :icon="Plus" @click="openCreate">新建角色</el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
      <el-table-column prop="name" label="角色名称" width="160" />
      <el-table-column prop="code" label="角色编码" width="200" />
      <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(STATUS_NORMAL, row.status).type" size="small">
            {{ tagOf(STATUS_NORMAL, row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:role:grant'" link type="primary" @click="openGrant(row)">分配权限</el-button>
          <el-button v-permission="'admin:role:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:role:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <!-- 角色信息 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新建角色'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="大写字母/数字/下划线，如 OPERATOR" />
        </el-form-item>
        <el-form-item label="说明" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
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

    <!-- 分配权限：整棵树勾选后全量提交 -->
    <el-dialog v-model="grantVisible" :title="'分配权限 - ' + grantRoleName" width="560px">
      <el-tree ref="treeRef" :data="menuTree" show-checkbox node-key="id" default-expand-all
               :props="{ label: 'name', children: 'children' }" />
      <template #footer>
        <el-button @click="grantVisible = false">取 消</el-button>
        <el-button type="primary" :loading="granting" @click="submitGrant">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { roleApi, menuApi } from '@/api/system'
import { STATUS_NORMAL, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ name: '', code: '', remark: '', sort: 0, status: 1 })
const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{2,63}$/, message: '需大写字母开头，仅含大写字母/数字/下划线', trigger: 'blur' }
  ]
}

const grantVisible = ref(false)
const granting = ref(false)
const grantRoleName = ref('')
const grantRoleId = ref(null)
const treeRef = ref()
const menuTree = ref([])

async function load () {
  loading.value = true
  try {
    const res = await roleApi.page(query)
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
  reload()
}

function openCreate () {
  editing.value = null
  Object.assign(form, { name: '', code: '', remark: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, { name: row.name, code: row.code, remark: row.remark, sort: row.sort, status: row.status })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await roleApi.update(editing.value.id, { ...form })
    } else {
      await roleApi.create({ ...form })
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

async function remove (row) {
  await ElMessageBox.confirm('确定删除角色「' + row.name + '」吗？', '危险操作', { type: 'warning' }).catch(() => false)
  await roleApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function openGrant (row) {
  grantRoleId.value = row.id
  grantRoleName.value = row.name
  // 菜单树里也包含 type=3 的按钮节点，勾选它们才是真正的授权
  menuTree.value = await menuApi.tree()
  grantVisible.value = true
  await nextTick()
  const ids = await roleApi.menuIds(row.id)
  // 只勾选叶子，父节点由 el-tree 自行推导，避免半选态被当成全选提交
  treeRef.value.setCheckedKeys((ids || []).map(String), false)
}

async function submitGrant () {
  granting.value = true
  try {
    const checked = treeRef.value.getCheckedKeys(false)
    const half = treeRef.value.getHalfCheckedKeys()
    const menuIds = [...new Set([...checked, ...half])].map(Number)
    await roleApi.grantMenus(grantRoleId.value, menuIds)
    ElMessage.success('授权成功')
    grantVisible.value = false
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    granting.value = false
  }
}

onMounted(load)
</script>
