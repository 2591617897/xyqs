// 临时验证脚本：模拟小程序宿主，跑一遍 mock 数据层
const store = {}
global.qq = {
  getStorageSync: (k) => (k in store ? store[k] : ''),
  setStorageSync: (k, v) => { store[k] = JSON.parse(JSON.stringify(v)) },
  removeStorageSync: (k) => { delete store[k] },
  showToast: () => {},
  navigateTo: () => {},
  switchTab: () => {},
  showModal: () => {}
}
global.getApp = () => ({ globalData: {} })

const mock = require(require('path').join(__dirname, '../miniprogram/utils/mock.js'))

let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  ok   ' + name) }
  else { fail++; console.log('  FAIL ' + name + (extra ? ' → ' + JSON.stringify(extra) : '')) }
}
function call(method, url, data) { return mock.handle(method, url, data) }

console.log('\n[1] 未登录访问受保护接口')
check('GET /user/me 返回 401', call('GET', '/user/me').code === 401)
check('POST /goods 返回 401', call('POST', '/goods', { title: 'x', price: 1 }).code === 401)
check('浏览商品不需要登录', call('GET', '/goods', { page: 1, size: 5 }).code === 0)

console.log('\n[2] 登录')
const login = call('POST', '/auth/login', { code: 'x' })
check('返回 token', login.code === 0 && !!login.data.token)
check('用户已认证', login.data.user.verifyStatus === 2)

console.log('\n[3] 商品列表：分页 / 搜索 / 分类 / 排序')
const p1 = call('GET', '/goods', { page: 1, size: 4 })
check('第一页 4 条', p1.data.records.length === 4, p1.data.records.length)
check('total = 15', p1.data.total === 15, p1.data.total)
check('hasMore', p1.data.hasMore === true)
const p4 = call('GET', '/goods', { page: 4, size: 4 })
check('第四页 3 条', p4.data.records.length === 3, p4.data.records.length)
check('末页 hasMore=false', p4.data.hasMore === false)
const kw = call('GET', '/goods', { keyword: '键盘' })
check('搜索「键盘」命中 1 条', kw.data.total === 1, kw.data.total)
const cat = call('GET', '/goods', { categoryId: 2 })
check('分类「图书」2 条', cat.data.total === 2, cat.data.total)
const asc = call('GET', '/goods', { sort: 'price_asc', size: 20 })
check('价格升序', asc.data.records[0].price <= asc.data.records[1].price)
const hot = call('GET', '/goods', { sort: 'hot', size: 3 })
check('热门按浏览量降序', hot.data.records[0].viewCount >= hot.data.records[1].viewCount)
check('列表带 categoryName', !!asc.data.records[0].categoryName)

console.log('\n[4] 商品详情 / 收藏')
const detail = call('GET', '/goods/8006')
check('详情存在', detail.code === 0 && detail.data.id === 8006)
check('浏览量 +1', detail.data.viewCount === 211, detail.data.viewCount)
check('已收藏 8006', detail.data.liked === true)
const unlike = call('POST', '/goods/8006/like')
check('取消收藏返回 false', unlike.data === false)
check('收藏数 -1', call('GET', '/goods/8006').data.likeCount === 18)
call('POST', '/goods/8006/like')

console.log('\n[5] 发布商品 → 我的闲置')
const created = call('POST', '/goods', {
  title: '测试台灯', description: '测试', price: 19.9, categoryId: 3, images: [], tradeType: 1, location: '宿舍'
})
check('发布返回 id', created.code === 0 && created.data > 100000, created)
const mine = call('GET', '/goods/mine')
check('我的闲置含新商品', mine.data.some((g) => g.title === '测试台灯'))
check('新商品在列表首位（最新）', call('GET', '/goods', { size: 1 }).data.records[0].title === '测试台灯')
check('价格被转成数字', typeof call('GET', '/goods/mine').data[0].price === 'number')

console.log('\n[6] 购买意向')
const order = call('POST', '/goods/8006/order', { contact: 'QQ 999', message: '还在吗' })
check('已买过 → 重复提交被拦', order.code === 400, order)
const order2 = call('POST', '/goods/8003/order', { contact: 'QQ 999' })
check('新意向创建成功', order2.code === 0)
check('买家意向 2 条', call('GET', '/goods/orders/mine', { role: 'buyer' }).data.length === 2)
check('卖家意向 1 条', call('GET', '/goods/orders/mine', { role: 'seller' }).data.length === 1)
check('意向带商品标题', !!call('GET', '/goods/orders/mine', { role: 'buyer' }).data[0].goodsTitle)
check('不能买自己的商品', call('POST', '/goods/' + created.data + '/order', { contact: 'x' }).code === 400)

console.log('\n[7] 表白墙：列表 / 发布 / 点赞 / 评论 / 删除')
const wall = call('GET', '/wall/posts', { page: 1, size: 5 })
check('5 条', wall.data.records.length === 5)
check('匿名昵称存在', !!wall.data.records[0].anonName)
check('不返回 userId', wall.data.records[0].userId === undefined)
check('列表已点赞标记正确', wall.data.records.filter((p) => p.id === 7001)[0].liked === true)
const wallLike = call('POST', '/wall/posts/7002/like')
check('点赞返回 true', wallLike.data === true)
check('点赞数 +1', call('GET', '/wall/posts/7002').data.likeCount === 19)
const wallNew = call('POST', '/wall/posts', { content: '测试一条匿名心事', images: [] })
check('发布成功', wallNew.code === 0)
const wallList2 = call('GET', '/wall/posts', { page: 1, size: 1 })
check('最新一条在最前', wallList2.data.records[0].id === wallNew.data)
check('mine 标记', wallList2.data.records[0].mine === true)
const wallHot = call('GET', '/wall/posts', { page: 1, size: 4, sort: 'hot' })
check(
  '最热排序生效',
  wallHot.data.records[0].likeCount >= wallHot.data.records[1].likeCount &&
    wallHot.data.records[1].likeCount >= wallHot.data.records[2].likeCount,
  wallHot.data.records.map((r) => r.likeCount).join(',')
)
const wc = call('POST', '/wall/posts/' + wallNew.data + '/comments', { content: '第一次评论' })
check('评论成功', wc.code === 0)
check('评论数 +1', call('GET', '/wall/posts/' + wallNew.data).data.commentCount === 1)
const wcl = call('GET', '/wall/posts/' + wallNew.data + '/comments')
check('评论列表 1 条', wcl.data.length === 1)
check('评论不泄漏 userId', wcl.data[0].userId === undefined)
const wcImg = call('POST', '/wall/posts/' + wallNew.data + '/comments', {
  content: '',
  images: ['a.png', 'b.png']
})
check('只发图不写字的评论', wcImg.code === 0, wcImg)
check('空内容且无图被拒', call('POST', '/wall/posts/' + wallNew.data + '/comments', {}).code === 400)
const wcl2 = call('GET', '/wall/posts/' + wallNew.data + '/comments').data
check('评论 2 条', wcl2.length === 2)
check('评论图片返回 imageList', wcl2[1].imageList.length === 2, wcl2[1].imageList)
check('删除自己的评论', call('DELETE', '/wall/posts/' + wallNew.data + '/comments/' + wc.data).code === 0)
check('删除后剩 1 条', call('GET', '/wall/posts/' + wallNew.data + '/comments').data.length === 1)
check('评论数同步减 1', call('GET', '/wall/posts/' + wallNew.data).data.commentCount === 1)
check('删除他人评论被拒', call('DELETE', '/wall/posts/7001/comments/5001').code === 403)
const editRes = call('PUT', '/wall/posts/' + wallNew.data, { content: '改过的心事，还是匿名', images: [] })
check('编辑自己的表白', editRes.code === 0, editRes)
const edited = call('GET', '/wall/posts/' + wallNew.data).data
check('编辑后内容已更新', edited.content === '改过的心事，还是匿名')
check('编辑后匿名昵称不变', edited.anonName === wallList2.data.records[0].anonName)
check('编辑后评论数保留', edited.commentCount === 1)
check('编辑他人表白被拒', call('PUT', '/wall/posts/7001', { content: 'x' }).code === 403)
check('编辑为空被拒', call('PUT', '/wall/posts/' + wallNew.data, { content: '   ' }).code === 400)
check('删除自己的表白', call('DELETE', '/wall/posts/' + wallNew.data).code === 0)
check('删除后列表不可见', call('GET', '/wall/posts', { size: 50 }).data.records.every((p) => p.id !== wallNew.data))
check('删除他人表白被拒', call('DELETE', '/wall/posts/7001').code === 403)

console.log('\n[8] 频道 / 帖子')
const chs = call('GET', '/channels')
// 分类整理后只保留 3 个上线频道（2004 树洞吐槽 / 2005 二手交易 已软下线，
// 详见 sql/migration-20261027-category-tidy.sql）
check('3 个上线频道', chs.data.length === 3, chs.data.length)
check('下线频道不出现在 /channels', !chs.data.some((c) => c.id === 2004 || c.id === 2005))
check('频道带 postCount', typeof chs.data[0].postCount === 'number')
const posts = call('GET', '/posts', { page: 1, size: 3 })
check('帖子 3 条', posts.data.records.length === 3)
check('帖子带频道名', !!posts.data.records[0].channelName)
check('total 12', posts.data.total === 12, posts.data.total)
const byCh = call('GET', '/posts', { channelId: 2003 })
check('频道 2003 有 3 帖', byCh.data.total === 3, byCh.data.total)
const newPost = call('POST', '/posts', { channelId: 2004, title: '测试帖子', content: '正文', images: [] })
check('发帖成功', newPost.code === 0)
const pd = call('GET', '/posts/' + newPost.data)
check('详情 mine = true', pd.data.mine === true)
check('详情带 authorName', pd.data.authorName === '林小满')
const pc = call('POST', '/posts/' + newPost.data + '/comments', { content: '沙发' })
check('评论成功', pc.code === 0)
check('评论数 1', call('GET', '/posts/' + newPost.data).data.commentCount === 1)
const pcImg = call('POST', '/posts/' + newPost.data + '/comments', {
  content: '',
  images: ['http://x/1.jpg']
})
check('只发图也能评论', pcImg.code === 0)
check('评论带图片', call('GET', '/posts/' + newPost.data + '/comments')
  .data.filter((c) => c.id === pcImg.data)[0].imageList.length === 1)
check('空评论被拦', call('POST', '/posts/' + newPost.data + '/comments', { content: ' ', images: [] }).code === 400)
check('删除自己的帖子评论', call('DELETE', '/posts/' + newPost.data + '/comments/' + pcImg.data).code === 0)
check('删评论后计数 -1', call('GET', '/posts/' + newPost.data).data.commentCount === 1)
check('删他人帖子评论被拒', call('DELETE', '/posts/' + newPost.data + '/comments/9001').code === 404)
check('发帖缺标题被拦', call('POST', '/posts', { channelId: 2004, title: '', content: 'x' }).code === 400)
const editPost = call('PUT', '/posts/' + newPost.data, {
  channelId: 2003,
  title: '改过的标题',
  content: '改过的正文',
  images: []
})
check('编辑自己的帖子', editPost.code === 0, editPost)
const pd2 = call('GET', '/posts/' + newPost.data).data
check('帖子标题已更新', pd2.title === '改过的标题')
check('帖子正文已更新', pd2.content === '改过的正文')
check('帖子频道已更新', pd2.channelId === 2003)
check('编辑他人帖子被拒', call('PUT', '/posts/6002', { channelId: 2003, title: 'x', content: 'y' }).code === 403)
check('编辑缺标题被拒', call('PUT', '/posts/' + newPost.data, { channelId: 2003, title: '  ', content: 'y' }).code === 400)
check('删除自己的帖子', call('DELETE', '/posts/' + newPost.data).code === 0)

console.log('\n[9] 用户 / 认证')
const me = call('GET', '/user/me')
check('学校名回显', me.data.schoolName === '浙江大学')
check('学号脱敏', me.data.studentNo === '****0101', me.data.studentNo)
check('姓名脱敏', me.data.realName === '林*', me.data.realName)
check('已认证时重复提交被拦', call('POST', '/user/verify', {
  schoolId: 1005, studentNo: '2022010101', realName: '林满', verifyImg: 'x'
}).code === 400)
check('学校搜索', call('GET', '/schools', { keyword: '杭州' }).data.length === 1)

console.log('\n[10] 退出登录')
mock.logout()
check('退出后 me 401', call('GET', '/user/me').code === 401)
check('退出后仍可浏览', call('GET', '/posts').code === 0)
check('退出后点赞需登录', call('POST', '/posts/6001/like').code === 401)

console.log('\n[11] 重新登录后数据仍在')
call('POST', '/auth/login', {})
check('仍是我发布的数据', call('GET', '/goods/mine').data.length === 2)
check('认证状态保留', call('GET', '/user/me').data.verifyStatus === 2)

console.log('\n[12] 关注 / 粉丝与个人主页')
// 未登录：主页可读、状态为 false、写操作 401
mock.logout()
const anonStatus = call('GET', '/user/follow/status?userId=9002')
check('未登录关注状态为 false', anonStatus.code === 0 && anonStatus.data === false, anonStatus)
const anonProfile = call('GET', '/user/9002/profile')
check('未登录可访问公开主页', anonProfile.code === 0 && anonProfile.data.nickname === '陈屿')
check('未登录 following=false', anonProfile.data.following === false)
check('未登录 isSelf=false', anonProfile.data.isSelf === false)
check(
  '主页不含敏感字段',
  anonProfile.data.phone === undefined &&
    anonProfile.data.studentNo === undefined &&
    anonProfile.data.realName === undefined,
  anonProfile.data
)
check('主页 404（不存在的用户）', call('GET', '/user/9999/profile').code === 404)
check('未登录关注被拦（401）', call('POST', '/user/follow/9002').code === 401)
check('未登录取消关注被拦（401）', call('DELETE', '/user/follow/9002').code === 401)

// 登录后：me 带计数、主页关系态
call('POST', '/auth/login', {})
const meVO = call('GET', '/user/me')
check('me 带 fansCount/followCount', typeof meVO.data.fansCount === 'number' && typeof meVO.data.followCount === 'number', meVO.data)
check('我的粉丝数=2（初始 9003/9005→9001）', meVO.data.fansCount === 2, meVO.data.fansCount)
check('我的关注数=1（初始 9001→9002）', meVO.data.followCount === 1, meVO.data.followCount)
const selfProfile = call('GET', '/user/9001/profile')
check('本人主页 isSelf=true', selfProfile.data.isSelf === true)
check('本人主页 following=false', selfProfile.data.following === false)

// 关注 9003：计数联动 + 幂等
const fansBefore = call('GET', '/user/9003/profile').data.fansCount
const followed = call('POST', '/user/follow/9003')
check('关注成功', followed.code === 0 && followed.data.following === true && followed.data.fansCount === fansBefore + 1, followed.data)
check('重复关注幂等（计数不涨）', call('POST', '/user/follow/9003').data.fansCount === fansBefore + 1)
check('我的关注数 +1', call('GET', '/user/me').data.followCount === 2)
check('关注状态为 true', call('GET', '/user/follow/status?userId=9003').data === true)
const p3 = call('GET', '/user/9003/profile')
check('主页 following=true', p3.data.following === true)
check('主页 fansCount 已 +1', p3.data.fansCount === fansBefore + 1)

// 取消关注：计数 -1 + 幂等
const unfollowed = call('DELETE', '/user/follow/9003')
check('取消关注成功', unfollowed.code === 0 && unfollowed.data.following === false && unfollowed.data.fansCount === fansBefore, unfollowed.data)
check('未关注再取消仍成功（幂等）', call('DELETE', '/user/follow/9003').code === 0)
check('我的关注数回到 1', call('GET', '/user/me').data.followCount === 1)
check('取消后状态为 false', call('GET', '/user/follow/status?userId=9003').data === false)

// 业务校验
check('关注自己被拦（400）', call('POST', '/user/follow/9001').code === 400)
check('关注不存在的用户（404）', call('POST', '/user/follow/9999').code === 404)

// 列表按作者过滤 + 主页计数与列表一致
const userPosts = call('GET', '/posts', { userId: 9002, page: 1, size: 10 })
check('该作者帖子分页结构完整', userPosts.code === 0 && typeof userPosts.data.total === 'number' && typeof userPosts.data.hasMore === 'boolean')
check('该作者帖子全部属于该作者', userPosts.data.records.every((p) => p.userId === 9002))
const userGoods = call('GET', '/goods', { userId: 9002, page: 1, size: 10 })
check('该作者商品全部属于该作者', userGoods.data.records.every((g) => g.userId === 9002))
check('主页帖子数与列表一致', call('GET', '/user/9002/profile').data.postCount === userPosts.data.total)
check('主页在售数与列表一致', call('GET', '/user/9002/profile').data.goodsSaleCount === userGoods.data.records.filter((g) => g.status === 1).length)

console.log('\n[13] 私信左滑删除（会话隐藏 / 消息撤回）')
// 已登录状态（第 12 段末尾已登录）
const beforeList = call('GET', '/messages').data
check('消息中心返回会话列表', beforeList.convos.length > 0, beforeList.convos.length)
const targetConvo = beforeList.convos[0]
check('会话带 lastFrom 字段', targetConvo.lastFrom === 'me' || targetConvo.lastFrom === 'peer', targetConvo.lastFrom)

// 删除会话：只对自己隐藏
const delRes = call('DELETE', '/messages/' + targetConvo.id)
check('删除会话返回成功', delRes.code === 0, delRes)
const afterList = call('GET', '/messages').data
check('被删会话已从列表移除', afterList.convos.every((c) => String(c.id) !== String(targetConvo.id)))
check('其他会话不受影响', afterList.convos.length === beforeList.convos.length - 1)
// 幂等：重复删除仍成功
check('重复删除会话仍成功（幂等）', call('DELETE', '/messages/' + targetConvo.id).code === 0)

// 撤回消息：只能删自己发的
const convoDetail2 = call('GET', '/messages/' + targetConvo.id)
check('会话详情可读', convoDetail2.code === 0 && convoDetail2.data.messages.length > 0)
const myMsg = convoDetail2.data.messages.filter((m) => m.from === 'me')[0]
const peerMsg = convoDetail2.data.messages.filter((m) => m.from === 'peer')[0]
if (myMsg) {
  const delMsg = call('DELETE', '/messages/' + targetConvo.id + '/messages/' + myMsg.id)
  check('撤回自己的消息成功', delMsg.code === 0, delMsg)
  const afterMsg = call('GET', '/messages/' + targetConvo.id).data.messages
  check('撤回后消息不再返回', afterMsg.every((m) => String(m.id) !== String(myMsg.id)))
  check('重复撤回被拒', call('DELETE', '/messages/' + targetConvo.id + '/messages/' + myMsg.id).code === 400)
}
if (peerMsg) {
  const delPeer = call('DELETE', '/messages/' + targetConvo.id + '/messages/' + peerMsg.id)
  check('撤回对方消息被拒（403）', delPeer.code === 403, delPeer)
}
check('撤回不存在的消息 404', call('DELETE', '/messages/' + targetConvo.id + '/messages/999999').code === 404)
// 未登录不能删除
mock.logout()
check('未登录删除会话被拦（401）', call('DELETE', '/messages/' + targetConvo.id).code === 401)
call('POST', '/auth/login', {})

console.log('\n[14] 首页功能区（utils/zones.js）')
const zones = require(require('path').join(__dirname, '../miniprogram/utils/zones.js'))
const chList = call('GET', '/channels').data
const entries = zones.buildEntries(chList)
check('金刚区 = 3 固定入口 + 3 频道', entries.length === 6, entries.length)
check('金刚区首项是「树洞闲谈」', entries[0].title === '树洞闲谈' && entries[0].url === '/pages/wall/index')
check('金刚区含闲置交换', entries.some((e) => e.title === '闲置交换' && e.url === '/pages/goods/list'))
check('频道入口带 channelId 参数', entries.some((e) => e.url === '/pages/post/list?channelId=2001'))
check('入口都有图标与底色', entries.every((e) => !!e.icon && !!e.tone))

const zPosts = call('GET', '/posts', { page: 1, size: 16 }).data
const zWall = call('GET', '/wall/posts', { page: 1, size: 6 }).data
const zGoods = call('GET', '/goods', { page: 1, size: 6 }).data
const zHot = call('GET', '/posts', { page: 1, size: 1, sort: 'hot' }).data
const zAll = zones.buildZones({ posts: zPosts, hot: zHot, wall: zWall, goods: zGoods }, chList)
const zKeys = zAll.map((z) => z.key)
check('生成 5 个功能区', zAll.length === 5, zKeys)
check('区顺序：热/墙/闲置/公告/生活', zKeys.join(',') === 'hot,wall,goods,notice,campus', zKeys.join(','))
check('「我想说」区内嵌表白墙数据', zAll[1].items.length <= 3 && zAll[1].items.length > 0 && zAll[1].items[0].kind === 'wall', zAll[1].items.length)
check('表白墙卡片带匿名头像与标签', !!zAll[1].items[0].avatarClass && zAll[1].items[0].tags.length === 1)
check('闲置区卡片带价格', zAll[2].items[0].priceText !== undefined && zAll[2].items[0].kind === 'goods')
check('公告区只含 2001 频道', zAll[3].items.every((it) => it.badge === '校园公告'))
check('生活区不含公告频道', zAll[4].items.every((it) => it.badge !== '校园公告'))
// 首页卡片已改单列全宽，每区都收敛到 ZONE_LIMIT（3）条，其余走「更多」
check('单列全宽：每区最多 3 条', zAll.every((z) => z.items.length <= 3), zAll.map((z) => z.items.length))
check('每个区都有标题与图标', zAll.every((z) => !!z.title && !!z.icon))
check('无数据的区不渲染', zones.buildZones({ posts: { records: [] }, hot: { records: [] }, wall: { records: [] }, goods: { records: [] } }, chList).length === 0)

console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
process.exit(fail ? 1 : 0)
