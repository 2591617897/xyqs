/**
 * 安全基线自检
 * 用法：node scripts/security-baseline-check.js
 *
 * 目的：把「已修好的安全问题」变成会失败的断言，防止后续提交把它们改回去。
 * 这些检查刻意全部基于「读文件 + 正则」，不连数据库、不启服务，因此可以直接进 CI。
 *
 * 覆盖七类回归：
 *   1. 密钥与凭据不得以字面量出现在配置/代码里（JWT / 管理员令牌 / AppSecret / 地图 Key）
 *   2. 敏感字段实体必须挂加密 TypeHandler（否则又变成明文落库）
 *   3. 身份只能来自签名令牌：网关与业务服务的鉴权实现不得信任请求头
 *   4. 网关放行名单不得包含宽泛前缀（/actuator、/api/user）
 *   5. CORS 不得 `*` + 携带凭证
 *   6. 上传必须做文件头校验
 *   7. 后台口令必须 BCrypt、且改密后要吊销令牌
 */
const fs = require('fs')
const path = require('path')
const { execSync } = require('child_process')

const ROOT = path.join(__dirname, '..')
const failures = []
const warnings = []

function fail(rule, detail) {
  failures.push({ rule, detail })
}

function warn(rule, detail) {
  warnings.push({ rule, detail })
}

function read(relPath) {
  const f = path.join(ROOT, relPath)
  return fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : null
}

function exists(relPath) {
  return fs.existsSync(path.join(ROOT, relPath))
}

/**
 * 去掉注释再匹配。
 * 这一步必不可少：本次加固的说明文字大量写在 javadoc 里（比如
 * 「以前是 allowedOriginPatterns("*")，现在改成白名单」），
 * 不剔注释就会把「描述旧漏洞的注释」当成漏洞本身，自检结果全是误报。
 */
function stripComments(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')   // 块注释 / javadoc
    .replace(/^\s*\/\/.*$/gm, '')        // 整行 //
    .replace(/([^:'"])\/\/[^\n]*/g, '$1') // 行尾 //
}

function walk(dir, out = []) {
  const abs = path.join(ROOT, dir)
  if (!fs.existsSync(abs)) return out
  for (const name of fs.readdirSync(abs)) {
    if (name === 'node_modules' || name === 'target' || name === '.git') continue
    const p = path.join(abs, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walk(path.join(dir, name), out)
    else out.push(path.join(dir, name).replace(/\\/g, '/'))
  }
  return out
}

// ---------------------------------------------------------------- 1. 硬编码密钥
const YML_FILES = walk('backend').filter((p) => p.endsWith('application.yml'))
// 这些键的值必须是 ${ENV:...} 形式；写成字面量就等于把凭据提交进仓库
const SECRET_KEYS = [
  'secret', 'appsecret', 'app-secret', 'password', 'sk', 'qqmap-key', 'qqmap-sk',
  'access-key', 'accesskey', 'api-key', 'apikey', 'token'
]
const SECRET_ALLOW = /^(\$\{|#|$|null|true|false|\d+$)/

for (const file of YML_FILES) {
  const text = read(file) || ''
  text.split(/\r?\n/).forEach((line, i) => {
    const m = line.match(/^\s*([A-Za-z0-9_-]+):\s*(.*)$/)
    if (!m) return
    const key = m[1].toLowerCase()
    let value = m[2].split('#')[0].trim().replace(/^["']|["']$/g, '')
    if (!SECRET_KEYS.includes(key)) return
    if (value.length < 6) return
    if (SECRET_ALLOW.test(value)) return
    fail('硬编码密钥', `${file}:${i + 1} ${m[1]} 的值是字面量（应为 \${ENV:} 或留空）`)
  })
}

// Java / JS 里也不许直接写长串密钥
const CODE_FILES = [
  ...walk('backend').filter((p) => p.endsWith('.java')),
  ...walk('deploy').filter((p) => /\.(conf|sh|example)$/.test(p))
]
for (const file of CODE_FILES) {
  const text = read(file) || ''
  // 典型形态：xxx-secret-key-please-change / 32 位以上随机串
  const bad = text.match(/["'][A-Za-z0-9_-]{16,}["']/g) || []
  for (const s of bad) {
    const v = s.slice(1, -1)
    if (!/secret|key|salt|token|pwd|passw/i.test(v)) continue
    if (/campus-(api|miniapp|admin)$/.test(v)) continue
    // 只报「看起来像真密钥」的：含 secret/key 且长度 >= 24
    if (v.length >= 24) fail('硬编码密钥', `${file} 疑似硬编码凭据：${v.slice(0, 12)}...`)
  }
}

// 私钥、密钥文件不得被 git 跟踪
try {
  const tracked = execSync('git ls-files', { cwd: ROOT, encoding: 'utf8' })
  for (const line of tracked.split(/\r?\n/)) {
    if (!line) continue
    if (/\.(pem|key|p12|pfx|jks|keystore)$/i.test(line) && !/env\.example$/.test(line)) {
      fail('凭据入库', `密钥文件被 git 跟踪：${line}`)
    }
    if (/(^|\/)application-local\.ya?ml$/.test(line)) {
      fail('凭据入库', `本地凭据文件被 git 跟踪：${line}（应在 .gitignore 中）`)
    }
    if (/(^|\/)\.env$/.test(line)) {
      fail('凭据入库', `.env 被 git 跟踪：${line}`)
    }
  }
} catch (e) {
  warn('凭据入库', '当前目录不是 git 仓库，跳过跟踪检查')
}

// ---------------------------------------------------------------- 2. 敏感字段加密
const ENCRYPTED_FIELDS = [
  { file: 'backend/campus-user/src/main/java/com/campus/user/entity/User.java',
    fields: ['phone', 'studentNo', 'realName'] },
  { file: 'backend/campus-goods/src/main/java/com/campus/goods/entity/GoodsOrder.java',
    fields: ['contact'] },
  { file: 'backend/campus-admin/src/main/java/com/campus/admin/entity/UserVerifyLog.java',
    fields: ['realName', 'studentNo'] }
]
for (const { file, fields } of ENCRYPTED_FIELDS) {
  const text = read(file)
  if (text === null) {
    warn('敏感字段加密', `文件不存在：${file}`)
    continue
  }
  if (!/@TableName\([^)]*autoResultMap\s*=\s*true/.test(text)) {
    fail('敏感字段加密', `${file} 缺少 @TableName(autoResultMap = true)，加解密会静默失效`)
  }
  for (const f of fields) {
    // 字段声明前必须有 typeHandler = EncryptedStringTypeHandler.class
    const re = new RegExp(`@TableField\\([^)]*EncryptedStringTypeHandler[^)]*\\)\\s*(?:@[^\\n]*\\s*)*private\\s+String\\s+${f}\\b`, 's')
    if (!re.test(text)) {
      fail('敏感字段加密', `${file} 的 ${f} 未挂 EncryptedStringTypeHandler`)
    }
  }
}

// ---------------------------------------------------------------- 3. 身份只来自令牌
const SEC_AUTOCONFIG_RAW = read('backend/campus-common/src/main/java/com/campus/common/security/SecurityAutoConfig.java')
const SEC_AUTOCONFIG = SEC_AUTOCONFIG_RAW === null ? null : stripComments(SEC_AUTOCONFIG_RAW)
if (SEC_AUTOCONFIG === null) {
  fail('身份来源', 'SecurityAutoConfig.java 不存在')
} else {
  if (!/<artifactId>jjwt|<groupId>io\.jsonwebtoken/.test(read('backend/campus-common/pom.xml') || '')) {
    warn('身份来源', 'common 模块未声明 jjwt 依赖')
  }
  // 允许「读 header 但没有令牌就拒绝」的实现，禁止「无条件信任 header」
  if (/HEADER_USER_ID\s*\(\s*request\s*\)|getHeader\(HEADER_USER_ID\)/.test(SEC_AUTOCONFIG)
      && !/BizException\s*\(\s*401|unauthorized/.test(SEC_AUTOCONFIG)) {
    fail('身份来源', 'SecurityAutoConfig 读取了 X-User-Id 却没有拒绝逻辑')
  }
  if (!/jwtService\.parse/.test(SEC_AUTOCONFIG)) {
    fail('身份来源', 'SecurityAutoConfig 未从 JWT 取身份')
  }
}
const INTERCEPTOR = stripComments(
  read('backend/campus-admin/src/main/java/com/campus/admin/config/AdminAuthInterceptor.java') || '')
if (!/parseAdminToken|parse\(/.test(INTERCEPTOR)) {
  fail('身份来源', 'AdminAuthInterceptor 未校验管理员令牌')
}
if (!/token_version|tokenVersion/.test(INTERCEPTOR)) {
  fail('令牌吊销', 'AdminAuthInterceptor 未比对 token_version，改密后旧令牌仍可用')
}

// 后台改密必须吊销令牌
const AUTH_ADMIN = stripComments(
  read('backend/campus-admin/src/main/java/com/campus/admin/service/AuthAdminService.java') || '')
if (!/bumpTokenVersion|revokeTokens/.test(AUTH_ADMIN)) {
  fail('令牌吊销', 'AuthAdminService.changePassword 未吊销旧令牌')
}

// ---------------------------------------------------------------- 4. 网关放行名单
const GW = stripComments(
  read('backend/campus-gateway/src/main/java/com/campus/gateway/filter/AuthGlobalFilter.java') || '')
if (!GW) {
  fail('网关放行', 'AuthGlobalFilter.java 不存在')
} else {
  // 只看 WHITE_PREFIX 这个真正决定放行的列表，注释与常量定义不算
  const m = GW.match(/WHITE_PREFIX\s*=\s*List\.of\(([\s\S]*?)\);/)
  const whitelist = m ? m[1] : ''
  if (!whitelist) {
    fail('网关放行', '未找到 WHITE_PREFIX 定义，无法确认放行范围')
  }
  for (const bad of ['"/actuator"', '"/api/user"', '"/api/admin"']) {
    if (whitelist.includes(bad)) fail('网关放行', `WHITE_PREFIX 含宽泛前缀 ${bad}，应为精确路径`)
  }
  // 健康检查必须是常量 + 精确 equals，不能留在前缀列表里
  if (!/ACTUATOR_HEALTH_EXACT|\/actuator\/health/.test(GW)) {
    warn('网关放行', '未找到 /actuator/health 的精确放行（健康检查会 401）')
  }
  if (/WHITE_PREFIX[\s\S]{0,300}ADMIN_PREFIX\s*,/.test(GW)) {
    fail('网关放行', '后台前缀被加进了公开放行名单')
  }
  if (!/requireIssuer|requireAudience|require\(/.test(GW)) {
    warn('令牌校验', '网关未校验 iss/aud')
  }
}

// ---------------------------------------------------------------- 5. CORS
const GATEWAY_APP = stripComments(
  read('backend/campus-gateway/src/main/java/com/campus/gateway/GatewayApplication.java') || '')
const wildcardOrigin = /allowedOriginPatterns?\s*\(\s*"\*"\s*\)/.test(GATEWAY_APP)
  || /setAllowedOrigins\s*\(\s*List\.of\(\s*"\*"\s*\)\s*\)/.test(GATEWAY_APP)
if (wildcardOrigin && !/setAllowCredentials\s*\(\s*false\s*\)/.test(GATEWAY_APP)) {
  fail('CORS', '网关 CORS 允许任意来源且携带凭证')
}
if (!/CORS_ALLOWED_ORIGINS|allowedOrigins/.test(GATEWAY_APP)) {
  warn('CORS', '网关未按白名单配置来源')
}

// ---------------------------------------------------------------- 6. 上传校验
const UPLOADS = walk('backend').filter((p) => /controller[\\/]UploadController\.java$/.test(p))
if (UPLOADS.length === 0) warn('上传', '未找到 UploadController')
for (const f of UPLOADS) {
  const text = read(f) || ''
  if (!/FileMagic\./.test(text)) fail('上传', `${f} 未做文件头（magic bytes)校验`)
}

// ---------------------------------------------------------------- 7. 后台口令
const PWD_UTIL = read('backend/campus-admin/src/main/java/com/campus/admin/config/AdminPasswordUtil.java') || ''
if (!/BCrypt/.test(PWD_UTIL)) {
  fail('口令哈希', 'AdminPasswordUtil 未使用 BCrypt')
} else {
  if (/md5Hex/.test(PWD_UTIL)) {
    warn('口令哈希', 'AdminPasswordUtil 仍保留 md5Hex（应仅用于兼容旧哈希校验后升级）')
  }
  if (!/needsUpgrade/.test(PWD_UTIL)) {
    warn('口令哈希', 'AdminPasswordUtil 缺少 needsUpgrade，旧 MD5 哈希无法平滑升级')
  }
}
if (!/checkStrength/.test(PWD_UTIL)) {
  fail('口令策略', 'AdminPasswordUtil 缺少口令强度校验')
}

// ---------------------------------------------------------------- 8. 其他基线
const NGINX = read('deploy/nginx-campus-api.trkcwl.com.conf') || ''
if (!/Strict-Transport-Security/.test(NGINX)) warn('传输安全', 'Nginx 未配置 HSTS')
if (!/ssl_protocols\s+TLSv1\.2/.test(NGINX)) warn('传输安全', 'Nginx 未限制 TLS 版本')

const GITIGNORE = read('.gitignore') || ''
for (const pattern of ['application-local', '.env']) {
  if (!GITIGNORE.includes(pattern)) warn('凭据入库', `.gitignore 未忽略 ${pattern}`)
}
if (!exists('deploy/env.example')) warn('配置管理', '缺少 deploy/env.example 环境变量模板')

const BACKUP = read('scripts/backup-db.sh') || ''
if (!/openssl enc|gpg/.test(BACKUP)) warn('备份', '备份脚本未加密')

// ---------------------------------------------------------------- 9. 微信学生认证
// mock 一旦在默认配置里是 true，任何人都能一键变成「已认证学生」，
// 属于最容易被临时改完忘记改回来的开关，所以把它钉成会失败的断言。
const AUTH_YML = read('backend/campus-auth/src/main/resources/application.yml') || ''
const studentMock = AUTH_YML.match(/WX_STUDENT_VERIFY_MOCK:([^}\s]*)/)
if (studentMock && studentMock[1].trim() === 'true') {
  fail('学生认证', 'WX_STUDENT_VERIFY_MOCK 默认值不能是 true（生产会人人认证通过）')
} else if (!studentMock) {
  warn('学生认证', 'application.yml 缺少 student-verify 配置段')
}

// 前端开关与插件声明必须同步：只开开关不声明插件，用户点了「开始微信认证」不会有任何反应
const MP_CFG = read('miniprogram/utils/config.js') || ''
const APP_JSON_TXT = read('miniprogram/app.json') || ''
const wxStudentOn = /WX_STUDENT_VERIFY:\s*true/.test(MP_CFG)
const pluginDeclared = /"studentVerify"\s*:/.test(APP_JSON_TXT) && /wxa16657d57059e0f0/.test(APP_JSON_TXT)
if (wxStudentOn && !pluginDeclared) {
  warn('学生认证', '已开启 WX_STUDENT_VERIFY 但 app.json 未声明 studentVerify 插件，按钮会无响应')
}
if (!wxStudentOn && pluginDeclared) {
  warn('学生认证', 'app.json 声明了插件但前端开关是 false，多了一处无效声明')
}

// ---------------------------------------------------------------- 输出
const okCount = failures.length === 0
console.log('')
console.log('安全基线自检')
console.log('─'.repeat(60))
for (const w of warnings) console.log('  ⚠ ' + w.rule + '：' + w.detail)
for (const f of failures) console.log('  ✗ ' + f.rule + '：' + f.detail)
console.log('─'.repeat(60))
console.log(`失败 ${failures.length} 项，告警 ${warnings.length} 项，检查文件 ${YML_FILES.length + CODE_FILES.length} 个`)
console.log(okCount ? '通过：安全基线未被回退' : '未通过：请修复上面标记为 ✗ 的项')
process.exit(okCount ? 0 : 1)
