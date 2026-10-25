<template>
  <div class="page-table">
    <slot name="toolbar" />
    <el-table v-loading="loading" :data="data" :row-key="rowKey" border stripe size="default">
      <slot />
      <template #empty>
        <el-empty description="暂无数据" :image-size="80" />
      </template>
    </el-table>
    <div class="page-table-pager">
      <el-pagination
        v-model:current-page="innerPage"
        v-model:page-size="innerSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="onChange"
        @current-change="onChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  loading: { type: Boolean, default: false },
  rowKey: { type: String, default: 'id' }
})
const emit = defineEmits(['load', 'update:page', 'update:size'])

const innerPage = ref(props.page)
const innerSize = ref(props.size)

watch(() => props.page, (v) => { innerPage.value = v })
watch(() => props.size, (v) => { innerSize.value = v })

function onChange () {
  emit('update:page', innerPage.value)
  emit('update:size', innerSize.value)
  emit('load')
}
</script>

<style scoped>
.page-table-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
