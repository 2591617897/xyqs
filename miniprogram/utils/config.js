/**
 * ============ 全局地址配置（只改这里） ============
 * 换服务器只需改 ACTIVE_ENV，或改对应环境的 host / port。
 * 接口地址、上传地址、图片地址都由 SERVER 派生，不需要再改别处。
 *
 * 微信真机 / 上线注意：
 *  - 必须是 HTTPS 且域名已在「微信公众平台 → 开发 → 服务器域名」配置为 request / uploadFile 合法域名
 *  - 本地 http://IP:port 只能在开发者工具勾选「不校验合法域名」时使用
 *  - 校园网/局域网 IP 仅供局域网联调
 */

const ACTIVE_ENV = 'dev'

const ENVS = {
  // 本地开发（开发者工具 + 本机后端）
  dev: {
    scheme: 'http',
    host: '192.168.0.103',
    port: 8888,
    mock: false
  },
  // 局域网联调（真机预览时改成电脑的局域网 IP）
  lan: {
    scheme: 'http',
    host: '192.168.0.103',
    port: 8888,
    mock: false
  },
  // 线上环境（需 HTTPS + 已备案 + 已在后台配合法域名）
  prod: {
    scheme: 'https',
    host: 'api.example.com',
    port: '',
    mock: false
  }
}

const env = ENVS[ACTIVE_ENV] || ENVS.dev
const portPart = env.port ? ':' + env.port : ''
const SERVER = env.scheme + '://' + env.host + portPart

module.exports = {
  ENV: ACTIVE_ENV,
  /** 服务器根地址，如 http://127.0.0.1:8888（图片等静态资源基于它） */
  SERVER: SERVER,
  /** 接口前缀 */
  BASE_URL: SERVER + '/api',
  /** 图片上传地址 */
  UPLOAD_URL: SERVER + '/api/upload',
  /** true 时走本地模拟数据，不发网络请求 */
  USE_MOCK: env.mock,
  /** 请求超时（毫秒） */
  TIMEOUT: 15000,
  /** 仅后端 campus-auth 的 campus.auth.mock=true 时生效（环境变量 AUTH_MOCK） */
  DEV_OPENID: 'dev_openid_guizhou_campus_001'
}
