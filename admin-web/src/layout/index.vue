<template>
  <el-container class="app-layout">
    <el-aside :width="collapsed ? 'var(--side-w-mini)' : 'var(--side-w)'" class="app-aside">
      <div class="aside-glow" aria-hidden="true" />

      <div class="app-brand">
        <div class="brand-mark">
          <el-icon :size="17"><School /></el-icon>
        </div>
        <transition name="brand-text">
          <div v-show="!collapsed" class="brand-text">
            <strong>校园聚</strong>
            <span>MANAGE CONSOLE</span>
          </div>
        </transition>
      </div>

      <el-scrollbar class="aside-scroll">
        <SidebarMenu :menus="menus" :collapsed="collapsed" />
      </el-scrollbar>

      <div class="app-aside-foot" :class="{ 'is-mini': collapsed }">
        <div class="foot-dot" />
        <template v-if="!collapsed">
          <span class="foot-text">服务正常</span>
          <span class="foot-ver">v1.0</span>
        </template>
      </div>
    </el-aside>

    <el-container direction="vertical" class="app-body">
      <Navbar @toggle="toggle" />
      <el-main class="app-main">
        <router-view v-slot="{ Component, route }">
          <transition name="page" mode="out-in" appear>
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { School } from '@element-plus/icons-vue'
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
  try {
    let list = menus.value
    if (!list || !list.length) {
      await userStore.loadInfo(true)
      list = await userStore.loadMenus(true)
    }
    addDynamicRoutes(router, list)

    // 刷新后直接打开动态页时，首帧会先命中 404 兜底；路由注册完重新解析一次
    const current = router.currentRoute.value
    if (!current.matched.length || current.name === 'NotFound' || current.name === 'NotFoundTail') {
      router.replace(current.fullPath)
    }
  } catch (e) {
    ElMessage.error('菜单加载失败，请重新登录')
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
  overflow: hidden;
}

/* ---------------- 侧边栏 ---------------- */
.app-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--side-bg);
  transition: width .28s var(--ease);
  overflow: hidden;
  box-shadow: 2px 0 18px -12px rgba(4, 26, 21, .9);
  z-index: 10;
}

/* 顶部一抹品牌光晕，让深色侧栏不至于死板 */
.aside-glow {
  position: absolute;
  top: -120px;
  left: -60px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(150, 120, 255, .32) 0%, rgba(150, 120, 255, 0) 68%);
  pointer-events: none;
  animation: floatY 9s ease-in-out infinite;
}

.app-brand {
  position: relative;
  height: 60px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, .06);
  overflow: hidden;
}

.brand-mark {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  color: #2A1F55;
  background: linear-gradient(135deg, #E4DBFF 0%, #B9A4F2 48%, #8E74E0 100%);
  box-shadow: 0 6px 16px -6px rgba(160, 137, 240, .95), inset 0 1px 0 rgba(255, 255, 255, .6);
  transition: transform .4s var(--ease), box-shadow .4s var(--ease);
}

.app-brand:hover .brand-mark {
  transform: rotate(-8deg) scale(1.06);
  box-shadow: 0 8px 22px -6px rgba(160, 137, 240, 1), inset 0 1px 0 rgba(255, 255, 255, .72);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  white-space: nowrap;
}

.brand-text strong {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-text span {
  font-size: 9.5px;
  letter-spacing: 1.6px;
  color: rgba(201, 183, 255, .65);
  font-weight: 600;
}

.brand-text-enter-active,
.brand-text-leave-active {
  transition: opacity .2s ease, transform .24s var(--ease);
}
.brand-text-enter-from,
.brand-text-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

.aside-scroll {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
}

.app-aside-foot {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 20px;
  border-top: 1px solid rgba(255, 255, 255, .06);
  color: rgba(226, 244, 237, .5);
  font-size: 12px;
  transition: padding .28s var(--ease);
}

.app-aside-foot.is-mini {
  justify-content: center;
  padding: 0;
}

.foot-dot {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: #C9B7FF;
  animation: pulseRing 2.4s ease-out infinite;
}

.foot-text {
  white-space: nowrap;
}

.foot-ver {
  margin-left: auto;
  font-size: 11px;
  color: rgba(226, 244, 237, .32);
}

/* ---------------- 主区域 ---------------- */
.app-body {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-page);
  overflow: hidden;
}

/* 内容区右上角的极淡光斑，避免大面积纯灰 */
.app-body::after {
  content: '';
  position: absolute;
  top: -140px;
  right: -120px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 99, 216, .10) 0%, rgba(124, 99, 216, 0) 70%);
  pointer-events: none;
  z-index: 0;
}

.app-main {
  position: relative;
  z-index: 1;
  padding: 18px 20px 24px;
  overflow-y: auto;
}
</style>
