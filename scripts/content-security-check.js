/**
 * 内容安全接线校验（静态检查，不需要启动服务）
 * 用法：node scripts/content-security-check.js
 *
 * 背景：内容安全是「少一处就白做」的合规项——审核看的是「任意发布场景都生效」。
 * 以后再加发布入口（比如新频道、新评论类型），最容易漏的就是忘了调 SecurityGuard。
 * 这个脚本把「哪些写入口必须过审」写成断言，防止后续改动悄悄漏掉一条链路。
 *
 * 检查项：
 *   1. 所有 UGC 写入口（发帖/表白墙/闲置/三类评论/私信/昵称）都调用了 SecurityGuard
 *   2. 带图片的入口额外调用了 checkImages
 *   3. campus-auth 侧真的实现了 msg_sec_check 与 media_check_async
 *   4. 图片审核有台账表、回调验签、网关白名单与路由
 *   5. 配置项齐全（开关、图片开关、回调 Token）
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const BACKEND = path.join(ROOT, 'backend')

let passed = 0
const failures = []

function ok(msg) {
  passed++
  console.log('  ok   ' + msg)
}

function fail(msg) {
  failures.push(msg)
  console.log('  x    ' + msg)
}

function check(cond, msg) {
  cond ? ok(msg) : fail(msg)
}

function p(rel) {
  return path.join(BACKEND, rel)
}

function read(rel) {
  const f = p(rel)
  if (!fs.existsSync(f)) {
    fail('缺少文件：' + rel)
    return ''
  }
  return fs.readFileSync(f, 'utf8')
}

/**
 * 从 Java 源码里截出某个方法体（靠大括号配平，够用且不引入解析器）
 */
function methodBody(src, name) {
  const re = new RegExp('(public|private|protected)[^\\n]*\\b' + name + '\\s*\\(', 'g')
  const m = re.exec(src)
  if (!m) return null
  let i = src.indexOf('{', m.index)
  if (i < 0) return null
  let depth = 0
  for (let j = i; j < src.length; j++) {
    if (src[j] === '{') depth++
    else if (src[j] === '}') {
      depth--
      if (depth === 0) return src.slice(i, j + 1)
    }
  }
  return null
}

// ---------------------------------------------------------------- 1. 写入口覆盖
console.log('\n[1] UGC 写入口是否都过内容安全')

const ENTRIES = [
  {
    name: '帖子发布',
    file: 'campus-post/src/main/java/com/campus/post/service/PostService.java',
    method: 'publish',
    need: ['checkText', 'checkImages', 'BIZ_POST']
  },
  {
    name: '帖子编辑',
    file: 'campus-post/src/main/java/com/campus/post/service/PostService.java',
    method: 'update',
    need: ['checkText', 'checkImages', 'BIZ_POST']
  },
  {
    name: '帖子评论',
    file: 'campus-post/src/main/java/com/campus/post/service/PostService.java',
    method: 'addComment',
    need: ['checkText', 'checkImages', 'BIZ_POST_COMMENT']
  },
  {
    name: '表白墙发布（匿名）',
    file: 'campus-wall/src/main/java/com/campus/wall/service/WallService.java',
    method: 'publish',
    need: ['checkText', 'checkImages', 'BIZ_WALL']
  },
  {
    name: '表白墙编辑',
    file: 'campus-wall/src/main/java/com/campus/wall/service/WallService.java',
    method: 'update',
    need: ['checkText', 'checkImages', 'BIZ_WALL']
  },
  {
    name: '表白墙评论',
    file: 'campus-wall/src/main/java/com/campus/wall/service/WallService.java',
    method: 'addComment',
    need: ['checkText', 'checkImages', 'BIZ_WALL_COMMENT']
  },
  {
    name: '闲置发布',
    file: 'campus-goods/src/main/java/com/campus/goods/service/GoodsService.java',
    method: 'publish',
    need: ['checkText', 'checkImages', 'BIZ_GOODS']
  },
  {
    name: '闲置编辑',
    file: 'campus-goods/src/main/java/com/campus/goods/service/GoodsService.java',
    method: 'update',
    need: ['checkText', 'checkImages', 'BIZ_GOODS']
  },
  {
    name: '闲置评论',
    file: 'campus-goods/src/main/java/com/campus/goods/service/GoodsService.java',
    method: 'addComment',
    need: ['checkText', 'checkImages', 'BIZ_GOODS_COMMENT']
  },
  {
    name: '私信发送',
    file: 'campus-message/src/main/java/com/campus/message/service/MessageService.java',
    method: 'send',
    need: ['checkText', 'checkImages', 'BIZ_MESSAGE']
  },
  {
    name: '群消息发送',
    file: 'campus-message/src/main/java/com/campus/message/service/GroupService.java',
    method: 'send',
    need: ['checkText', 'checkImages', 'BIZ_GROUP_MSG']
  }
]

ENTRIES.forEach(function (e) {
  const src = read(e.file)
  if (!src) return
  const body = methodBody(src, e.method)
  if (!body) {
    fail(e.name + '：找不到方法 ' + e.method + '（方法被重命名了？）')
    return
  }
  const missing = e.need.filter(function (k) {
    return body.indexOf(k) < 0
  })
  check(missing.length === 0,
    e.name + ' → ' + e.method + '() 过审' + (missing.length ? '（缺 ' + missing.join('/') + '）' : ''))
})

// 昵称是「资料」场景，只需要文本审核
;(function () {
  const src = read('campus-user/src/main/java/com/campus/user/service/UserService.java')
  if (!src) return
  check(/checkText\([\s\S]{0,200}?SCENE_PROFILE[\s\S]{0,120}?BIZ_NICKNAME/.test(src),
    '昵称修改 → SCENE_PROFILE + BIZ_NICKNAME')
})()

// ---------------------------------------------------------------- 2. 守卫与客户端
console.log('\n[2] 公共守卫层')

const guard = read('campus-common/src/main/java/com/campus/common/client/SecurityGuard.java')
check(/public void checkText\(Long userId, String text, int scene, String bizType\)/.test(guard),
  'SecurityGuard.checkText 支持 bizType')
check(/public void checkImages\(/.test(guard), 'SecurityGuard.checkImages 存在')
check(/https?:\/\//.test(guard), 'checkImages 过滤非 http(s) 地址（本地临时路径不送审）')
check(/catch \(Exception e\)[\s\S]{0,200}放行|跳过/.test(guard), 'fail-open：微信侧异常不影响发布')

const client = read('campus-common/src/main/java/com/campus/common/client/SecurityClient.java')
check(/msg-check/.test(client) && /media-check/.test(client), 'SecurityClient 暴露 msg-check 与 media-check')

// ---------------------------------------------------------------- 3. auth 侧实现
console.log('\n[3] campus-auth 内容安全实现')

const svc = read('campus-auth/src/main/java/com/campus/auth/service/WxSecurityService.java')
check(/wxa\/msg_sec_check/.test(svc), '调用 msg_sec_check（文本）')
check(/wxa\/media_check_async/.test(svc), '调用 media_check_async（图片）')
check(/public int submitMediaCheck/.test(svc), 'submitMediaCheck 提交图片送审')
check(/public void handleCallback/.test(svc), 'handleCallback 处理审核结果回调')
check(/public boolean verifySignature/.test(svc), 'verifySignature 回调验签')
check(/wxa_media_check/.test(svc), '识别 Event=wxa_media_check')
check(/recordViolation/.test(svc), '违规事件写 t_content_audit')
check(/ST_RISKY|ST_REVIEW/.test(svc), '区分 risky / review 两种结果')

const mediaEntity = read('campus-auth/src/main/java/com/campus/auth/entity/MediaCheck.java')
check(/t_media_check/.test(mediaEntity), 'MediaCheck 映射 t_media_check')
check(!!read('campus-auth/src/main/java/com/campus/auth/mapper/MediaCheckMapper.java'),
  'MediaCheckMapper 存在（按 trace_id 反查）')
check(!!read('campus-auth/src/main/java/com/campus/auth/controller/WxOpenController.java'),
  'WxOpenController 提供公网回调入口')

// ---------------------------------------------------------------- 4. 回调链路
console.log('\n[4] 回调链路（微信服务器 → 网关 → auth）')

const gw = read('campus-gateway/src/main/resources/application.yml')
check(/Path=\/api\/wx\/\*\*/.test(gw), '网关有 /api/wx/** 路由')
check(/campus-wx/.test(gw), '路由 id = campus-wx 指向 campus-auth')

const filter = read('campus-gateway/src/main/java/com/campus/gateway/filter/AuthGlobalFilter.java')
check(/WX_CALLBACK_PREFIX/.test(filter) && /callback/.test(filter),
  'AuthGlobalFilter 精确放行 /api/wx/*/callback（不做宽泛前缀放行）')

const sql = read('sql/migration-20261025-content-security.sql')
check(/CREATE TABLE IF NOT EXISTS t_media_check/i.test(sql), '迁移脚本建 t_media_check')
check(/UNIQUE KEY uk_trace/.test(sql), 'trace_id 唯一索引（回调幂等）')

// ---------------------------------------------------------------- 5. 配置
console.log('\n[5] 配置项')

const authYml = read('campus-auth/src/main/resources/application.yml')
check(/sec-check:/.test(authYml) && /enabled:/.test(authYml), 'campus.wx.sec-check.enabled 存在')
check(/media-enabled:/.test(authYml), 'campus.wx.sec-check.media-enabled 存在')
check(/callback-token:/.test(authYml), 'campus.wx.callback-token 存在')

// ---------------------------------------------------------------- 汇总
console.log('\n' + '='.repeat(46))
if (failures.length) {
  console.log('失败 ' + failures.length + ' 项：')
  failures.forEach(function (f) {
    console.log('  - ' + f)
  })
  console.log('\n========== 通过 ' + passed + ' / 失败 ' + failures.length + ' ==========')
  process.exit(1)
}
console.log('========== 通过 ' + passed + ' / 失败 0 ==========')
