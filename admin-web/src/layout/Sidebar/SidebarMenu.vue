<template>
  <el-menu :default-active="activePath" :collapse="collapsed" :collapse-transition="false"
           background-color="#001529" text-color="#c0c4cc" active-text-color="#10b981" router unique-opened>
    <template v-for="menu in menus" :key="menu.id">
      <el-sub-menu v-if="menu.children && menu.children.length" :index="String(menu.id)">
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
</script>

<style scoped>
.el-menu {
  border-right: none;
}
</style>
