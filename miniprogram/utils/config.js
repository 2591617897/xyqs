// 全局配置
module.exports = {
  // true：走本地模拟数据（不需要后端，适合纯前端演示）
  // false：走真实 Spring Boot 网关（默认）
  USE_MOCK: false,

  // 网关地址。本地开发需在开发者工具勾选「不校验合法域名」
  BASE_URL: 'http://127.0.0.1:8888/api',

  // 请求超时（毫秒）
  TIMEOUT: 15000,

  // 仅当 USE_MOCK = false 且后端 campus-auth 开启 mock 时生效
  DEV_OPENID: 'dev_openid_guizhou_campus_001'
}
