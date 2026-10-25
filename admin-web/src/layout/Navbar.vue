<template>
  <el-header class="app-navbar">
    <div class="app-navbar-left">
      <el-icon class="app-fold" :size="20" @click="$emit('toggle')">
        <component :is="collapsed ? 'Expand' : 'Fold'" />
      </el-icon>
      <span class="app-breadcrumb">{{ title }}</span>
    </div>
    <div class="app-navbar-right">
      <el-tag v-if="isSuper" type="danger" size="small" effect="plain">超级管理员</el-tag>
      <el-dropdown @command="onCommand">
        <span class="app-user">
          <el-avatar :size="28" :src="avatar">{{ avatar ? '' : username.charAt(0) }}</el-avatar>
          <span class="app-username">{{ username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 强制改密：初始密码登录后必须改 -->
    <el-dialog v-model="pwdVisible" title="修改登录密码" width="420px"
               :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确 定</el-button>
      </template>
    </el-dialog>
  </el-header>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { authApi } from '@/api/system'
import { resetDynamic } from '@/router/dynamic'

defineEmits(['toggle'])

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const title = computed(() => router.currentRoute.value.meta?.title || '')
const isSuper = computed(() => userStore.isSuper)
const username = computed(() => userStore.username || '未登录')
const avatar = computed(() => (userStore.info && userStore.info.avatar) || '')

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' }
  ]
}

// 首次进入若还在用初始密码，强弹改密对话框（不可关闭）
watch(isSuper, () => {}, { immediate: true })
onMounted(() => {
  if (userStore.mustChangePwd) pwdVisible.value = true
})

async function submitPwd () {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdLoading.value = true
  try {
    await authApi.changePassword({ ...pwdForm })
    ElMessage.success('密码修改成功，请重新登录')
    authApi.logout().catch(() => {})
    userStore.reset()
    resetDynamic()
    router.replace('/login')
  } catch (e) {
    // 提示已在拦截器统一处理
  } finally {
    pwdLoading.value = false
  }
}

async function onCommand (cmd) {
  if (cmd === 'password') {
    pwdVisible.value = true
    return
  }
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' }).catch(() => false)
  authApi.logout().catch(() => {})
  userStore.reset()
  resetDynamic()
  router.replace('/login')
}
</script>

<style scoped>
.app-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #f0f1f3;
  padding: 0 16px;
  height: 60px;
}
.app-navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.app-fold {
  cursor: pointer;
  color: #475569;
}
.app-breadcrumb {
  font-size: 14px;
  color: #475569;
}
.app-navbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.app-user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}
.app-username {
  font-size: 14px;
  color: #0f172a;
}
</style>
