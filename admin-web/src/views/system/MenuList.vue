<template>
  <div class="page-card">
    <div class="page-toolbar">
      <span class="text-muted">菜单与权限点。改 component 字段要同步改前端 views 下的 .vue 路径，否则动态路由会落 404。</span>
      <el-button v-permission="'admin:menu:create'" type="success" :icon="Plus" @click="openCreate(null)">新建顶层</el-button>
    </div>

    <PageTable :data="tree" :total="tree.length" :page="1" :size="1000" :loading="loading" row-key="id">
      <el-table-column prop="name" label="菜单名称" min-width="200" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(MENU_TYPE, row.type).type" size="small">{{ tagOf(MENU_TYPE, row.type).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由" width="180" show-overflow-tooltip />
      <el-table-column prop="component" label="组件" width="200" show-overflow-tooltip />
      <el-table-column prop="permission" label="权限点" min-width="220" show-overflow-tooltip />
      <el-table-column prop="icon" label="图标" width="110" />
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:menu:create'" link type="primary" @click="openCreate(row)">新增下级</el-button>
          <el-button v-permission="'admin:menu:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新建菜单'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="form.parentId" :data="tree" :props="{ label: 'name', children: 'children' }"
                          node-key="id" check-strictly clearable style="width: 100%"
                          :render-after-expand="false" placeholder="顶层" />
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="如 /content/posts" />
        </el-form-item>
        <el-form-item label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="如 content/PostList" />
        </el-form-item>
        <el-form-item label="权限点" prop="permission">
          <el-input v-model="form.permission" placeholder="如 admin:content:post:hide（按钮必填）" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Odometer" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageTable from '@/components/PageTable.vue'
import { menuApi } from '@/api/system'
import { MENU_TYPE, tagOf } from '@/utils/dict'

const tree = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  parentId: 0, name: '', type: 2, path: '', component: '', permission: '', icon: '', sort: 0, status: 1, visible: 1
})
const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

async function load () {
  loading.value = true
  try {
    tree.value = await menuApi.tree()
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    loading.value = false
  }
}

function openCreate (parent) {
  editing.value = null
  Object.assign(form, {
    parentId: parent ? parent.id : 0, name: '', type: parent ? 2 : 1,
    path: '', component: '', permission: '', icon: '', sort: 0, status: 1, visible: 1
  })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    parentId: row.parentId || 0, name: row.name, type: row.type, path: row.path, component: row.component,
    permission: row.permission, icon: row.icon, sort: row.sort, status: row.status, visible: row.visible
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await menuApi.update(editing.value.id, { ...form })
    } else {
      await menuApi.create({ ...form })
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
  await ElMessageBox.confirm('确定删除「' + row.name + '」吗？', '危险操作', { type: 'warning' }).catch(() => false)
  await menuApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>
