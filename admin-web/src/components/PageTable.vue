<template>
  <div class="page-table">
    <slot name="toolbar" />
    <div class="table-wrap">
      <el-table v-loading="loading" :data="data" :row-key="rowKey" border stripe size="default">
        <slot />
        <template #empty>
          <div class="table-empty">
            <span class="empty-badge">
              <el-icon :size="20"><Files /></el-icon>
            </span>
            <p class="empty-title">暂无数据</p>
            <p class="empty-sub">调整筛选条件后再试一次</p>
          </div>
        </template>
      </el-table>
    </div>
    <div class="page-table-pager">
      <el-pagination
        v-model:current-page="innerPage"
        v-model:page-size="innerSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalCount"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="onChange"
        @current-change="onChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Files } from '@element-plus/icons-vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  // 后端分页的 total 是字符串（Long 转字符），这里必须容错：
  // Element 的分页用 typeof v === 'number' 判断是否「缺参」，
  // 传字符串会被判成未提供 total，整个分页组件静默不渲染。
  total: { type: [Number, String], default: 0 },
  page: { type: [Number, String], default: 1 },
  size: { type: [Number, String], default: 10 },
  loading: { type: Boolean, default: false },
  rowKey: { type: String, default: 'id' }
})
const emit = defineEmits(['load', 'update:page', 'update:size'])

const totalCount = computed(() => Number(props.total) || 0)

const innerPage = ref(Number(props.page) || 1)
const innerSize = ref(Number(props.size) || 10)

watch(() => props.page, (v) => { innerPage.value = Number(v) || 1 })
watch(() => props.size, (v) => { innerSize.value = Number(v) || 10 })

function onChange () {
  emit('update:page', Number(innerPage.value) || 1)
  emit('update:size', Number(innerSize.value) || 10)
  emit('load')
}
</script>

<style scoped>
.table-wrap {
  border-radius: var(--r-md);
  overflow: hidden;
}

.page-table-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--line-soft);
}

.table-empty {
  padding: 34px 0 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.empty-badge {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  margin-bottom: 6px;
  border-radius: 15px;
  color: var(--brand-500);
  background: var(--brand-50);
  box-shadow: inset 0 0 0 1px var(--brand-100);
  animation: popIn .45s var(--ease) both;
}

.empty-title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-2);
}

.empty-sub {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}
</style>
