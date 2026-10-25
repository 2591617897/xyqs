<template>
  <div class="err-page">
    <div class="err-glow" aria-hidden="true" />
    <div class="err-card">
      <div class="err-code">404</div>
      <h1>页面走丢了</h1>
      <p>页面不存在，或菜单的组件路径与前端 views 目录不匹配。</p>
      <div class="err-actions">
        <el-button type="primary" @click="$router.push('/dashboard')">回到首页</el-button>
        <el-button @click="$router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.err-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--bg-page);
  overflow: hidden;
}

.err-glow {
  position: absolute;
  width: 620px;
  height: 620px;
  top: -220px;
  right: -160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 99, 216, .16) 0%, rgba(124, 99, 216, 0) 70%);
  pointer-events: none;
}

.err-card {
  position: relative;
  max-width: 460px;
  padding: 40px 40px 34px;
  text-align: center;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-3);
  animation: cardIn .5s var(--ease) both;
}

.err-code {
  font-size: 68px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
  background: var(--brand-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.err-card h1 {
  margin: 10px 0 8px;
  font-size: 19px;
  font-weight: 700;
  color: var(--text-1);
}

.err-card p {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-3);
}

.err-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.err-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
