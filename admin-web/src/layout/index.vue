<template>
  <el-container class="app-layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="app-aside">
      <div class="app-logo">
        <el-icon :size="20"><Grid /></el-icon>
        <span v-show="!collapsed" class="app-logo-text">校园聚</span>
      </div>
      <SidebarMenu :menus="menus" :collapsed="collapsed" />
    </el-aside>
    <el-container>
      <Navbar @toggle="toggle" />
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Grid } from '@element-plus/icons-vue'
import SidebarMenu from './Sidebar/SidebarMenu.vue'
import Navbar from './Navbar.vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { addDynamicRoutes, resetDynamic } from '@/router/dynamic'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const menus = computed(() => userStore.menus)

function toggle () {
  appStore.toggleSidebar()
}

async function ensureMenus () {
  if (!menus.value || !menus.value.length) {
    try {
      await userStore.loadInfo(true)
      const list = await userStore.loadMenus(true)
      addDynamicRoutes(router, list)
      router.replace(router.currentRoute.value.fullPath)
    } catch (e) {
      ElMessage.error('菜单加载失败，请重新登录')
    }
  }
}

onMounted(ensureMenus)

// 退出登录后回来要重新注册动态路由
if (!userStore.token) {
  resetDynamic()
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-aside {
  background: #001529;
  transition: width 0.2s;
  overflow-x: hidden;
}
.app-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #10b981;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.app-logo-text {
  color: #fff;
  font-size: 15px;
  white-space: nowrap;
}
.app-main {
  background: #f5f6f8;
  padding: 16px;
  overflow-y: auto;
}
</style>
