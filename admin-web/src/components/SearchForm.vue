<template>
  <el-form :inline="true" :model="model" class="search-form" @submit.prevent="$emit('search')">
    <slot />
    <el-form-item>
      <el-button type="primary" :icon="Search" @click="$emit('search')">查询</el-button>
      <el-button :icon="Refresh" @click="$emit('reset')">重置</el-button>
      <slot name="extra" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { Search, Refresh } from '@element-plus/icons-vue'

defineProps({
  model: { type: Object, required: true }
})
defineEmits(['search', 'reset'])
</script>

<style scoped>
.search-form {
  background: #fff;
  padding: 16px 16px 0;
  border-radius: 6px;
  margin-bottom: 12px;
}
</style>
