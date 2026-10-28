/**
 * 需求广场（求物 / 求帮忙）自检
 * ------------------------------------------------------------------
 * 覆盖五层，任何一层漏改都会被这里拦住：
 *   1. 数据库迁移（两张表 + 关键列 + 加密列）
 *   2. 后端实体 / 分类 / 可见范围 / 状态机
 *   3. 网关路由与公开读放行（最容易漏的一层：漏了就是整模块 404）
 *   4. 小程序分包注册 / preloadRule / 入口
 *   5. 前后端常量一致性（大类、状态、分类）
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const MP = path.join(ROOT, 'miniprogram')
const BE = path.join(ROOT, 'backend')
const SQL = path.join(ROOT, 'sql', 'migration-20261028-demand.sql')

let pass = 0
let fail = 0

function check(name, ok, extra) {
  if (ok) {
    pass++
    console.log('  ✅ ' + name)
  } else {
    fail++
    console.log('  ❌ ' + name + (extra !== undefined ? ' → ' + extra : ''))
  }
}

function read(p) {
  try {
    return fs.readFileSync(p, 'utf8')
  } catch (e) {
    return ''
  }
}

function has(file, re) {
  return re.test(read(file))
}

console.log('\n[1] 数据库迁移')
const sql = read(SQL)
check('迁移脚本存在', !!sql)
check('建了 t_demand', /CREATE TABLE IF NOT EXISTS t_demand/.test(sql))
check('建了 t_demand_reply', /CREATE TABLE IF NOT EXISTS t_demand_reply/.test(sql))
check('t_demand 有 type 列（1求物/2求帮忙）', /type\s+TINYINT/.test(sql))
check('t_demand 有 visibility 列且默认 school', /visibility\s+VARCHAR\(16\)\s+NOT NULL DEFAULT 'school'/.test(sql))
check('t_demand 有 budget 且可空（NULL = 面议）', /budget\s+DECIMAL\(10,2\)\s+DEFAULT NULL/.test(sql))
check('t_demand 有 status 列', /status\s+TINYINT\s+NOT NULL DEFAULT 1/.test(sql))
check('t_demand_reply 有 contact 列', /contact\s+VARCHAR\(255\)/.test(sql))
check('t_demand_reply 有 idx_demand 索引', /KEY idx_demand/.test(sql))

console.log('\n[2] 后端实体与服务')
const demandEntity = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/entity/Demand.java'))
check('Demand 实体存在', !!demandEntity)
check('Demand 有 TYPE_WANT=1', /TYPE_WANT = 1/.test(demandEntity))
check('Demand 有 TYPE_HELP=2', /TYPE_HELP = 2/.test(demandEntity))
check('Demand 有 STATUS_DONE=2', /STATUS_DONE = 2/.test(demandEntity))
check('Demand 声明了 visibility 字段', /private String visibility/.test(demandEntity))

const replyEntity = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/entity/DemandReply.java'))
check('DemandReply 带 autoResultMap=true（否则加密列读出密文）',
  /@TableName\(value = "t_demand_reply", autoResultMap = true\)/.test(replyEntity))
check('DemandReply 的 contact 用 EncryptedStringTypeHandler',
  /typeHandler = EncryptedStringTypeHandler\.class/.test(replyEntity))

const service = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/service/DemandService.java'))
check('DemandService 存在', !!service)
check('分类白名单里求物 7 项', (service.match(/new DemandCategoryVO\(1,/g) || []).length === 7)
check('分类白名单里求帮忙 6 项', (service.match(/new DemandCategoryVO\(2,/g) || []).length === 6)
check('列表只返回进行中（status=1）', /eq\(Demand::getStatus, Demand\.STATUS_OPEN\)/.test(service))
check('本市流透传 schoolScope（OR 本校）',
  /and\(q -> q\.eq\(Demand::getVisibility, Visibility\.CITY\)\s*\.or\(\)\.eq\(Demand::getSchoolId, schoolId\)\)/.test(service))
check('没有「or() 后跟条件 eq」的悬空 OR 写法', !/\.or\(\)\s*\n?\s*\.eq\(true|or\(\)\.eq\(schoolId != null/.test(service))
check('有 assertVisible（详情独立校验）', /private void assertVisible\(Demand/.test(service))
check('assertVisible 抛 404 而非 403', /BizException\.notFound\("需求不存在或已被删除"\)/.test(service))
check('发布走 SecurityGuard.purify', /purify\(userId,[\s\S]{0,80}BIZ_DEMAND/.test(service))
check('发布走 SecurityGuard.checkText', /checkText\(userId, title \+ "\\n" \+ description,[\s\S]{0,80}BIZ_DEMAND/.test(service))
check('回应也送审（BIZ_DEMAND_REPLY）', /BIZ_DEMAND_REPLY/.test(service))
check('一人一需求只能回应一次', /hasReplied\(demandId, userId\)/.test(service))
check('删需求时级联删回应', /delete\(Wrappers\.<DemandReply>lambdaQuery\(\)\.eq\(DemandReply::getDemandId, id\)\)/.test(service))
check('回应列表校验发布者身份', /只能查看自己收到的回应/.test(service))

const guard = read(path.join(BE, 'campus-common/src/main/java/com/campus/common/client/SecurityGuard.java'))
check('SecurityGuard 新增 BIZ_DEMAND', /BIZ_DEMAND = "DEMAND"/.test(guard))
check('SecurityGuard 新增 BIZ_DEMAND_REPLY', /BIZ_DEMAND_REPLY = "DEMAND_REPLY"/.test(guard))

console.log('\n[3] 网关')
const gwYml = read(path.join(BE, 'campus-gateway/src/main/resources/application.yml'))
check('网关路由含 /api/demands/**', /Path=\/api\/goods\/\*\*,\/api\/demands\/\*\*/.test(gwYml))
const filter = read(path.join(BE, 'campus-gateway/src/main/java/com/campus/gateway/filter/AuthGlobalFilter.java'))
check('PUBLIC_READ_PREFIX 放行 /api/demands', /"\/api\/demands"/.test(filter))
check('PRIVATE_SEGMENTS 含 replies（回应列表不放行游客）', /"replies"/.test(filter))

console.log('\n[4] 小程序分包与入口')
const appJson = JSON.parse(read(path.join(MP, 'app.json')))
const demandPkg = (appJson.subPackages || []).filter(function (p) { return p.name === 'demand' })[0]
check('app.json 注册 demand 分包', !!demandPkg)
check('demand 分包含 4 个页面',
  !!demandPkg && demandPkg.pages.join(',') === 'list,detail,publish,mine', demandPkg && demandPkg.pages.join(','))
const pages = ['list', 'detail', 'publish', 'mine']
pages.forEach(function (p) {
  const dir = path.join(MP, 'subpackages/demand')
  check('subpackages/demand/' + p + ' 四件套齐全',
    fs.existsSync(path.join(dir, p + '.js')) &&
    fs.existsSync(path.join(dir, p + '.wxml')) &&
    fs.existsSync(path.join(dir, p + '.wxss')) &&
    fs.existsSync(path.join(dir, p + '.json')))
})
const userPreload = (appJson.preloadRule || {})['pages/user/index']
const zonePreload = (appJson.preloadRule || {})['pages/zone/index']
check('我的页预下载 demand 分包', !!userPreload && userPreload.packages.indexOf('subpackages/demand') >= 0)
check('功能区页预下载 demand 分包', !!zonePreload && zonePreload.packages.indexOf('subpackages/demand') >= 0)

const zones = read(path.join(MP, 'utils/zones.js'))
check('功能区有「需求广场」固定入口', /key: 'demand'/.test(zones))
check('入口落点指向 demand 分包', /url: '\/subpackages\/demand\/list'/.test(zones))
const userJs = read(path.join(MP, 'pages/user/index.js'))
check('我的页有 goMyDemands', /goMyDemands: function/.test(userJs))
check('我的页有 goDemandSquare', /goDemandSquare: function/.test(userJs))
check('我的页跳 demand 分包（非主包）', /\/subpackages\/demand\/mine/.test(userJs))

console.log('\n[5] 前后端常量一致性')
const demandUtil = read(path.join(MP, 'utils/demand.js'))
check('前端大类 1 = 求物', /key: 1, label: '求物'/.test(demandUtil))
check('前端大类 2 = 求帮忙', /key: 2, label: '求帮忙'/.test(demandUtil))
check('前端状态文案与后端一致（0已下架/1进行中/2已完成）',
  /0: '已下架'/.test(demandUtil) && /1: '进行中'/.test(demandUtil) && /2: '已完成'/.test(demandUtil))
check('预算为空显示面议（不显示 ¥0）', /return '面议'/.test(demandUtil))
const listJs = read(path.join(MP, 'subpackages/demand/list.js'))
check('换大类时清掉二级分类（防串类）', /type: t, categoryId: 0, categories: \[\]/.test(listJs))
const publishJs = read(path.join(MP, 'subpackages/demand/publish.js'))
check('换大类时清掉二级分类（防串类）', /type: t, categoryId: 0, categories: \[\]/.test(publishJs))
check('发布前把本地图换成服务端 URL', /upload\.uploadImages/.test(publishJs))

console.log('\n[6] WXML 表达式')
const wxmlls = pages.map(function (p) { return path.join(MP, 'subpackages/demand', p + '.wxml') })
wxmlls.forEach(function (f) {
  const txt = read(f).replace(/<!--[\s\S]*?-->/g, '')
  const exps = txt.match(/\{\{[^}]*\}\}/g) || []
  let bad = 0
  exps.forEach(function (e) {
    const inner = e.slice(2, -2).replace(/'[^']*'/g, "''").replace(/"[^"]*"/g, '""')
    let depth = 0
    for (const ch of inner) {
      if (ch === '(' || ch === '[') depth++
      if (ch === ')' || ch === ']') depth--
      if (depth < 0) bad++
    }
    if (depth !== 0) bad++
  })
  check(path.basename(f) + ' 表达式括号配平', bad === 0, bad)
})

console.log('\n[7] 后台管理（campus-admin + admin-web）')
const goodsAdmin = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/controller/InternalAdminDemandController.java'))
check('goods 侧有后台处置通道', /RequestMapping\("\/internal\/admin\/demands"\)/.test(goodsAdmin))
check('后台只能下架/恢复，不碰已完成',
  /status != 0 && status != 1/.test(goodsAdmin) && /需求已完成，不可改回进行中/.test(goodsAdmin))
check('后台删除级联删回应', /replyMapper\.delete\(/.test(goodsAdmin))
check('后台分类字典由 goods 下发（唯一数据源）', /GetMapping\("\/categories"\)/.test(goodsAdmin))

const adminDir = path.join(BE, 'campus-admin/src/main/java/com/campus/admin')
check('admin 有 DemandRow', fs.existsSync(path.join(adminDir, 'dto/DemandRow.java')))
check('admin 有 DemandQueryMapper', /FROM t_demand d/.test(read(path.join(adminDir, 'mapper/DemandQueryMapper.java'))))
check('admin 有 DemandAdminClient（Feign）', /path = "\/internal\/admin\/demands"/.test(read(path.join(adminDir, 'client/DemandAdminClient.java'))))
const gateway = read(path.join(adminDir, 'client/AdminWriteGateway.java'))
check('写网关有 setDemandStatus', /public void setDemandStatus\(/.test(gateway))
check('写网关有 deleteDemand', /public void deleteDemand\(/.test(gateway))
check('删除不做降级直写（不可逆操作不能悄悄执行）', /删除是不可逆操作/.test(gateway))
check('降级 Mapper 有 updateDemandStatus 且不碰已完成',
  /UPDATE t_demand SET status = #{status} WHERE id = #{id} AND status <> 2/
    .test(read(path.join(adminDir, 'mapper/FallbackWriteMapper.java'))))
const contentCtrl = read(path.join(adminDir, 'controller/ContentController.java'))
check('后台有 /content/demands 列表', /@GetMapping\("\/demands"\)/.test(contentCtrl))
check('后台有需求详情', /@GetMapping\("\/demands\/\{id\}"\)/.test(contentCtrl))
check('后台有下架接口', /@PostMapping\("\/demands\/\{id\}\/status"\)/.test(contentCtrl))
check('后台有删除接口', /@DeleteMapping\("\/demands\/\{id\}"\)/.test(contentCtrl))
check('后台接口都带权限点', /admin:content:demand:list/.test(contentCtrl) && /admin:content:demand:delete/.test(contentCtrl))
check('处置落审计流水', /recordAudit\("DEMAND"/.test(read(path.join(adminDir, 'service/ContentService.java'))))

const WEB = path.join(ROOT, 'admin-web/src')
check('后台页面 DemandList.vue 存在', fs.existsSync(path.join(WEB, 'views/content/DemandList.vue')))
check('后台 api 有 demands 四个方法', /demands: /.test(read(path.join(WEB, 'api/content.js'))) &&
  /deleteDemand: /.test(read(path.join(WEB, 'api/content.js'))) &&
  /demandCategories: /.test(read(path.join(WEB, 'api/content.js'))))
check('DELETE 带 body 走 { data }', /request\.delete\(BASE \+ '\/demands\/' \+ id, \{ data \}\)/.test(read(path.join(WEB, 'api/content.js'))))
check('字典有 DEMAND_TYPE / DEMAND_STATUS',
  /DEMAND_TYPE/.test(read(path.join(WEB, 'utils/dict.js'))) && /DEMAND_STATUS/.test(read(path.join(WEB, 'utils/dict.js'))))
const menuSql = read(path.join(BE, 'sql/migration-20261028-demand-admin.sql'))
check('菜单 SQL 用 9330 段（避开 9310/9320）', /\(9330,/.test(menuSql))
check('菜单组件路径与文件对应', /content\/DemandList/.test(menuSql))
check('菜单给角色授权', /t_admin_role_menu/.test(menuSql))
check('弹窗用 append-to-body（顶栏 backdrop-filter 会裁掉）',
  /append-to-body/.test(read(path.join(WEB, 'views/content/DemandList.vue'))))

console.log('\n[8] 互通推荐（需求 ↔ 闲置）')
const matchSvc = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/service/DemandMatchService.java'))
check('DemandMatchService 存在', !!matchSvc)
check('需求 → 闲置', /public List<MatchGoodsVO> matchGoods\(/.test(matchSvc))
check('闲置 → 需求', /public List<MatchDemandVO> matchDemands\(/.test(matchSvc))
check('候选池有上限（不全表扫描）', /POOL = 300/.test(matchSvc))
check('推荐也过滤可见范围（不把外校仅本校内容推给人）',
  /"city"\.equals\(g\.getVisibility\(\)\)/.test(matchSvc) && /"city"\.equals\(d\.getVisibility\(\)\)/.test(matchSvc))
check('只匹配在售 / 进行中的内容',
  /eq\(Goods::getStatus, 1\)/.test(matchSvc) && /eq\(Demand::getStatus, Demand\.STATUS_OPEN\)/.test(matchSvc))
check('标题命中权重高于描述', /s \+= 2/.test(matchSvc) && /s \+= 1/.test(matchSvc))
const demandCtrlJava = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/controller/DemandController.java'))
check('接口 GET /demands/{id}/matches', /@GetMapping\("\/\{id\}\/matches"\)/.test(demandCtrlJava))
check('接口 GET /goods/{id}/demands',
  /@GetMapping\("\/\{id\}\/demands"\)/.test(read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/controller/GoodsController.java'))))
const dDetailJs = read(path.join(MP, 'subpackages/demand/detail.js'))
check('需求详情页拉推荐', /loadMatches: function/.test(dDetailJs))
check('推荐失败不影响主流程（单独 catch）', /loadMatches\(\)/.test(dDetailJs))
check('需求详情页可跳闲置', /goGoodsDetail: function/.test(dDetailJs))
const gDetailJs = read(path.join(MP, 'pages/goods/detail.js'))
check('闲置详情页拉推荐', /loadWanted: function/.test(gDetailJs))
check('闲置详情页可跳需求', /goDemandDetail: function/.test(gDetailJs))
check('跳分包路径正确', /\/subpackages\/demand\/detail\?id=/.test(gDetailJs))
const gdPreload = (appJson.preloadRule || {})['pages/goods/detail']
check('闲置详情页预下载 demand 分包（否则跳转白屏）',
  !!gdPreload && gdPreload.packages.indexOf('subpackages/demand') >= 0)

console.log('\n========== 结果：' + pass + ' 通过 / ' + fail + ' 失败 ==========\n')
process.exit(fail === 0 ? 0 : 1)
