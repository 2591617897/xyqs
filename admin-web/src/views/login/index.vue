<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <h2 class="login-title">{{ APP_TITLE }}</h2>
      <p class="login-sub">小程序后台管理</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock"
                    show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <p class="login-tip">忘记密码请联系平台超级管理员</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api/system'
import { useUserStore } from '@/stores/user'
import { APP_TITLE } from '@/config/env'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await store.login({ username: form.username, password: form.password })
    await store.loadInfo(true)
    await store.loadMenus(true)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect && route.query.redirect !== '/login' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    // 错误提示已经在请求拦截器里统一弹了
  } finally {
    loading.value = false
  }
}

// 登录接口不需要令牌，进来先清一次残留登录态，避免顶着过期 token 发请求
store.reset()
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f766e 0%, #10b981 55%, #34d399 100%);
}
.login-card {
  width: 380px;
  border-radius: 12px;
  padding: 12px 8px 4px;
}
.login-title {
  margin: 0;
  text-align: center;
  font-size: 22px;
  color: #0f172a;
}
.login-sub {
  margin: 6px 0 22px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
}
.login-btn {
  width: 100%;
}
.login-tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
