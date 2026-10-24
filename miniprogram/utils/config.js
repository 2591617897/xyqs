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

const ACTIVE_ENV = 'lan'

const ENVS = {
  // 本地开发（开发者工具 + 本机后端）
  // mock: true 时全站走本地模拟数据（utils/mock.js），无需启动后端
  // 后端已实现完整（含私信），默认 mock: false 走真实接口；
  // 只想看界面效果、不想起后端时把它改回 true 即可
  dev: {
    scheme: 'http',
    host: '192.168.0.102',
    port: 8888,
    mock: false
  },
  // 局域网联调（真机预览时改成电脑的局域网 IP）
  // ⚠️ 注意：微信真机对 `<image>` 的 **http** 图片会静默失败（只显示灰块，不触发 binderror），
  // 而 wx.previewImage 走的是另一条下载通道，所以会出现「点开预览能看、列表里是灰块」。
  // 局域网 http 只能满足开发者工具；真机看图必须走下面的 https 环境。
  lan: {
    scheme: 'http',
    host: '192.168.0.102',
    port: 8888,
    mock: false
  },
  // 真机联调（HTTPS）：用内网穿透（cpolar / frp / ngrok）或正式域名拿到 https 地址后填这里，
  // 并把 ACTIVE_ENV 改成 'tunnel'。接口 / 上传 / 图片地址全部由 SERVER 派生，改一处即可全站生效。
  tunnel: {
    scheme: 'https',
    host: '', // 例如 xxxx.cpolar.cn
    port: '',
    mock: false
  },
  // 线上环境（需 HTTPS + 已备案 + 已在后台配 downloadFile / request 合法域名）
  prod: {
    scheme: 'https',
    host: '', // 正式域名
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
  /**
   * 本地单人演示开关（默认 false）。
   *
   * true：登录时把下面的固定 DEV_OPENID 发给后端，配合后端 AUTH_MOCK=true 使用，
   *       好处是开发者工具里能直接落到种子账号（林小满），看到演示数据。
   *       代价是**所有微信账号都会登录成同一个人**。
   * false：不带 devOpenid，后端走真实 code2Session，每个微信得到各自独立的账号。
   *
   * 真机 / 多账号测试必须保持 false。
   */
  DEV_LOGIN: false,
  /** 仅 DEV_LOGIN=true 时随登录请求发送 */
  DEV_OPENID: 'dev_openid_guizhou_campus_001'
}
