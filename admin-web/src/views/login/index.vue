<template>
  <div class="login-page">
    <!-- 背景层：呼吸光斑 + 网格 -->
    <div class="bg-blob blob-1" aria-hidden="true" />
    <div class="bg-blob blob-2" aria-hidden="true" />
    <div class="bg-blob blob-3" aria-hidden="true" />
    <div class="bg-grid" aria-hidden="true" />
    <div class="bg-ring ring-1" aria-hidden="true" />
    <div class="bg-ring ring-2" aria-hidden="true" />

    <main class="login-shell" :class="{ shake: shaking }">
      <div class="brand">
        <div class="brand-mark">
          <el-icon :size="19"><School /></el-icon>
        </div>
        <div class="brand-text">
          <strong>{{ APP_TITLE }}</strong>
          <span>小程序后台管理</span>
        </div>
      </div>

      <h1 class="login-title">欢迎回来</h1>
      <p class="login-sub">登录后管理内容、用户与运营数据</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="管理员账号" :prefix-icon="User"
                    autocomplete="username" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="登录密码" :prefix-icon="Lock"
                    show-password autocomplete="current-password" />
        </el-form-item>

        <div class="login-options">
          <el-checkbox v-model="remember">记住账号</el-checkbox>
          <span class="login-tip">忘记密码请联系平台超级管理员</span>
        </div>

        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>

      <div class="login-foot">
        <span class="live-dot" />服务运行正常 · v1.0
      </div>
    </main>

    <footer class="page-foot">© {{ year }} {{ APP_TITLE }} · 贵州校园生活服务平台</footer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, School } from '@element-plus/icons-vue'
import { authApi } from '@/api/system'
import { useUserStore } from '@/stores/user'
import { APP_TITLE } from '@/config/env'

const REMEMBER_KEY = 'admin_remember_user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const shaking = ref(false)
const remember = ref(false)
const year = new Date().getFullYear()

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 记住的账号自动回填，只存账号不存密码
const saved = localStorage.getItem(REMEMBER_KEY)
if (saved) {
  form.username = saved
  remember.value = true
}

function triggerShake () {
  shaking.value = true
  setTimeout(() => { shaking.value = false }, 520)
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    triggerShake()
    return
  }
  loading.value = true
  try {
    await store.login({ username: form.username, password: form.password })
    if (remember.value) localStorage.setItem(REMEMBER_KEY, form.username)
    else localStorage.removeItem(REMEMBER_KEY)
    await store.loadInfo(true)
    await store.loadMenus(true)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect && route.query.redirect !== '/login' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    triggerShake()
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
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 20px 56px;
  overflow: hidden;
  background:
    radial-gradient(1200px 600px at 15% 0%, #3C2A78 0%, transparent 60%),
    radial-gradient(900px 500px at 90% 100%, #30205F 0%, transparent 65%),
    linear-gradient(160deg, #1B1140 0%, #2A1C5C 55%, #170F33 100%);
}

/* ---------------- 背景装饰 ---------------- */
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  pointer-events: none;
  opacity: .55;
}

.blob-1 {
  width: 520px;
  height: 520px;
  top: -180px;
  left: -120px;
  background: radial-gradient(circle, rgba(160, 128, 255, .62), transparent 68%);
  animation: gradientShift 16s ease-in-out infinite;
}

.blob-2 {
  width: 420px;
  height: 420px;
  bottom: -140px;
  right: -80px;
  background: radial-gradient(circle, rgba(120, 150, 255, .32), transparent 70%);
  animation: floatY 13s ease-in-out infinite;
}

.blob-3 {
  width: 300px;
  height: 300px;
  top: 40%;
  right: 24%;
  background: radial-gradient(circle, rgba(216, 190, 255, .22), transparent 70%);
  animation: gradientShift 21s ease-in-out infinite reverse;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, .045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .045) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 42%, #000 0%, transparent 72%);
  -webkit-mask-image: radial-gradient(circle at 50% 42%, #000 0%, transparent 72%);
  pointer-events: none;
}

/* 呼应宣传图的大圆环 */
.bg-ring {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.ring-1 {
  width: 620px;
  height: 620px;
  left: -220px;
  top: 50%;
  margin-top: -310px;
  border: 56px solid rgba(186, 164, 255, .13);
  box-shadow: inset 0 0 60px rgba(186, 164, 255, .14);
  animation: floatY 18s ease-in-out infinite;
}

.ring-2 {
  width: 320px;
  height: 320px;
  right: -110px;
  bottom: 8%;
  border: 22px solid rgba(255, 255, 255, .05);
  animation: floatY 14s ease-in-out infinite reverse;
}

/* ---------------- 卡片 ---------------- */
.login-shell {
  position: relative;
  z-index: 2;
  width: 396px;
  max-width: 100%;
  padding: 30px 30px 22px;
  border-radius: 20px;
  background: rgba(255, 255, 255, .97);
  box-shadow: 0 30px 70px -28px rgba(0, 0, 0, .65), 0 0 0 1px rgba(255, 255, 255, .12),
    inset 0 1px 0 rgba(255, 255, 255, .9);
  animation: loginIn .62s var(--ease) both;
}

.login-shell.shake {
  animation: shake .5s cubic-bezier(.36, .07, .19, .97) both;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #2A1F55;
  background: linear-gradient(135deg, #E4DBFF 0%, #B9A4F2 48%, #8E74E0 100%);
  box-shadow: 0 8px 18px -8px rgba(160, 137, 240, .95), inset 0 1px 0 rgba(255, 255, 255, .6);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text strong {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
}

.brand-text span {
  font-size: 11.5px;
  color: var(--text-3);
}

.login-title {
  margin: 0;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: -.4px;
  color: var(--text-1);
}

.login-sub {
  margin: 5px 0 22px;
  font-size: 12.5px;
  color: var(--text-3);
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: -4px 0 16px;
}

.login-tip {
  font-size: 11.5px;
  color: var(--text-3);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
}

.login-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--line-soft);
  font-size: 11.5px;
  color: var(--text-3);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #C9B7FF;
  animation: pulseRing 2.4s ease-out infinite;
}

.page-foot {
  position: relative;
  z-index: 2;
  margin-top: 22px;
  font-size: 11.5px;
  color: rgba(226, 220, 252, .42);
  letter-spacing: .3px;
}

:deep(.el-checkbox__label) {
  font-size: 12.5px;
  color: var(--text-2);
}

@keyframes loginIn {
  from {
    opacity: 0;
    transform: translateY(18px) scale(.985);
    filter: blur(6px);
  }
  to {
    opacity: 1;
    transform: none;
    filter: none;
  }
}

@keyframes shake {
  10%, 90% { transform: translateX(-2px); }
  20%, 80% { transform: translateX(4px); }
  30%, 50%, 70% { transform: translateX(-7px); }
  40%, 60% { transform: translateX(7px); }
}
</style>
