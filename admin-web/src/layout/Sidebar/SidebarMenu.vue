<template>
  <el-menu
    class="sidebar-menu"
    :default-active="activePath"
    :collapse="collapsed"
    :collapse-transition="false"
    background-color="#241A4D"
    text-color="rgba(226, 244, 237, 0.72)"
    active-text-color="#FFFFFF"
    router
    unique-opened
  >
    <template v-for="menu in menus" :key="menu.id">
      <el-sub-menu
        v-if="menu.children && menu.children.length"
        :index="String(menu.id)"
        :class="{ 'is-branch-active': branchActive(menu) }"
      >
        <template #title>
          <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
          <span>{{ menu.name }}</span>
        </template>
        <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.path">
          <el-icon><component :is="child.icon || 'Document'" /></el-icon>
          <span>{{ child.name }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else-if="menu.path" :index="menu.path">
        <el-icon><component :is="menu.icon || 'Document'" /></el-icon>
        <template #title>{{ menu.name }}</template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineProps({
  menus: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false }
})

const route = useRoute()
// 用当前路径做高亮，禁掉 el-menu 自己的 index 推断
const activePath = computed(() => route.path)

// 父级是否包含当前路由：包含时给父标题也点一层高亮，折叠态更好辨认
function branchActive (menu) {
  if (!menu.children || !menu.children.length) return false
  return menu.children.some((c) => c.path && route.path.indexOf(c.path) === 0)
}
</script>

<!-- 菜单内部结构由 Element Plus 渲染，这里用非 scoped 样式并以 .sidebar-menu 命名空间隔离 -->
<style>
.sidebar-menu {
  --el-menu-base-level-padding: 12px;
  --el-menu-level-padding: 10px;
  padding: 6px 10px 14px;
  border-right: none !important;
  background: transparent !important;
}

/* ---------------- 菜单项基础态 ---------------- */
.sidebar-menu .el-menu-item,
.sidebar-menu .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  margin: 2px 0;
  border-radius: var(--r-md);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--side-text);
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.sidebar-menu .el-menu-item .el-icon,
.sidebar-menu .el-sub-menu__title .el-icon {
  width: 18px;
  font-size: 16px;
  margin-right: 8px;
  color: rgba(226, 244, 237, .58);
  transition: color var(--dur) var(--ease), transform var(--dur) var(--ease);
}

.sidebar-menu .el-menu-item:hover,
.sidebar-menu .el-sub-menu__title:hover {
  background: var(--side-hover);
  color: var(--side-text-hover);
  transform: translateX(2px);
}

.sidebar-menu .el-menu-item:hover .el-icon,
.sidebar-menu .el-sub-menu__title:hover .el-icon {
  color: #D3C4FF;
  transform: scale(1.08);
}

/* ---------------- 激活态 ---------------- */
.sidebar-menu .el-menu-item.is-active {
  position: relative;
  color: #fff;
  font-weight: 600;
  background: var(--side-active);
  box-shadow: 0 10px 20px -14px rgba(150, 126, 240, .95),
    inset 0 0 0 1px rgba(255, 255, 255, .1);
  transform: none;
}

.sidebar-menu .el-menu-item.is-active .el-icon {
  color: #F0EBFF;
}

/* 左侧发光小竖条 */
.sidebar-menu .el-menu-item.is-active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  width: 3px;
  height: 20px;
  border-radius: 0 var(--r-pill) var(--r-pill) 0;
  background: var(--side-bar);
  box-shadow: 0 0 10px rgba(201, 183, 255, .95);
  transform: translateY(-50%);
  animation: sidebarBarIn .3s var(--ease) both;
}

@keyframes sidebarBarIn {
  from {
    height: 0;
    opacity: 0;
  }
  to {
    height: 20px;
    opacity: 1;
  }
}

/* ---------------- 分组 ---------------- */
.sidebar-menu .el-sub-menu.is-opened > .el-sub-menu__title {
  color: #fff;
}

.sidebar-menu .el-sub-menu.is-opened > .el-sub-menu__title .el-icon {
  color: #D3C4FF;
}

/* 分支命中当前路由：父标题淡淡高亮 */
.sidebar-menu .el-sub-menu.is-branch-active > .el-sub-menu__title {
  color: #fff;
  background: rgba(255, 255, 255, .05);
}

.sidebar-menu .el-sub-menu .el-menu {
  background: transparent;
  padding: 2px 0 4px;
}

.sidebar-menu .el-sub-menu .el-menu-item {
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  border-radius: var(--r-sm);
}

/* ---------------- 折叠态 ---------------- */
.sidebar-menu.el-menu--collapse {
  padding: 6px 8px 14px;
  width: 100%;
}

.sidebar-menu.el-menu--collapse .el-menu-item,
.sidebar-menu.el-menu--collapse .el-sub-menu__title {
  padding: 0 !important;
  display: flex;
  justify-content: center;
}

.sidebar-menu.el-menu--collapse .el-menu-item .el-icon,
.sidebar-menu.el-menu--collapse .el-sub-menu__title .el-icon {
  margin-right: 0;
}

/* 折叠后弹出的子菜单（teleport 到 body，必须用全局选择器补深色底） */
.el-menu--popup {
  background: #241A4D !important;
  border-radius: var(--r-md);
  padding: 6px !important;
  box-shadow: var(--shadow-3);
}

.el-menu--popup .el-menu-item {
  color: rgba(226, 244, 237, .78) !important;
  border-radius: var(--r-sm);
  height: 38px;
  line-height: 38px;
}

.el-menu--popup .el-menu-item:hover {
  background: rgba(255, 255, 255, .08) !important;
  color: #fff !important;
}

.el-menu--popup .el-menu-item.is-active {
  background: var(--side-active) !important;
  color: #fff !important;
}
</style>
