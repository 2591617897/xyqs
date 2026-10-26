<template>
  <el-header class="app-navbar">
    <div class="app-navbar-left">
      <button class="icon-btn fold-btn" type="button" :title="collapsed ? '展开菜单' : '收起菜单'"
              @click="$emit('toggle')">
        <el-icon :size="17">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </button>

      <nav class="crumbs" aria-label="面包屑">
        <span class="crumb crumb-home" @click="router.push('/dashboard')">控制台</span>
        <el-icon class="crumb-sep" :size="12"><ArrowRight /></el-icon>
        <transition name="crumb" mode="out-in">
          <span :key="title" class="crumb crumb-current">{{ title || '概览' }}</span>
        </transition>
      </nav>
    </div>

    <div class="app-navbar-right">
      <button class="icon-btn" type="button" :title="isFullscreen ? '退出全屏' : '全屏'"
              @click="toggleFull">
        <el-icon :size="16">
          <component :is="isFullscreen ? 'Aim' : 'FullScreen'" />
        </el-icon>
      </button>

      <span class="nav-divider" />

      <el-dropdown trigger="click" @command="onCommand" @visible-change="(v) => (dropdownOpen = v)">
        <span class="app-user">
          <el-avatar :size="32" :src="avatar" class="user-avatar">
            {{ avatar ? '' : username.charAt(0) }}
          </el-avatar>
          <span class="user-meta">
            <span class="user-name">{{ username }}</span>
            <span class="user-role">{{ isSuper ? '超级管理员' : '管理员' }}</span>
          </span>
          <el-icon class="user-caret" :class="{ open: dropdownOpen }" :size="12">
            <CaretBottom />
          </el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">
              <el-icon><Key /></el-icon>修改密码
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 强制改密：初始密码登录后必须改 -->
    <!-- ⚠️ 必须 append-to-body：.app-navbar 上有 backdrop-filter，它会成为 position:fixed
         的包含块；el-dialog 默认原地渲染，遮罩就被压成顶栏那条 60px 高的框，
         对话框本体又因为 margin-top:15vh 落到框外（框还是 overflow:auto）——
         表现就是「点了修改密码没反应」。传送到 body 才按视口定位。 -->
    <el-dialog v-model="pwdVisible" title="修改登录密码" width="420px" append-to-body
               :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p class="dialog-hint">为了账号安全，使用初始密码登录后需要先修改密码。</p>
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CaretBottom, Key, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { authApi } from '@/api/system'
import { setToken } from '@/utils/request'
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

const dropdownOpen = ref(false)
const isFullscreen = ref(false)

function toggleFull () {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.().catch(() => {})
  } else {
    document.exitFullscreen?.()
  }
}

function onFsChange () {
  isFullscreen.value = !!document.fullscreenElement
}

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

onMounted(() => {
  document.addEventListener('fullscreenchange', onFsChange)
})

// 不能只在 onMounted 里判断一次：info 是异步拉的（Layout 挂载时才 loadInfo），
// 而子组件的 onMounted 先于父组件执行，那一刻 mustChangePwd 还是 false，
// 弹窗永远不自动出现 —— 只能靠用户手动点「修改密码」。用 watch 等它变成 true。
watch(
  () => userStore.mustChangePwd,
  (v) => { if (v) pwdVisible.value = true },
  { immediate: true }
)

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
})

async function submitPwd () {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdLoading.value = true
  try {
    // 后端改密后会让所有旧令牌失效（token_version +1），同时回一张新令牌，
    // 这里必须替换本地存储，否则用户会立刻被自己的改密操作踢下线。
    const newToken = await authApi.changePassword({ ...pwdForm })
    if (newToken) {
      setToken(newToken)
      userStore.token = newToken
    }
    ElMessage.success('密码修改成功')
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    // 重新拉一次自身信息，把 mustChangePwd 置回 false，解除页面的强制改密状态
    await userStore.loadInfo(true).catch(() => {})
    // 强制改密期间业务接口全被 403 拦掉（看板数据都是空的），改完刷新一次让各页重拉。
    // 令牌在 sessionStorage，刷新不掉登录态。
    setTimeout(() => window.location.reload(), 600)
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
  const ok = await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' }).catch(() => false)
  if (ok !== 'confirm') return
  authApi.logout().catch(() => {})
  userStore.reset()
  resetDynamic()
  router.replace('/login')
}
</script>

<style scoped>
.app-navbar {
  position: relative;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--nav-h);
  flex: none;
  padding: 0 20px 0 16px;
  background: rgba(255, 255, 255, .82);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--line-soft);
}

.app-navbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: all var(--dur) var(--ease);
}

.icon-btn:hover {
  background: var(--brand-50);
  border-color: var(--brand-100);
  color: var(--brand-600);
  transform: translateY(-1px);
}

.icon-btn:active {
  transform: scale(.94);
}

.icon-btn:focus-visible {
  outline: 2px solid var(--brand-300);
  outline-offset: 2px;
}

/* ---------------- 面包屑 ---------------- */
.crumbs {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  font-size: 13px;
}

.crumb {
  white-space: nowrap;
}

.crumb-home {
  color: var(--text-3);
  cursor: pointer;
  border-radius: var(--r-xs);
  padding: 2px 4px;
  transition: color var(--dur), background var(--dur);
}

.crumb-home:hover {
  color: var(--brand-500);
  background: var(--brand-50);
}

.crumb-sep {
  color: var(--line-strong);
}

.crumb-current {
  color: var(--text-1);
  font-weight: 600;
  padding-left: 2px;
}

.crumb-enter-active,
.crumb-leave-active {
  transition: opacity .18s ease, transform .22s var(--ease);
}
.crumb-enter-from {
  opacity: 0;
  transform: translateX(6px);
}
.crumb-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}

/* ---------------- 右侧 ---------------- */
.app-navbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-divider {
  width: 1px;
  height: 20px;
  margin: 0 4px;
  background: var(--line);
}

.app-user {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--r-pill);
  cursor: pointer;
  outline: none;
  transition: background var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.app-user:hover {
  background: var(--brand-50);
  box-shadow: inset 0 0 0 1px var(--brand-100);
}

.user-avatar {
  flex: none;
  background: var(--brand-gradient);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  box-shadow: 0 0 0 2px #fff, 0 0 0 3.5px var(--brand-100);
  transition: box-shadow var(--dur) var(--ease);
}

.app-user:hover .user-avatar {
  box-shadow: 0 0 0 2px #fff, 0 0 0 3.5px var(--brand-300);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 11px;
  color: var(--brand-500);
  letter-spacing: .2px;
}

.user-caret {
  color: var(--text-3);
  transition: transform var(--dur) var(--ease), color var(--dur);
}

.user-caret.open {
  transform: rotate(180deg);
  color: var(--brand-500);
}

.dialog-hint {
  margin: 0 0 16px;
  padding: 9px 12px;
  border-radius: var(--r-sm);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 12.5px;
  line-height: 1.5;
}

@media (max-width: 720px) {
  .user-meta {
    display: none;
  }
}
</style>
