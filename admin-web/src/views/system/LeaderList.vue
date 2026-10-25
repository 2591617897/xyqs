<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      指派后该管理员的数据范围会自动切成「仅辖区」——否则身份与权限不一致，等于给了个假权限。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="组织节点">
        <el-tree-select v-model="query.regionId" :data="tree" :props="treeProps" check-strictly
                        clearable filterable placeholder="全部" style="width: 260px"
                        @change="reload" />
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:leader:assign'" type="success" :icon="Plus" @click="openAssign">
          指派负责人
        </el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="id" label="记录ID" width="180" show-overflow-tooltip />
      <el-table-column label="组织节点" min-width="180">
        <template #default="{ row }">{{ regionName(row.regionId) }}</template>
      </el-table-column>
      <el-table-column label="管理员" min-width="160">
        <template #default="{ row }">{{ adminName(row.adminUserId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.leaderType === 1 ? 'success' : 'info'" size="small">
            {{ row.leaderType === 1 ? '负责人' : '协管' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="指派时间" width="170" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:leader:revoke'" link type="danger" @click="revoke(row)">撤销</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" title="指派负责人" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="组织节点" prop="regionId">
          <el-tree-select v-model="form.regionId" :data="tree" :props="treeProps" check-strictly
                          filterable placeholder="请选择节点" style="width: 100%" />
        </el-form-item>
        <el-form-item label="管理员" prop="adminId">
          <el-select v-model="form.adminId" filterable placeholder="请选择管理员" style="width: 100%">
            <el-option v-for="a in admins" :key="a.id" :label="a.username + (a.realName ? '（' + a.realName + '）' : '')"
                       :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="leaderType">
          <el-radio-group v-model="form.leaderType">
            <el-radio :value="1">负责人</el-radio>
            <el-radio :value="2">协管</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确 定</el-button>
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
import { regionApi } from '@/api/operate'
import { adminApi } from '@/api/system'

const treeProps = { label: 'name', value: 'id', children: 'children' }

const tree = ref([])
const admins = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ regionId: null, page: 1, size: 10 })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ regionId: null, adminId: null, leaderType: 1 })
const rules = {
  regionId: [{ required: true, message: '请选择组织节点', trigger: 'change' }],
  adminId: [{ required: true, message: '请选择管理员', trigger: 'change' }]
}

async function loadTree () {
  tree.value = await regionApi.tree() || []
}

async function loadAdmins () {
  const res = await adminApi.page({ size: 100 })
  admins.value = res.records || []
}

async function load () {
  loading.value = true
  try {
    const list = await regionApi.leaders(query.regionId) || []
    rows.value = list
    total.value = list.length
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.regionId = null; reload() }

function regionName (id) {
  const hit = findNode(tree.value, id)
  return hit ? hit.name : String(id)
}

function adminName (id) {
  const hit = admins.value.find((a) => String(a.id) === String(id))
  return hit ? hit.username : String(id)
}

function findNode (nodes, id) {
  for (const n of nodes || []) {
    if (String(n.id) === String(id)) return n
    const sub = findNode(n.children, id)
    if (sub) return sub
  }
  return null
}

function openAssign () {
  Object.assign(form, { regionId: query.regionId, adminId: null, leaderType: 1 })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await regionApi.assignLeader(form.regionId, form.adminId, form.leaderType)
    ElMessage.success('已指派，该管理员数据范围已切换为「仅辖区」')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function revoke (row) {
  await ElMessageBox.confirm('撤销后该管理员将看不到该辖区数据，确认？', '撤销负责人', { type: 'warning' })
    .catch(() => false)
  await regionApi.revokeLeader(row.id)
  ElMessage.success('已撤销')
  load()
}

onMounted(() => { loadTree(); loadAdmins(); load() })
</script>

<style scoped>
.tip { margin-bottom: 14px; }
</style>
