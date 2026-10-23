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
check('6 个频道', chs.data.length === 6)
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
check('发帖缺标题被拦', call('POST', '/posts', { channelId: 2004, title: '', content: 'x' }).code === 400)
const editPost = call('PUT', '/posts/' + newPost.data, {
  channelId: 2002,
  title: '改过的标题',
  content: '改过的正文',
  images: []
})
check('编辑自己的帖子', editPost.code === 0, editPost)
const pd2 = call('GET', '/posts/' + newPost.data).data
check('帖子标题已更新', pd2.title === '改过的标题')
check('帖子正文已更新', pd2.content === '改过的正文')
check('帖子频道已更新', pd2.channelId === 2002)
check('编辑他人帖子被拒', call('PUT', '/posts/6002', { channelId: 2002, title: 'x', content: 'y' }).code === 403)
check('编辑缺标题被拒', call('PUT', '/posts/' + newPost.data, { channelId: 2002, title: '  ', content: 'y' }).code === 400)
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

console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
process.exit(fail ? 1 : 0)
