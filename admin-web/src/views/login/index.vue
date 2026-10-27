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
          <a class="login-link" href="javascript:void(0)" @click="openFind">忘记密码？</a>
        </div>

        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>

      <div class="login-foot">
        <span class="live-dot" />服务运行正常 · v1.0
      </div>
    </main>

    <!-- 找回密码：验证码只发到该账号已绑定的邮箱，前端不能指定收件人 -->
    <el-dialog v-model="findVisible" title="找回登录密码" width="404px" align-center
               class="find-dialog" :close-on-click-modal="false" @closed="onFindClosed">
      <div class="find-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>验证码会发送到该账号<b>已绑定的邮箱</b>。账号没绑邮箱请找超级管理员重置。</span>
      </div>

      <el-form ref="findRef" :model="find" :rules="findRules" label-position="top" size="large">
        <el-form-item label="管理员账号" prop="username">
          <el-input v-model="find.username" placeholder="请输入管理员账号" :disabled="codeSent" clearable />
        </el-form-item>

        <template v-if="codeSent">
          <el-alert type="success" :closable="false" show-icon class="find-sent"
                    :title="'验证码已发送至 ' + sentEmail + '，' + expireMinutes + ' 分钟内有效'" />
          <el-form-item label="邮箱验证码" prop="code">
            <el-input v-model="find.code" maxlength="6" placeholder="请输入 6 位数字验证码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="find.newPassword" type="password" show-password
                      placeholder="至少 8 位，含大小写/数字/符号中的三类" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm">
            <el-input v-model="find.confirm" type="password" show-password placeholder="再输入一次新密码" />
          </el-form-item>
          <div class="find-resend">
            <el-button link type="primary" :disabled="countdown > 0" :loading="sending" @click="sendCode">
              {{ countdown > 0 ? countdown + ' 秒后可重新发送' : '没收到？重新发送' }}
            </el-button>
          </div>
        </template>
      </el-form>

      <template #footer>
        <template v-if="!codeSent">
          <el-button @click="findVisible = false">取 消</el-button>
          <el-button type="primary" :loading="sending" @click="sendCode">发送验证码</el-button>
        </template>
        <template v-else>
          <el-button @click="backToAccount">修改账号</el-button>
          <el-button type="primary" :loading="resetting" @click="doReset">重置密码</el-button>
        </template>
      </template>
    </el-dialog>

    <footer class="page-foot">© {{ year }} {{ APP_TITLE }} · 贵州校园生活服务平台</footer>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, School, InfoFilled } from '@element-plus/icons-vue'
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

/* ---------------- 找回密码 ---------------- */
const findVisible = ref(false)
const findRef = ref()
const sending = ref(false)
const resetting = ref(false)
const codeSent = ref(false)
const sentEmail = ref('')
const expireMinutes = ref(10)
const countdown = ref(0)
let timer = null

const find = reactive({ username: '', code: '', newPassword: '', confirm: '' })

/**
 * 新密码强度在这里做的是「提前告知」，最终判定仍在服务端
 * （AdminPasswordUtil#checkStrength，还多一条弱口令黑名单）。
 * 两边口径别各写一套文案，前端只做长度/三类字符/含账号名这三条能即时算出来的。
 */
function checkNewPassword (rule, value, cb) {
  if (!value) return cb(new Error('请输入新密码'))
  if (value.length < 8) return cb(new Error('密码长度不能少于 8 位'))
  if (value.length > 64) return cb(new Error('密码长度不能超过 64 位'))
  if (find.username && value.toLowerCase().includes(find.username.trim().toLowerCase())) {
    return cb(new Error('密码不能包含账号名'))
  }
  let kinds = 0
  if (/[a-z]/.test(value)) kinds++
  if (/[A-Z]/.test(value)) kinds++
  if (/\d/.test(value)) kinds++
  if (/[^a-zA-Z0-9]/.test(value)) kinds++
  if (kinds < 3) return cb(new Error('密码需包含大写字母、小写字母、数字、符号中的至少三类'))
  cb()
}

function checkConfirm (rule, value, cb) {
  if (!value) return cb(new Error('请再输入一次新密码'))
  if (value !== find.newPassword) return cb(new Error('两次输入的密码不一致'))
  cb()
}

const findRules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [{ validator: checkNewPassword, trigger: 'blur' }],
  confirm: [{ validator: checkConfirm, trigger: 'blur' }]
}

function openFind () {
  // 登录框里已经填了账号就直接带过来，少打一次
  find.username = form.username || ''
  findVisible.value = true
}

function startCountdown (sec) {
  countdown.value = sec
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode () {
  const ok = await findRef.value.validateField('username').then(() => true).catch(() => false)
  if (!ok) return
  sending.value = true
  try {
    const res = await authApi.sendResetCode(find.username.trim()) || {}
    sentEmail.value = res.maskedEmail || '邮箱'
    expireMinutes.value = res.expireMinutes || 10
    codeSent.value = true
    startCountdown(res.resendSeconds || 60)
  } catch (e) {
    // 具体原因（账号不存在 / 未绑邮箱 / 发送过频）已由请求拦截器弹出
  } finally {
    sending.value = false
  }
}

function backToAccount () {
  // 换账号后原验证码就作废了（服务端按账号校验），把已填的码清掉免得误导
  codeSent.value = false
  find.code = ''
  find.newPassword = ''
  find.confirm = ''
  findRef.value && findRef.value.clearValidate()
}

async function doReset () {
  const ok = await findRef.value.validate().catch(() => false)
  if (!ok) return
  resetting.value = true
  try {
    await authApi.resetPassword({
      username: find.username.trim(),
      code: find.code.trim(),
      newPassword: find.newPassword
    })
    ElMessage.success('密码已重置，请用新密码登录')
    form.username = find.username.trim()
    form.password = ''
    findVisible.value = false
  } catch (e) {
    // 同上，拦截器已弹提示
  } finally {
    resetting.value = false
  }
}

function onFindClosed () {
  clearInterval(timer)
  countdown.value = 0
  codeSent.value = false
  sentEmail.value = ''
  Object.assign(find, { username: '', code: '', newPassword: '', confirm: '' })
  findRef.value && findRef.value.clearValidate()
}

onBeforeUnmount(() => clearInterval(timer))

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

.login-link {
  font-size: 11.5px;
  color: #B9A4F2;
  text-decoration: none;
  transition: color .18s;
}

.login-link:hover {
  color: #D8CBFF;
  text-decoration: underline;
}

/* ---------------- 找回密码弹窗 ---------------- */
.find-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #F4F1FE;
  color: #5B4B8A;
  font-size: 12.5px;
  line-height: 1.6;
}

.find-tip .el-icon {
  flex: none;
  margin-top: 2px;
}

.find-tip b {
  color: #4A3A7A;
}

.find-sent {
  margin-bottom: 14px;
}

.find-sent :deep(.el-alert__title) {
  font-size: 12.5px;
  line-height: 1.5;
}

.find-resend {
  margin: -8px 0 2px;
  text-align: right;
}

.find-resend :deep(.el-button) {
  font-size: 12.5px;
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
