<template>
  <el-form :inline="true" :model="model" class="search-form" @submit.prevent="$emit('search')">
    <slot />
    <el-form-item class="search-actions">
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
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0 4px;
  margin-bottom: -6px;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 12px;
  margin-right: 12px;
}

.search-form :deep(.el-form-item__label) {
  font-size: 13px;
  padding-right: 8px;
}

.search-form :deep(.el-input__wrapper),
.search-form :deep(.el-select__wrapper) {
  background: var(--bg-subtle);
}

.search-form :deep(.el-input__wrapper:hover),
.search-form :deep(.el-select__wrapper:hover) {
  background: #fff;
}

.search-form :deep(.el-input__wrapper.is-focus),
.search-form :deep(.el-select__wrapper.is-focused) {
  background: #fff;
}

.search-actions {
  margin-left: auto;
}
</style>
