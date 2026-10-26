/**
 * 本地开发用 JWT 生成（跳过微信登录，方便脚本/调试直连接口）
 * 用法：node scripts/make-dev-token.js <userId> [schoolId] [verifyStatus]
 * 示例：node scripts/make-dev-token.js 2114018677295706113 1001 2
 *
 * 密钥来源优先级：环境变量 JWT_SECRET → ~/.campus-secrets/jwt.key（与各服务同一份）。
 * 只用于本地联调，**不要**把它生成的令牌带到生产环境。
 */
const fs = require('fs')
const os = require('os')
const path = require('path')
const crypto = require('crypto')

const ISSUER = 'campus-api'
const AUDIENCE = 'campus-miniapp'

function b64url(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function secret() {
  if (process.env.JWT_SECRET && process.env.JWT_SECRET.trim()) {
    return process.env.JWT_SECRET.trim()
  }
  const file = path.join(os.homedir(), '.campus-secrets', 'jwt.key')
  if (!fs.existsSync(file)) {
    throw new Error('找不到密钥：请设置 JWT_SECRET 或确认 ~/.campus-secrets/jwt.key 存在')
  }
  return fs.readFileSync(file, 'utf8').trim()
}

const userId = process.argv[2]
if (!userId) {
  console.error('用法：node scripts/make-dev-token.js <userId> [schoolId] [verifyStatus]')
  process.exit(1)
}
const schoolId = process.argv[3] ? Number(process.argv[3]) : null
const verifyStatus = process.argv[4] ? Number(process.argv[4]) : 2

const now = Math.floor(Date.now() / 1000)
const payload = {
  sub: String(userId),
  iss: ISSUER,
  aud: AUDIENCE,
  iat: now,
  exp: now + 7 * 86400
}
if (schoolId) payload.schoolId = schoolId
if (verifyStatus !== null) payload.vs = verifyStatus

const input =
  b64url(Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))) +
  '.' +
  b64url(Buffer.from(JSON.stringify(payload)))
const sig = crypto.createHmac('sha256', secret()).update(input).digest()

console.log(input + '.' + b64url(sig))
