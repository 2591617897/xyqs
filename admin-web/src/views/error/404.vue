<template>
  <div class="page-card">
    <el-result icon="info" title="404" sub-title="页面不存在，或菜单的组件路径与前端 views 目录不匹配">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">回到首页</el-button>
      </template>
    </el-result>
  </div>
</template>
