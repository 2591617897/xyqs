/**
 * 本地模拟数据层（Mock Backend）
 * ------------------------------------------------------------------
 * 目标：前端完全不依赖后端，也能跑通所有页面与交互。
 * 特性：
 *   1. 接口路径 / 字段结构与 Spring Boot 后端完全一致
 *   2. 数据持久化到 Storage，退出小程序再次进入数据还在
 *   3. 登录、认证、发布、点赞、评论、收藏、购买意向全部可交互
 *   4. 支持分页 / 搜索 / 排序 / 下拉刷新
 *
 * 重置数据：见文件末尾 reset()，或在「我的」页点底部版权文字。
 * 接真实后端：把 utils/config.js 里的 USE_MOCK 改成 false。
 */

const { mp } = require('./util')

const DB_KEY = 'mock_db_v1'
const SCHOOL_ID = 1005
const SCHOOL_NAME = '浙江大学'
const ME_ID = 9001

let db = null

/* ============================ 基础工具 ============================ */

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function fmt(d) {
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes()) +
    ':' +
    pad(d.getSeconds())
  )
}

/** n 分钟前 */
function ago(n) {
  return fmt(new Date(Date.now() - n * 60000))
}

function ok(data) {
  return { code: 0, msg: 'ok', data: data === undefined ? null : data }
}

function err(code, msg) {
  return { code: code, msg: msg, data: null }
}

function pickPage(q) {
  return {
    page: Math.max(1, Number(q.page) || 1),
    size: Math.max(1, Number(q.size) || 10)
  }
}

function paginate(list, q) {
  const p = pickPage(q)
  const start = (p.page - 1) * p.size
  return {
    records: list.slice(start, start + p.size),
    total: list.length,
    page: p.page,
    size: p.size,
    hasMore: start + p.size < list.length
  }
}

function byTimeDesc(a, b) {
  return a.createdAt < b.createdAt ? 1 : -1
}

function byLikeDesc(a, b) {
  return (b.likeCount || 0) - (a.likeCount || 0)
}

/* ============================ 种子数据 ============================ */

function seed() {
  return {
    seq: 100000,
    session: false,
    profile: {
      id: ME_ID,
      nickname: '林小满',
      avatar: '',
      gender: 2,
      phone: '',
      bio: '浙大在读，喜欢逛食堂和拍照',
      schoolId: SCHOOL_ID,
      studentNo: '2022010101',
      realName: '林满',
      // 想体验「未认证 → 提交认证」流程，把这里改成 0
      verifyStatus: 2,
      verifyRemark: '',
      createdAt: ago(60 * 24 * 40)
    },

    // 演示用户资料（公开主页 / 关注关系用；9001 为当前登录账号，资料以 profile 为准）
    users: [
      { id: 9001, nickname: '林小满', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '浙大在读，喜欢逛食堂和拍照', status: 1 },
      { id: 9002, nickname: '陈屿', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '计算机学院，出闲置回消息很快', status: 1 },
      { id: 9003, nickname: '周小舟', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '经管学院，考研资料出出出', status: 1 },
      { id: 9004, nickname: '苏念', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '经管学院，喜欢图书馆靠窗位', status: 1 },
      { id: 9005, nickname: '阿泽', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '体育学院，夜跑爱好者', status: 1 },
      { id: 9006, nickname: '李知夏', avatar: '', schoolId: SCHOOL_ID, verifyStatus: 2, bio: '人文学院，食堂测评博主', status: 1 }
    ],

    // 初始关注关系（followerId 关注 followeeId）
    follows: [
      { id: 5001, followerId: 9001, followeeId: 9002, createdAt: ago(60 * 24 * 3) },
      { id: 5002, followerId: 9003, followeeId: 9001, createdAt: ago(60 * 24 * 2) },
      { id: 5003, followerId: 9005, followeeId: 9001, createdAt: ago(60 * 24) }
    ],

    schools: [
      { id: 1001, name: '清华大学', province: '北京', city: '北京', district: '海淀区', sort: 100 },
      { id: 1002, name: '北京大学', province: '北京', city: '北京', district: '海淀区', sort: 99 },
      { id: 1003, name: '复旦大学', province: '上海', city: '上海', district: '杨浦区', sort: 98 },
      { id: 1004, name: '上海交通大学', province: '上海', city: '上海', district: '闵行区', sort: 97 },
      { id: 1005, name: '浙江大学', province: '浙江', city: '杭州', district: '西湖区', sort: 96 },
      { id: 1006, name: '武汉大学', province: '湖北', city: '武汉', district: '武昌区', sort: 95 },
      { id: 1007, name: '四川大学', province: '四川', city: '成都', district: '武侯区', sort: 94 },
      { id: 1008, name: '中山大学', province: '广东', city: '广州', district: '海珠区', sort: 93 },
      { id: 1009, name: '华中科技大学', province: '湖北', city: '武汉', district: '洪山区', sort: 92 },
      { id: 1010, name: '南京大学', province: '江苏', city: '南京', district: '鼓楼区', sort: 91 },
      { id: 1011, name: '西安交通大学', province: '陕西', city: '西安', district: '碑林区', sort: 90 },
      { id: 1012, name: '哈尔滨工业大学', province: '黑龙江', city: '哈尔滨', district: '南岗区', sort: 89 },
      { id: 1013, name: '山东大学', province: '山东', city: '济南', district: '历城区', sort: 88 },
      { id: 1014, name: '厦门大学', province: '福建', city: '厦门', district: '思明区', sort: 87 },
      { id: 1015, name: '湖南大学', province: '湖南', city: '长沙', district: '岳麓区', sort: 86 },
      { id: 1016, name: '郑州大学', province: '河南', city: '郑州', district: '高新区', sort: 85 },
      { id: 1017, name: '深圳大学', province: '广东', city: '深圳', district: '南山区', sort: 84 },
      { id: 1018, name: '苏州大学', province: '江苏', city: '苏州', district: '姑苏区', sort: 83 },
      { id: 1019, name: '西南大学', province: '重庆', city: '重庆', district: '北碚区', sort: 82 },
      { id: 1020, name: '兰州大学', province: '甘肃', city: '兰州', district: '城关区', sort: 81 }
    ],

    channels: [
      { id: 2001, name: '校园公告', icon: '📢', description: '失物招领、校园通知、社团招新', sort: 100 },
      { id: 2002, name: '学习交流', icon: '📚', description: '选课攻略、考研保研、资料互换', sort: 90 },
      { id: 2003, name: '吃喝玩乐', icon: '🍜', description: '食堂测评、周边探店、周末去哪儿', sort: 80 },
      { id: 2004, name: '树洞吐槽', icon: '🕳️', description: '今天也想碎碎念一下', sort: 70 },
      { id: 2005, name: '二手交易', icon: '♻️', description: '闲置好物流转，低碳又省钱', sort: 60 },
      { id: 2006, name: '运动搭子', icon: '🏀', description: '篮球、羽毛球、夜跑约起来', sort: 50 }
    ],

    goods: [
      {
        id: 8001,
        userId: 9002,
        schoolId: SCHOOL_ID,
        sellerName: '陈屿',
        sellerAvatar: '',
        title: '九成新机械键盘 87 键 青轴',
        description:
          '去年双十一买的，青轴，手感很好。因为换静音轴了所以出，配件全在，可当面验货。宿舍楼下自提，试到手满意再付钱。',
        price: 129,
        originalPrice: 299,
        categoryId: 1,
        conditionLevel: 2,
        purchaseTime: '2023年11月',
        usageFrequency: '每天使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 二食堂门口',
        status: 1,
        viewCount: 156,
        likeCount: 12,
        createdAt: ago(26)
      },
      {
        id: 8002,
        userId: 9003,
        schoolId: SCHOOL_ID,
        sellerName: '周小舟',
        sellerAvatar: '',
        title: '考研数学全套资料（有笔记）',
        description: '张宇基础 30 讲 + 1000 题 + 历年真题，笔记很全，几乎没有折角。适合刚开始准备的同学。',
        price: 45,
        originalPrice: 180,
        categoryId: 2,
        conditionLevel: 3,
        purchaseTime: '2024年3月',
        usageFrequency: '经常使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 图书馆',
        status: 1,
        viewCount: 92,
        likeCount: 8,
        createdAt: ago(95)
      },
      {
        id: 8003,
        userId: 9004,
        schoolId: SCHOOL_ID,
        sellerName: '苏念',
        sellerAvatar: '',
        title: '宜家三层小推车 白色',
        description: '搬宿舍带不走，轮子很顺，放零食和护肤品正好。轻微使用痕迹，不影响使用。',
        price: 35,
        originalPrice: 99,
        categoryId: 3,
        conditionLevel: 3,
        purchaseTime: '2024年9月',
        usageFrequency: '经常使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 蓝田宿舍楼下',
        status: 1,
        viewCount: 64,
        likeCount: 5,
        createdAt: ago(180)
      },
      {
        id: 8004,
        userId: 9005,
        schoolId: SCHOOL_ID,
        sellerName: '阿泽',
        sellerAvatar: '',
        title: '优衣库轻薄羽绒服 M 码',
        description: '买小了穿不下，只试过一次。藏青色，很适合杭州的冬天。吊牌还在，可以看。',
        price: 160,
        originalPrice: 599,
        categoryId: 4,
        conditionLevel: 2,
        purchaseTime: '2024年10月',
        usageFrequency: '几乎没用过',
        source: '自购自用',
        warranty: '官方保修中',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 西区操场',
        status: 1,
        viewCount: 78,
        likeCount: 6,
        createdAt: ago(300)
      },
      {
        id: 8005,
        userId: 9006,
        schoolId: SCHOOL_ID,
        sellerName: '李知夏',
        sellerAvatar: '',
        title: '迪卡侬羽毛球拍 两支装',
        description: '大一体育课买的，后来一直吃灰。拍线还挺紧，送三个球。适合入门。',
        price: 60,
        originalPrice: 158,
        categoryId: 5,
        conditionLevel: 3,
        purchaseTime: '2023年9月',
        usageFrequency: '偶尔使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 体育馆',
        status: 1,
        viewCount: 41,
        likeCount: 3,
        createdAt: ago(420)
      },
      {
        id: 8006,
        userId: 9002,
        schoolId: SCHOOL_ID,
        sellerName: '陈屿',
        sellerAvatar: '',
        title: '24 寸显示器 1080P 无坏点',
        description: '换了 27 寸的，这台闲置。屏幕无坏点无划痕，送 HDMI 线和支架，宿舍外接笔记本很爽。',
        price: 320,
        originalPrice: 899,
        categoryId: 1,
        conditionLevel: 2,
        purchaseTime: '2023年6月',
        usageFrequency: '每天使用',
        source: '自购自用',
        warranty: '官方保修中',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 玉泉 8 舍',
        status: 1,
        viewCount: 210,
        likeCount: 19,
        createdAt: ago(600)
      },
      {
        id: 8007,
        userId: 9003,
        schoolId: SCHOOL_ID,
        sellerName: '周小舟',
        sellerAvatar: '',
        title: '捷安特通勤自行车 26 寸',
        description: '骑了一年半，刹车刚换过，车锁一起送。毕业出，只能校区内自提。',
        price: 260,
        originalPrice: 780,
        categoryId: 5,
        conditionLevel: 3,
        purchaseTime: '2024年4月',
        usageFrequency: '经常使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 东门车棚',
        status: 1,
        viewCount: 133,
        likeCount: 11,
        createdAt: ago(900)
      },
      {
        id: 8008,
        userId: 9004,
        schoolId: SCHOOL_ID,
        sellerName: '苏念',
        sellerAvatar: '',
        title: '四六级真题（全新未拆封）',
        description: '买多了，两套全新。四级一套六级一套，一起拿走更便宜。',
        price: 15,
        originalPrice: 45,
        categoryId: 2,
        conditionLevel: 1,
        purchaseTime: '2025年9月',
        usageFrequency: '几乎没用过',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 0,
        images: '',
        tradeType: 2,
        location: '可邮寄，邮费另算',
        status: 1,
        viewCount: 27,
        likeCount: 2,
        createdAt: ago(1200)
      },
      {
        id: 8009,
        userId: 9005,
        schoolId: SCHOOL_ID,
        sellerName: '阿泽',
        sellerAvatar: '',
        title: '宿舍小冰箱 50L',
        description: '静音款，制冷正常，内胆干净。太重了不邮寄，需要自己来搬（可以帮忙抬下楼）。',
        price: 180,
        originalPrice: 399,
        categoryId: 3,
        conditionLevel: 3,
        purchaseTime: '2023年12月',
        usageFrequency: '每天使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 蓝田 5 舍',
        status: 1,
        viewCount: 88,
        likeCount: 7,
        createdAt: ago(1500)
      },
      {
        id: 8010,
        userId: 9006,
        schoolId: SCHOOL_ID,
        sellerName: '李知夏',
        sellerAvatar: '',
        title: '护眼台灯 + 备用灯管',
        description: '可调三档亮度，考研期间用的。灯管全新备用一支。',
        price: 30,
        originalPrice: 89,
        categoryId: 3,
        conditionLevel: 2,
        purchaseTime: '2024年6月',
        usageFrequency: '经常使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 图书馆门口',
        status: 1,
        viewCount: 35,
        likeCount: 1,
        createdAt: ago(1800)
      },
      {
        id: 8011,
        userId: 9002,
        schoolId: SCHOOL_ID,
        sellerName: '陈屿',
        sellerAvatar: '',
        title: 'iPad 保护壳 + 类纸膜',
        description: '11 寸通用，壳是深灰色的，膜贴坏了一张还剩一张新的。',
        price: 25,
        originalPrice: 79,
        categoryId: 1,
        conditionLevel: 2,
        purchaseTime: '2025年3月',
        usageFrequency: '偶尔使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 0,
        images: '',
        tradeType: 2,
        location: '可邮寄',
        status: 1,
        viewCount: 19,
        likeCount: 0,
        createdAt: ago(2200)
      },
      {
        id: 8012,
        userId: 9003,
        schoolId: SCHOOL_ID,
        sellerName: '周小舟',
        sellerAvatar: '',
        title: '雅马哈民谣吉他 F310',
        description: '大一社团买的，弹了半年就吃灰了。音准没问题，送琴包和变调夹。',
        price: 350,
        originalPrice: 800,
        categoryId: 6,
        conditionLevel: 3,
        purchaseTime: '2023年9月',
        usageFrequency: '偶尔使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 学生活动中心',
        status: 1,
        viewCount: 76,
        likeCount: 9,
        createdAt: ago(2600)
      },
      {
        id: 8013,
        userId: 9005,
        schoolId: SCHOOL_ID,
        sellerName: '阿泽',
        sellerAvatar: '',
        title: '斯伯丁篮球 7 号',
        description: '室外场打过几次，球皮磨损轻微。打满气再给你。',
        price: 60,
        originalPrice: 159,
        categoryId: 5,
        conditionLevel: 3,
        purchaseTime: '2024年5月',
        usageFrequency: '偶尔使用',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 篮球场',
        status: 1,
        viewCount: 52,
        likeCount: 4,
        createdAt: ago(3000)
      },
      {
        id: 8014,
        userId: 9006,
        schoolId: SCHOOL_ID,
        sellerName: '李知夏',
        sellerAvatar: '',
        title: '毛呢大衣 米白色 S 码',
        description: '去年秋天买的，穿过三四次。干洗过，无起球。身高 160-168 都能穿。',
        price: 120,
        originalPrice: 480,
        categoryId: 4,
        conditionLevel: 2,
        purchaseTime: '2024年10月',
        usageFrequency: '几乎没用过',
        source: '自购自用',
        warranty: '官方保修已过',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 一食堂',
        status: 1,
        viewCount: 61,
        likeCount: 5,
        createdAt: ago(3400)
      },
      {
        id: 8015,
        userId: ME_ID,
        schoolId: SCHOOL_ID,
        sellerName: '林小满',
        sellerAvatar: '',
        title: '罗技 MX Master 3 无线鼠标',
        description: '换了轻一点的鼠标，这个闲置了。滚轮手感是真的好，电池很耐用，送 Type-C 线。',
        price: 260,
        originalPrice: 599,
        categoryId: 1,
        conditionLevel: 2,
        purchaseTime: '2024年2月',
        usageFrequency: '每天使用',
        source: '自购自用',
        warranty: '官方保修中',
        supportTrial: 1,
        images: '',
        tradeType: 1,
        location: '紫金港校区 · 紫金港 4 舍',
        status: 1,
        viewCount: 47,
        likeCount: 6,
        createdAt: ago(2)
      }
    ],

    wallPosts: [
      {
        id: 7001,
        userId: 9002,
        schoolId: SCHOOL_ID,
        anonName: '匿名同学·037',
        anonAvatar: 3,
        content:
          '图书馆四楼靠窗那个总穿灰色卫衣的男生，你昨天帮我把掉在地上的书捡起来了，一直没来得及说谢谢。',
        images: '',
        likeCount: 24,
        commentCount: 0,
        status: 1,
        createdAt: ago(18)
      },
      {
        id: 7002,
        userId: 9003,
        schoolId: SCHOOL_ID,
        anonName: '路过的同学·512',
        anonAvatar: 1,
        content: '想对在二食堂帮我付了 3 块钱的同学说声谢谢，那天手机没电了，你跑得比我还快。',
        images: '',
        likeCount: 18,
        commentCount: 0,
        status: 1,
        createdAt: ago(72)
      },
      {
        id: 7003,
        userId: 9004,
        schoolId: SCHOOL_ID,
        anonName: '神秘同学·221',
        anonAvatar: 5,
        content: '每周三晚上在操场跑步的女生，你的耳机好像是白色的。我总在你后面第三圈开始喘。',
        images: '',
        likeCount: 31,
        commentCount: 0,
        status: 1,
        createdAt: ago(150)
      },
      {
        id: 7004,
        userId: 9005,
        schoolId: SCHOOL_ID,
        anonName: '一位同学·648',
        anonAvatar: 2,
        content: '期末周了，有没有人一起去自习室占座？我可以带咖啡，你带自制力。',
        images: '',
        likeCount: 9,
        commentCount: 0,
        status: 1,
        createdAt: ago(240)
      },
      {
        id: 7005,
        userId: ME_ID,
        schoolId: SCHOOL_ID,
        anonName: '匿名同学·104',
        anonAvatar: 7,
        content: '今天在实验室通宵，看到窗外天亮了，突然觉得这些好像也没那么难。',
        images: '',
        likeCount: 42,
        commentCount: 0,
        status: 1,
        createdAt: ago(360)
      },
      {
        id: 7006,
        userId: 9006,
        schoolId: SCHOOL_ID,
        anonName: '路过的同学·880',
        anonAvatar: 4,
        content: '喜欢一个人三年，从大一到大三，还是没敢说。祝他考研顺利吧。',
        images: '',
        likeCount: 56,
        commentCount: 0,
        status: 1,
        createdAt: ago(700)
      },
      {
        id: 7007,
        userId: 9002,
        schoolId: SCHOOL_ID,
        anonName: '神秘同学·309',
        anonAvatar: 6,
        content: '室友说我每天笑得很开心，其实我只是不想让气氛尴尬。晚安。',
        images: '',
        likeCount: 27,
        commentCount: 0,
        status: 1,
        createdAt: ago(1100)
      },
      {
        id: 7008,
        userId: 9003,
        schoolId: SCHOOL_ID,
        anonName: '一位同学·466',
        anonAvatar: 8,
        content: '如果你也在紫金港，也喜欢下雨天不去上课，那我们可能见过。',
        images: '',
        likeCount: 12,
        commentCount: 0,
        status: 1,
        createdAt: ago(1600)
      }
    ],

    wallComments: [
      { id: 5001, postId: 7001, userId: 9004, anonName: '一位同学·318', content: '磕到了，蹲一个后续', createdAt: ago(10) },
      { id: 5002, postId: 7001, userId: 9005, anonName: '匿名同学·027', content: '兄弟你倒是说句话啊', createdAt: ago(8) },
      { id: 5003, postId: 7003, userId: ME_ID, anonName: '匿名同学·104', content: '第三圈开始喘也太真实了哈哈', createdAt: ago(120) },
      { id: 5004, postId: 7006, userId: 9002, anonName: '路过的同学·512', content: '勇敢一点吧，毕业就没机会了', createdAt: ago(600) },
      { id: 5005, postId: 7006, userId: 9003, anonName: '神秘同学·221', content: '祝你也能被好好喜欢', createdAt: ago(520) }
    ],

    posts: [
      {
        id: 6001,
        channelId: 2003,
        userId: ME_ID,
        schoolId: SCHOOL_ID,
        authorName: '林小满',
        authorAvatar: '',
        title: '紫金港食堂测评：这 5 个窗口闭眼入',
        content:
          '排名分先后：\n1. 三食堂麻辣香锅，阿姨手不抖，加个蛋更香\n2. 二食堂现烤面包，晚上七点后打折\n3. 一食堂手工水饺，荠菜馅是隐藏菜单\n4. 四食堂铁板饭，记得让他少放酱\n5. 教工食堂的汤，学生也能买\n\n欢迎补充，我想把这个列表做成攻略。',
        images: '',
        viewCount: 455,
        likeCount: 18,
        commentCount: 0,
        status: 1,
        createdAt: ago(30)
      },
      {
        id: 6002,
        channelId: 2002,
        userId: 9002,
        schoolId: SCHOOL_ID,
        authorName: '陈屿',
        authorAvatar: '',
        title: '数分期末复习资料整理（附往年真题）',
        content: '整理了三年的期末真题和错题本，按章节分了文件夹。需要的同学评论区扣 1，我发你网盘链接。',
        images: '',
        viewCount: 1200,
        attachment: { name: '宏观经济学_期末必过高分宝典.pdf', size: '4.8 MB', downloads: 328 },
        likeCount: 64,
        commentCount: 0,
        status: 1,
        createdAt: ago(120)
      },
      {
        id: 6003,
        channelId: 2001,
        userId: 9003,
        schoolId: SCHOOL_ID,
        authorName: '周小舟',
        authorAvatar: '',
        title: '失物招领：图书馆三楼捡到一个耳机盒',
        content: '白色充电盒，上面贴了一只小猫贴纸。放在三楼服务台了，失主可以带校园卡去认领。',
        images: '',
        viewCount: 202,
        likeCount: 7,
        commentCount: 0,
        status: 1,
        createdAt: ago(200)
      },
      {
        id: 6004,
        channelId: 2004,
        userId: 9004,
        schoolId: SCHOOL_ID,
        authorName: '苏念',
        authorAvatar: '',
        title: '凌晨三点的宿舍楼下，有人在弹吉他',
        content: '弹的是《晴天》，跑调得很认真。我没开窗，就坐在床边听完了整首。',
        images: '',
        viewCount: 1076,
        likeCount: 45,
        commentCount: 0,
        status: 1,
        createdAt: ago(320)
      },
      {
        id: 6005,
        channelId: 2006,
        userId: 9005,
        schoolId: SCHOOL_ID,
        authorName: '阿泽',
        authorAvatar: '',
        title: '周末羽毛球，缺两个人，馆已订好',
        content: '周六下午两点，风雨操场 3 号场，已经订了两小时。水平业余就行，主要是想凑够四个人双打。',
        images: '',
        viewCount: 294,
        likeCount: 11,
        commentCount: 0,
        status: 1,
        createdAt: ago(430)
      },
      {
        id: 6006,
        channelId: 2005,
        userId: 9006,
        schoolId: SCHOOL_ID,
        authorName: '李知夏',
        authorAvatar: '',
        title: '搬宿舍出闲置：书架、风扇、收纳箱',
        content: '书架 25，落地扇 40，三个收纳箱打包 30。都在蓝田，能自提最好，价格可以再商量。',
        images: '',
        viewCount: 248,
        likeCount: 9,
        commentCount: 0,
        status: 1,
        createdAt: ago(560)
      },
      {
        id: 6007,
        channelId: 2003,
        userId: ME_ID,
        schoolId: SCHOOL_ID,
        authorName: '林小满',
        authorAvatar: '',
        title: '西区新开的那家咖啡店，学生证打八折',
        content: '手冲意外地不错，座位也安静，适合下午写作业。就是插座只有靠窗那一排有。',
        images: '',
        viewCount: 547,
        likeCount: 22,
        commentCount: 0,
        status: 1,
        createdAt: ago(700)
      },
      {
        id: 6008,
        channelId: 2002,
        userId: 9002,
        schoolId: SCHOOL_ID,
        authorName: '陈屿',
        authorAvatar: '',
        title: '计算机二级备考经验：两周从零到过线',
        content:
          '1. 先把真题刷一遍，知道自己差在哪\n2. Word 和 Excel 的函数部分一定要动手做\n3. 选择题靠刷题量，别死记\n4. 考前三天只看错题\n\n两周每天两小时，够用。',
        images: '',
        viewCount: 915,
        likeCount: 38,
        commentCount: 0,
        status: 1,
        createdAt: ago(900)
      },
      {
        id: 6009,
        channelId: 2001,
        userId: 9003,
        schoolId: SCHOOL_ID,
        authorName: '周小舟',
        authorAvatar: '',
        title: '校车时刻表更新（10 月起执行）',
        content: '紫金港到玉泉的班次调整为每 40 分钟一班，末班车提前到 21:20。具体时刻表贴在候车亭了。',
        images: '',
        viewCount: 386,
        likeCount: 15,
        commentCount: 0,
        status: 1,
        createdAt: ago(1200)
      },
      {
        id: 6010,
        channelId: 2004,
        userId: 9004,
        schoolId: SCHOOL_ID,
        authorName: '苏念',
        authorAvatar: '',
        title: '有没有人跟我一样，越到期末越想整理房间',
        content: '书没看几页，桌面已经擦了三遍，连抽屉都分类收纳好了。',
        images: '',
        viewCount: 1214,
        likeCount: 51,
        commentCount: 0,
        status: 1,
        createdAt: ago(1500)
      },
      {
        id: 6011,
        channelId: 2006,
        userId: 9005,
        schoolId: SCHOOL_ID,
        authorName: '阿泽',
        authorAvatar: '',
        title: '夜跑招募：每晚九点，操场第三道',
        content: '配速 6 分左右，跑 5 公里。想来的直接来，不用报名，看到一群慢跑的就是我们。',
        images: '',
        viewCount: 501,
        likeCount: 20,
        commentCount: 0,
        status: 1,
        createdAt: ago(1800)
      },
      {
        id: 6012,
        channelId: 2003,
        userId: 9006,
        schoolId: SCHOOL_ID,
        authorName: '李知夏',
        authorAvatar: '',
        title: '食堂阿姨今天多给了我一勺肉，谢谢阿姨',
        content: '就这一勺，让我整个下午都挺开心的。',
        images: '',
        viewCount: 1720,
        likeCount: 73,
        commentCount: 0,
        status: 1,
        createdAt: ago(2200)
      }
    ],

    postComments: [
      { id: 5101, postId: 6001, userId: 9002, userName: '陈屿', userAvatar: '', content: '三食堂香锅 +1，记得加宽粉', createdAt: ago(20) },
      { id: 5102, postId: 6001, userId: 9004, userName: '苏念', userAvatar: '', content: '二食堂面包七点后打折是真的，我上次 5 块钱买了三个', createdAt: ago(15) },
      { id: 5103, postId: 6002, userId: 9005, userName: '阿泽', userAvatar: '', content: '1，求链接', createdAt: ago(100) },
      { id: 5104, postId: 6004, userId: 9006, userName: '李知夏', userAvatar: '', content: '这种时刻真的很治愈', createdAt: ago(300) },
      { id: 5105, postId: 6012, userId: ME_ID, userName: '林小满', userAvatar: '', content: '快乐有时候就是这么简单', createdAt: ago(2100) }
    ],

    orders: [
      {
        id: 4001,
        goodsId: 8006,
        buyerId: ME_ID,
        sellerId: 9002,
        buyerName: '林小满',
        message: '显示器还在吗？今晚可以去玉泉看货吗？',
        contact: 'QQ 12345678',
        status: 1,
        createdAt: ago(300)
      },
      {
        id: 4002,
        goodsId: 8015,
        buyerId: 9004,
        sellerId: ME_ID,
        buyerName: '苏念',
        message: '鼠标还在吗？价格能再谈一点吗，我今天下午就能来拿',
        contact: '微信 su_nian_2002',
        status: 0,
        createdAt: ago(60)
      }
    ],

    likes: [
      { targetType: 'GOODS', targetId: 8002, userId: ME_ID },
      { targetType: 'GOODS', targetId: 8006, userId: ME_ID },
      { targetType: 'WALL', targetId: 7001, userId: ME_ID },
      { targetType: 'POST', targetId: 6001, userId: ME_ID }
    ],

    // 私信会话（先做前端展示层，后续接 IM 时字段结构不变）
    convos: [
      {
        id: 3001,
        peerId: 9002,
        peerName: '陈屿',
        peerAvatar: '',
        peerVerified: true,
        peerOnline: true,
        peerTags: [{ text: '卖家', cls: 'mint' }, { text: '已认证', cls: 'plain' }],
        unread: 2,
        // 会话内关联的宝贝卡片（可为空）
        goodsCard: { id: 8001, title: '九成新机械键盘 87 键 青轴', price: 129, emoji: '⌨️', statusText: '议价中' },
        messages: [
          { id: 1, from: 'peer', type: 'text', content: '同学你好！键盘还在的，配件都在，成色很新～', createdAt: ago(95) },
          { id: 2, from: 'me', type: 'text', content: '学长好！请问轴体可以现场试打吗？今晚在紫荆四号楼楼下方便面交吗👀', createdAt: ago(90) },
          { id: 3, from: 'peer', type: 'location', content: '紫荆学生公寓4号楼 · 1层大厅服务台旁', createdAt: ago(85) },
          { id: 4, from: 'peer', type: 'text', content: '可以的呀！今晚 8 点半后我都在寝室，带电脑当场试打，满意再付～', createdAt: ago(80), unread: true }
        ]
      },
      {
        id: 3002,
        peerId: 0,
        peerName: '匿名纸条 #302号',
        peerAvatar: '',
        anon: true,
        peerTags: [{ text: '表白墙回声', cls: 'pink' }],
        unread: 1,
        goodsCard: null,
        messages: [
          { id: 1, from: 'peer', type: 'text', content: '“同学你好！看到你在六教302自习室发的捞人贴，那个经管学长我认识，可以帮你递张纸条～”', createdAt: ago(60 * 27) },
          { id: 2, from: 'me', type: 'text', content: '太感谢啦！麻烦帮我问一下他下周有没有空一起自习😊', createdAt: ago(60 * 26) },
          { id: 3, from: 'peer', type: 'text', content: '他说是计算机学院的，周三下午常在六教，你可以留意一下～', createdAt: ago(60 * 25), unread: true }
        ]
      },
      {
        id: 3003,
        peerId: 9005,
        peerName: '麦麦学长',
        peerAvatar: '',
        peerVerified: false,
        peerOnline: false,
        peerTags: [{ text: '热心课代表', cls: 'mint' }, { text: '经管学院', cls: 'plain' }],
        unread: 0,
        goodsCard: null,
        messages: [
          { id: 1, from: 'me', type: 'text', content: '学长，宏观经济学的期末复习资料方便发我一份吗？🙏', createdAt: ago(60 * 50) },
          { id: 2, from: 'peer', type: 'text', content: '宏观经济学期末真题汇总已经发到你校内邮箱啦，祝高分通过！✨', createdAt: ago(60 * 49), read: true }
        ]
      },
      {
        id: 3004,
        peerId: 9004,
        peerName: '苏念',
        peerAvatar: '',
        peerVerified: false,
        peerOnline: false,
        peerTags: [{ text: '买家', cls: 'orange' }],
        unread: 0,
        goodsCard: { id: 8015, title: '考研英语历年真题黄皮书', price: 35, emoji: '📚', statusText: '校内自提' },
        messages: [
          { id: 1, from: 'peer', type: 'text', content: '学姐今晚8点在紫荆二号楼自提方便吗？带现金还是扫码呢？', createdAt: ago(60 * 72) }
        ]
      },
      {
        id: 3005,
        peerId: 0,
        peerName: '校园趣事小助手',
        peerAvatar: '',
        official: true,
        peerTags: [{ text: '官方认证', cls: 'green' }],
        unread: 0,
        goodsCard: null,
        messages: [
          { id: 1, from: 'peer', type: 'text', content: '【毕业季集市】恭喜！你发布的闲置已被推荐至今日热门集市展位，快去看看吧～', createdAt: ago(60 * 24 * 3) },
          { id: 2, from: 'peer', type: 'text', content: '校园面交防骗提醒：请优先选择宿舍大厅、食堂等明亮公共区域，认准实名校友，谨防私下转账。', createdAt: ago(60 * 24 * 3 - 5) }
        ]
      }
    ],

    // 消息中心快捷入口角标（赞与收藏 / 新增粉丝 / 评论与@ / 官方通知）
    msgSummary: { likes: 18, followers: 2, mentions: 5, notice: '市集' }
  }
}

/* ============================ 存储与登录态 ============================ */

function load() {
  if (db) return db
  let cached = null
  try {
    cached = mp.getStorageSync(DB_KEY)
  } catch (e) {
    cached = null
  }
  db = cached && cached.goods ? cached : seed()
  // 轻量迁移：旧版本地种子没有帖子浏览数/附件字段，补齐以免首页展示缺失
  if (db.posts && db.posts.length && db.posts[0].viewCount === undefined) {
    db.posts.forEach(function (p) {
      p.viewCount = p.id === 6002 ? 1200 : (p.likeCount || 0) * 23 + 41
      if (p.id === 6002 && !p.attachment) {
        p.attachment = { name: '宏观经济学_期末必过高分宝典.pdf', size: '4.8 MB', downloads: 328 }
      }
    })
    save()
  }
  // 轻量迁移：旧版本地种子没有用户资料/关注关系，补齐以免主页与关注功能不可用
  const fresh = seed()
  if (!db.users) {
    db.users = fresh.users
    save()
  }
  if (!db.follows) {
    db.follows = fresh.follows
    save()
  }
  if (!cached || !cached.goods) save()
  return db
}

function save() {
  try {
    mp.setStorageSync(DB_KEY, db)
  } catch (e) {
    // 忽略存储失败（超出配额时不影响本次会话）
  }
}

function reset() {
  db = seed()
  save()
}

function logout() {
  load()
  db.session = false
  save()
}

function nextId() {
  db.seq = (db.seq || 100000) + 1
  return db.seq
}

function profile() {
  return load().profile
}

function requireLogin() {
  if (!db.session) throw { code: 401, msg: '请先登录' }
  return db.profile
}

function requireVerified() {
  const p = requireLogin()
  if (p.verifyStatus !== 2) throw { code: 403, msg: '请先完成校园身份认证后再发布内容' }
  return p
}

/* ============================ 关注关系 / 公开主页 ============================ */

/** 取用户资料：本人以 profile 为准（资料编辑后实时生效），其余查 db.users */
function findUser(id) {
  const uid = Number(id)
  if (uid === db.profile.id) return db.profile
  return (db.users || []).filter(function (u) {
    return u.id === uid
  })[0]
}

function hasFollow(followerId, followeeId) {
  return (db.follows || []).some(function (f) {
    return f.followerId === followerId && f.followeeId === followeeId
  })
}

/** 粉丝数（实时统计，与后端 t_user 计数器语义一致） */
function countFans(userId) {
  return (db.follows || []).filter(function (f) {
    return f.followeeId === userId
  }).length
}

/** 关注数 */
function countFollow(userId) {
  return (db.follows || []).filter(function (f) {
    return f.followerId === userId
  }).length
}

/** 该作者商品数（status 可空 = 全部；已物理删除的不计） */
function countGoodsByUser(userId, status) {
  return db.goods.filter(function (g) {
    return g.userId === userId && (status === undefined || status === null || g.status === status)
  }).length
}

/** 该作者 status=1 帖子数 */
function countPostsByUser(userId) {
  return db.posts.filter(function (p) {
    return p.userId === userId && p.status === 1
  }).length
}

/** 公开主页 VO（未登录可访问；用户缺失或 status=0 → 404） */
function profileVO(id) {
  const uid = Number(id)
  const u = findUser(uid)
  if (!u || u.status === 0) throw { code: 404, msg: '用户不存在或已被封禁' }
  const me = db.session ? db.profile : null
  const isSelf = !!me && me.id === uid
  return {
    id: u.id,
    nickname: u.nickname,
    avatar: u.avatar,
    bio: u.bio || '',
    schoolId: u.schoolId,
    schoolName: u.schoolId ? schoolName(u.schoolId) : '',
    verifyStatus: u.verifyStatus,
    fansCount: countFans(uid),
    followCount: countFollow(uid),
    following: !!me && !isSelf && hasFollow(me.id, uid),
    isSelf: isSelf,
    goodsSaleCount: countGoodsByUser(uid, 1),
    goodsTotalCount: countGoodsByUser(uid, null),
    postCount: countPostsByUser(uid)
  }
}

/** 关注（幂等：已关注不重复计数） */
function doFollow(followeeId) {
  const p = requireLogin()
  const targetId = Number(followeeId)
  if (p.id === targetId) throw { code: 400, msg: '不能关注自己' }
  const u = findUser(targetId)
  if (!u || u.status === 0) throw { code: 404, msg: '对方不存在或已被封禁' }
  if (!hasFollow(p.id, targetId)) {
    db.follows.push({ id: nextId(), followerId: p.id, followeeId: targetId, createdAt: fmt(new Date()) })
    save()
  }
  return { following: true, fansCount: countFans(targetId) }
}

/** 取消关注（幂等：未关注也返回成功） */
function doUnfollow(followeeId) {
  const p = requireLogin()
  const targetId = Number(followeeId)
  if (p.id === targetId) throw { code: 400, msg: '不能关注自己' }
  db.follows = (db.follows || []).filter(function (f) {
    return !(f.followerId === p.id && f.followeeId === targetId)
  })
  save()
  return { following: false, fansCount: countFans(targetId) }
}

/* ============================ 装饰器 ============================ */

const CATEGORY = {
  1: { name: '数码', icon: '📱' },
  2: { name: '图书', icon: '📚' },
  3: { name: '生活', icon: '🧺' },
  4: { name: '服饰', icon: '👕' },
  5: { name: '运动', icon: '🏀' },
  6: { name: '其他', icon: '✨' }
}

const CATEGORIES = Object.keys(CATEGORY).map(function (k) {
  return { id: Number(k), name: CATEGORY[k].name, icon: CATEGORY[k].icon }
})

function splitImages(images) {
  if (!images) return []
  return String(images)
    .split(',')
    .filter(function (x) {
      return !!x
    })
}

function hasLike(type, id) {
  if (!db.session) return false
  return db.likes.some(function (l) {
    return l.targetType === type && l.targetId === Number(id) && l.userId === db.profile.id
  })
}

function decorateGoods(g) {
  const copy = Object.assign({}, g)
  copy.imageList = splitImages(g.images)
  copy.categoryName = CATEGORY[g.categoryId] ? CATEGORY[g.categoryId].name : '其他'
  copy.emoji = CATEGORY[g.categoryId] ? CATEGORY[g.categoryId].icon : '✨'
  copy.price = Number(g.price)
  copy.liked = hasLike('GOODS', g.id)
  copy.supportTrial = g.supportTrial === 0 ? 0 : 1
  return copy
}

function decorateWall(p) {
  const copy = Object.assign({}, p)
  copy.imageList = splitImages(p.images)
  copy.liked = hasLike('WALL', p.id)
  copy.mine = db.session && p.userId === db.profile.id
  delete copy.userId
  return copy
}

function channelOf(id) {
  return db.channels.filter(function (c) {
    return c.id === Number(id)
  })[0]
}

function decoratePost(p) {
  const copy = Object.assign({}, p)
  const channel = channelOf(p.channelId)
  copy.imageList = splitImages(p.images)
  copy.channelName = channel ? channel.name : '校园频道'
  copy.channelIcon = channel ? channel.icon : '💬'
  copy.liked = hasLike('POST', p.id)
  copy.mine = db.session && p.userId === db.profile.id
  return copy
}

/* ============================ 业务实现 ============================ */

function login() {
  load()
  db.session = true
  save()
  const p = db.profile
  return ok({
    token: 'mock-token-' + p.id,
    user: {
      id: p.id,
      nickname: p.nickname,
      avatar: p.avatar,
      schoolId: p.schoolId,
      verifyStatus: p.verifyStatus
    }
  })
}

function schoolName(id) {
  const s = db.schools.filter(function (x) {
    return x.id === Number(id)
  })[0]
  return s ? s.name : ''
}

/** 学校所属区域（省/市/区），与后端 t_school 的区域字段对齐 */
function schoolRegion(id) {
  const s = db.schools.filter(function (x) {
    return x.id === Number(id)
  })[0]
  return s ? { province: s.province || '', city: s.city || '', district: s.district || '' } : { province: '', city: '', district: '' }
}

/**
 * 浏览范围过滤：scope=city 按城市聚合（本市所有高校），
 * 其余情况不过滤（兼容旧调用与自检脚本）。
 */
function filterByScope(list, q) {
  if (!q || q.scope !== 'city') return list
  const city = q.city || schoolRegion(db.profile.schoolId).city
  const ids = db.schools.filter(function (x) {
    return x.city === city
  }).map(function (x) {
    return x.id
  })
  return list.filter(function (p) {
    return ids.indexOf(Number(p.schoolId)) >= 0
  })
}

function maskStudentNo(no) {
  if (!no) return null
  if (no.length <= 4) return '****'
  return '****' + no.slice(no.length - 4)
}

function maskName(name) {
  if (!name) return null
  if (name.length <= 1) return name
  return name.charAt(0) + new Array(name.length).join('*')
}

function meVO() {
  const p = db.profile
  const region = schoolRegion(p.schoolId)
  return {
    id: p.id,
    nickname: p.nickname,
    avatar: p.avatar,
    gender: p.gender,
    phone: p.phone,
    bio: p.bio,
    schoolId: p.schoolId,
    schoolName: p.schoolId ? schoolName(p.schoolId) : '',
    schoolProvince: region.province,
    schoolCity: region.city,
    schoolDistrict: region.district,
    studentNo: maskStudentNo(p.studentNo),
    realName: maskName(p.realName),
    verifyStatus: p.verifyStatus,
    verifyRemark: p.verifyRemark,
    verifyImg: '',
    fansCount: countFans(p.id),
    followCount: countFollow(p.id),
    createdAt: p.createdAt
  }
}

function submitVerify(q) {
  const p = requireLogin()
  if (p.verifyStatus === 2) throw { code: 400, msg: '你已完成身份认证' }
  if (!q.schoolId) throw { code: 400, msg: '请选择所在学校' }
  if (!/^[A-Za-z0-9]{4,20}$/.test(q.studentNo || '')) throw { code: 400, msg: '学号格式不正确' }
  if (!q.realName || q.realName.length < 2) throw { code: 400, msg: '请填写真实姓名' }
  if (!q.verifyImg) throw { code: 400, msg: '请上传学生证/校园卡照片' }

  p.schoolId = Number(q.schoolId)
  p.studentNo = q.studentNo
  p.realName = q.realName
  p.verifyStatus = 2
  p.verifyRemark = ''
  save()
  return ok({ verifyStatus: 2, token: 'mock-token-' + p.id, message: '认证成功，欢迎来到校园趣事' })
}

function queryGoods(q) {
  let list
  if (q.userId) {
    // 个人主页场景：该作者全部状态商品（忽略学校范围与默认在售过滤），与后端 userId 分支一致
    list = db.goods.filter(function (g) {
      return g.userId === Number(q.userId)
    })
  } else {
    list = db.goods.filter(function (g) {
      return g.status === 1
    })
    if (q.schoolId) {
      list = list.filter(function (g) {
        return g.schoolId === Number(q.schoolId)
      })
    }
  }
  if (q.categoryId) {
    list = list.filter(function (g) {
      return g.categoryId === Number(q.categoryId)
    })
  }
  if (q.keyword) {
    const kw = String(q.keyword).toLowerCase()
    list = list.filter(function (g) {
      return (g.title + ' ' + (g.description || '') + ' ' + (g.location || '')).toLowerCase().indexOf(kw) >= 0
    })
  }
  const sort = q.sort || 'latest'
  if (sort === 'price_asc') {
    list.sort(function (a, b) {
      return a.price - b.price
    })
  } else if (sort === 'price_desc') {
    list.sort(function (a, b) {
      return b.price - a.price
    })
  } else if (sort === 'hot') {
    list.sort(function (a, b) {
      return b.viewCount - a.viewCount
    })
  } else {
    list.sort(byTimeDesc)
  }
  return paginate(list.map(decorateGoods), q)
}

function goodsDetail(id) {
  const g = db.goods.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!g) throw { code: 404, msg: '商品不存在或已被删除' }
  g.viewCount = (g.viewCount || 0) + 1
  save()
  const copy = decorateGoods(g)
  copy.sellerSaleCount = db.goods.filter(function (x) {
    return x.userId === g.userId && x.status === 1
  }).length
  copy.sellerTotalCount = db.goods.filter(function (x) {
    return x.userId === g.userId
  }).length
  return copy
}

function createGoods(q) {
  const p = requireVerified()
  if (!q.title || !String(q.title).trim()) throw { code: 400, msg: '请填写商品标题' }
  if (!q.price || Number(q.price) <= 0) throw { code: 400, msg: '请填写价格' }
  const images = (q.images || []).slice(0, 9).join(',')
  const g = {
    id: nextId(),
    userId: p.id,
    schoolId: p.schoolId || SCHOOL_ID,
    sellerName: p.nickname,
    sellerAvatar: p.avatar,
    title: String(q.title).trim(),
    description: q.description || '',
    price: Number(q.price),
    originalPrice: q.originalPrice ? Number(q.originalPrice) : null,
    categoryId: Number(q.categoryId) || 6,
    conditionLevel: q.conditionLevel ? Number(q.conditionLevel) : null,
    purchaseTime: q.purchaseTime || '',
    usageFrequency: q.usageFrequency || '',
    source: q.source || '',
    warranty: q.warranty || '',
    supportTrial: q.supportTrial === 0 ? 0 : 1,
    images: images,
    tradeType: Number(q.tradeType) || 1,
    location: q.location || '',
    status: 1,
    viewCount: 0,
    likeCount: 0,
    createdAt: fmt(new Date())
  }
  db.goods.unshift(g)
  save()
  return g.id
}

function ownGoods(id) {
  const g = db.goods.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!g) throw { code: 404, msg: '商品不存在' }
  if (g.userId !== db.profile.id) throw { code: 403, msg: '只能操作自己发布的商品' }
  return g
}

function updateGoods(id, q) {
  requireLogin()
  const g = ownGoods(id)
  if (g.status === 2) throw { code: 400, msg: '已售出的商品不能修改' }
  if (q.title !== undefined) g.title = q.title
  if (q.description !== undefined) g.description = q.description
  if (q.price !== undefined) g.price = Number(q.price)
  if (q.originalPrice !== undefined) g.originalPrice = q.originalPrice ? Number(q.originalPrice) : null
  if (q.categoryId !== undefined) g.categoryId = Number(q.categoryId)
  if (q.conditionLevel !== undefined) g.conditionLevel = q.conditionLevel ? Number(q.conditionLevel) : null
  if (q.purchaseTime !== undefined) g.purchaseTime = q.purchaseTime || ''
  if (q.usageFrequency !== undefined) g.usageFrequency = q.usageFrequency || ''
  if (q.source !== undefined) g.source = q.source || ''
  if (q.warranty !== undefined) g.warranty = q.warranty || ''
  if (q.supportTrial !== undefined) g.supportTrial = q.supportTrial === 0 ? 0 : 1
  if (q.images !== undefined) g.images = (q.images || []).join(',')
  if (q.tradeType !== undefined) g.tradeType = Number(q.tradeType)
  if (q.location !== undefined) g.location = q.location
  save()
  return null
}

function changeGoodsStatus(id, status) {
  requireLogin()
  const g = ownGoods(id)
  g.status = Number(status)
  save()
  return null
}

function deleteGoods(id) {
  requireLogin()
  ownGoods(id)
  db.goods = db.goods.filter(function (x) {
    return x.id !== Number(id)
  })
  db.likes = db.likes.filter(function (l) {
    return !(l.targetType === 'GOODS' && l.targetId === Number(id))
  })
  save()
  return null
}

function myGoods(q) {
  const p = requireLogin()
  const list = db.goods.filter(function (g) {
    if (g.userId !== p.id) return false
    if (q.status !== undefined && q.status !== '' && q.status !== null) {
      return g.status === Number(q.status)
    }
    return true
  })
  list.sort(byTimeDesc)
  return list.map(decorateGoods)
}

function myFavorites() {
  const p = requireLogin()
  const ids = db.likes
    .filter(function (l) {
      return l.targetType === 'GOODS' && l.userId === p.id
    })
    .map(function (l) {
      return l.targetId
    })
  const list = db.goods.filter(function (g) {
    return ids.indexOf(g.id) >= 0
  })
  return list.map(decorateGoods)
}

function createOrder(goodsId, q) {
  const p = requireVerified()
  if (!q.contact || !String(q.contact).trim()) throw { code: 400, msg: '请留下联系方式，方便卖家联系你' }
  const g = db.goods.filter(function (x) {
    return x.id === Number(goodsId)
  })[0]
  if (!g) throw { code: 404, msg: '商品不存在' }
  if (g.userId === p.id) throw { code: 400, msg: '不能购买自己发布的商品' }
  if (g.status !== 1) throw { code: 400, msg: '商品已下架或已售出' }
  const dup = db.orders.some(function (o) {
    return o.goodsId === g.id && o.buyerId === p.id && (o.status === 0 || o.status === 1)
  })
  if (dup) throw { code: 400, msg: '你已提交过购买意向，请等待卖家联系' }

  const order = {
    id: nextId(),
    goodsId: g.id,
    buyerId: p.id,
    sellerId: g.userId,
    buyerName: p.nickname,
    message: q.message || '',
    contact: String(q.contact).trim(),
    status: 0,
    createdAt: fmt(new Date())
  }
  db.orders.unshift(order)
  save()
  return order.id
}

function myOrders(role) {
  const p = requireLogin()
  const isSeller = role === 'seller'
  const list = db.orders
    .filter(function (o) {
      return isSeller ? o.sellerId === p.id : o.buyerId === p.id
    })
    .map(function (o) {
      const g = db.goods.filter(function (x) {
        return x.id === o.goodsId
      })[0]
      return Object.assign({}, o, { goodsTitle: g ? g.title : '商品已删除' })
    })
  list.sort(byTimeDesc)
  return list
}

/* ---------- 表白墙 ---------- */

function queryWall(q) {
  const list = filterByScope(db.wallPosts, q).filter(function (p) {
    return p.status === 1
  })
  if (q.sort === 'hot') {
    list.sort(function (a, b) {
      return (b.likeCount || 0) - (a.likeCount || 0)
    })
  } else {
    list.sort(byTimeDesc)
  }
  return paginate(list.map(decorateWall), q)
}

function wallDetail(id) {
  const p = db.wallPosts.filter(function (x) {
    return x.id === Number(id) && x.status === 1
  })[0]
  if (!p) throw { code: 404, msg: '这条表白不见了' }
  return decorateWall(p)
}

function anonName(seq) {
  const prefixes = ['匿名同学', '路过的同学', '一位同学', '神秘同学']
  return prefixes[seq % prefixes.length] + '·' + String(100 + (seq % 900))
}

function createWall(q) {
  const p = requireVerified()
  if (!q.content || !String(q.content).trim()) throw { code: 400, msg: '说点什么吧' }
  const id = nextId()
  const post = {
    id: id,
    userId: p.id,
    schoolId: p.schoolId || SCHOOL_ID,
    anonName: anonName(id),
    anonAvatar: (id % 8) + 1,
    content: String(q.content).trim(),
    images: (q.images || []).slice(0, 3).join(','),
    likeCount: 0,
    commentCount: 0,
    status: 1,
    createdAt: fmt(new Date())
  }
  db.wallPosts.unshift(post)
  save()
  return post.id
}

/** 编辑自己的表白（匿名昵称保持不变） */
function updateWall(id, q) {
  const p = requireVerified()
  const post = db.wallPosts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post || post.status !== 1) throw { code: 404, msg: '这条表白不见了' }
  if (post.userId !== p.id) throw { code: 403, msg: '只能编辑自己发布的内容' }
  if (!q.content || !String(q.content).trim()) throw { code: 400, msg: '说点什么吧' }
  post.content = String(q.content).trim()
  post.images = (q.images || []).slice(0, 3).join(',')
  save()
  return null
}

function deleteWall(id) {
  const p = requireLogin()
  const post = db.wallPosts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post) throw { code: 404, msg: '这条表白不见了' }
  if (post.userId !== p.id) throw { code: 403, msg: '只能删除自己发布的内容' }
  post.status = 0
  save()
  return null
}

function wallComments(id) {
  const p = db.wallPosts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!p) throw { code: 404, msg: '这条表白不见了' }
  const all = db.wallComments
    .filter(function (c) {
      return c.postId === Number(id)
    })
    .sort(function (a, b) {
      return a.createdAt < b.createdAt ? -1 : 1
    })
  return all
    .filter(function (c) {
      return !c.parentId
    })
    .map(function (c) {
      const replies = all
        .filter(function (r) {
          return r.parentId === c.id
        })
        .map(function (r) {
          const rc = Object.assign({}, r)
          rc.mine = db.session && r.userId === db.profile.id
          rc.imageList = splitImages(r.images)
          delete rc.userId
          return rc
        })
      const copy = Object.assign({}, c)
      copy.mine = db.session && c.userId === db.profile.id
      copy.imageList = splitImages(c.images)
      copy.replies = replies
      copy.replyCount = replies.length
      delete copy.userId
      return copy
    })
}

function addWallComment(id, q) {
  const p = requireVerified()
  const post = db.wallPosts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post || post.status !== 1) throw { code: 404, msg: '这条表白不见了' }

  const content = q.content ? String(q.content).trim() : ''
  const images = (q.images || []).slice(0, 3).join(',')
  // 支持「只发图不写字」
  if (!content && !images) throw { code: 400, msg: '说点什么，或者配张图吧' }

  const comment = {
    id: nextId(),
    postId: post.id,
    userId: p.id,
    anonName: anonName(post.id + db.wallComments.length * 7 + 13),
    content: content,
    images: images,
    parentId: Number(q.parentId) || 0,
    replyToName: null,
    createdAt: fmt(new Date())
  }
  if (comment.parentId > 0) {
    const parent = db.wallComments.filter(function (c) {
      return c.id === comment.parentId && c.postId === post.id
    })[0]
    if (!parent) throw { code: 400, msg: '要回复的评论不存在' }
    comment.replyToName = parent.anonName || '匿名同学'
  }
  db.wallComments.push(comment)
  post.commentCount = (post.commentCount || 0) + 1
  save()
  return comment.id
}

/** 删除自己的评论 */
function deleteWallComment(postId, commentId) {
  const p = requireLogin()
  const comment = db.wallComments.filter(function (c) {
    return c.id === Number(commentId) && c.postId === Number(postId)
  })[0]
  if (!comment) throw { code: 404, msg: '评论不存在或已删除' }
  if (comment.userId !== p.id) throw { code: 403, msg: '只能删除自己的评论' }

  const parentId = comment.parentId || 0
  let removed = 0
  if (parentId === 0) {
    const replies = db.wallComments.filter(function (c) {
      return c.parentId === comment.id
    })
    removed = replies.length
    db.wallComments = db.wallComments.filter(function (c) {
      return c.parentId !== comment.id
    })
  }
  db.wallComments = db.wallComments.filter(function (c) {
    return c.id !== comment.id
  })
  const post = db.wallPosts.filter(function (x) {
    return x.id === Number(postId)
  })[0]
  if (post) post.commentCount = Math.max(0, (post.commentCount || 0) - 1 - removed)
  save()
  return null
}

/* ---------- 频道 / 帖子 ---------- */

function channels() {
  load()
  return db.channels
    .slice()
    .sort(function (a, b) {
      return b.sort - a.sort
    })
    .map(function (c) {
      return Object.assign({}, c, {
        postCount: db.posts.filter(function (p) {
          return p.channelId === c.id && p.status === 1
        }).length
      })
    })
}

function queryPosts(q) {
  let list
  if (q.userId) {
    // 个人主页场景：该作者 status=1 帖子（忽略学校/频道范围），与后端 userId 分支一致
    list = db.posts.filter(function (p) {
      return p.userId === Number(q.userId) && p.status === 1
    })
  } else {
    list = filterByScope(db.posts, q).filter(function (p) {
      return p.status === 1
    })
    if (q.channelId) {
      list = list.filter(function (p) {
        return p.channelId === Number(q.channelId)
      })
    }
  }
  // sort=hot：按点赞倒序（首页最热帖子）
  list.sort(q.sort === 'hot' ? byLikeDesc : byTimeDesc)
  return paginate(list.map(decoratePost), q)
}

function postDetail(id) {
  const p = db.posts.filter(function (x) {
    return x.id === Number(id) && x.status === 1
  })[0]
  if (!p) throw { code: 404, msg: '帖子不存在或已删除' }
  return decoratePost(p)
}

function createPost(q) {
  const p = requireVerified()
  if (!q.channelId) throw { code: 400, msg: '请选择频道' }
  if (!q.title || !String(q.title).trim()) throw { code: 400, msg: '请填写标题' }
  if (!q.content || !String(q.content).trim()) throw { code: 400, msg: '正文不能为空' }
  const channel = channelOf(q.channelId)
  if (!channel) throw { code: 400, msg: '频道不存在' }
  const id = nextId()
  const post = {
    id: id,
    channelId: channel.id,
    userId: p.id,
    schoolId: p.schoolId || SCHOOL_ID,
    authorName: p.nickname,
    authorAvatar: p.avatar,
    title: String(q.title).trim(),
    content: String(q.content).trim(),
    images: (q.images || []).slice(0, 9).join(','),
    likeCount: 0,
    commentCount: 0,
    status: 1,
    createdAt: fmt(new Date())
  }
  db.posts.unshift(post)
  save()
  return post.id
}

/** 编辑自己的帖子 */
function updatePost(id, q) {
  const p = requireVerified()
  const post = db.posts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post || post.status !== 1) throw { code: 404, msg: '帖子不存在或已删除' }
  if (post.userId !== p.id) throw { code: 403, msg: '只能编辑自己发布的帖子' }

  const channel = channelOf(q.channelId)
  if (!channel) throw { code: 400, msg: '频道不存在' }
  if (!q.title || !String(q.title).trim()) throw { code: 400, msg: '请填写标题' }
  if (!q.content || !String(q.content).trim()) throw { code: 400, msg: '正文不能为空' }

  post.channelId = channel.id
  post.title = String(q.title).trim()
  post.content = String(q.content).trim()
  post.images = (q.images || []).slice(0, 9).join(',')
  save()
  return null
}

function deletePost(id) {
  const p = requireLogin()
  const post = db.posts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post) throw { code: 404, msg: '帖子不存在' }
  if (post.userId !== p.id) throw { code: 403, msg: '只能删除自己发布的帖子' }
  post.status = 0
  save()
  return null
}

function postComments(id) {
  db.postComments = db.postComments || []
  const all = db.postComments
    .filter(function (c) {
      return c.postId === Number(id)
    })
    .sort(function (a, b) {
      return a.createdAt < b.createdAt ? -1 : 1
    })
  return all
    .filter(function (c) {
      return !c.parentId
    })
    .map(function (c) {
      const replies = all
        .filter(function (r) {
          return r.parentId === c.id
        })
        .map(function (r) {
          const rc = Object.assign({}, r)
          rc.mine = db.session && r.userId === db.profile.id
          rc.imageList = r.images ? String(r.images).split(',').filter(Boolean) : []
          delete rc.userId
          return rc
        })
      const copy = Object.assign({}, c)
      copy.mine = db.session && c.userId === db.profile.id
      copy.imageList = c.images ? String(c.images).split(',').filter(Boolean) : []
      copy.replies = replies
      copy.replyCount = replies.length
      delete copy.userId
      return copy
    })
}

function addPostComment(id, q) {
  const p = requireVerified()
  const post = db.posts.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!post || post.status !== 1) throw { code: 404, msg: '帖子不存在' }

  const content = q.content ? String(q.content).trim() : ''
  const images = (q.images || []).slice(0, 3).join(',')
  if (!content && !images) throw { code: 400, msg: '说点什么，或者配张图吧' }

  const parentId = Number(q.parentId) || 0
  let replyToName = null
  if (parentId > 0) {
    const parent = db.postComments.filter(function (c) {
      return c.id === parentId && c.postId === post.id
    })[0]
    if (!parent) throw { code: 400, msg: '要回复的评论不存在' }
    replyToName = parent.userName || '校园用户'
  }

  const comment = {
    id: nextId(),
    postId: post.id,
    userId: p.id,
    userName: p.nickname,
    userAvatar: p.avatar,
    content: content,
    images: images,
    imageList: images ? images.split(',') : [],
    parentId: parentId,
    replyToName: replyToName,
    createdAt: fmt(new Date())
  }
  db.postComments.push(comment)
  post.commentCount = (post.commentCount || 0) + 1
  save()
  return comment.id
}

/** 删除自己的帖子评论（删顶层评论时级联删除其回复） */
function deletePostComment(postId, commentId) {
  const p = requireLogin()
  const comment = db.postComments.filter(function (c) {
    return c.id === Number(commentId) && c.postId === Number(postId)
  })[0]
  if (!comment) throw { code: 404, msg: '评论不存在或已删除' }
  if (comment.userId !== p.id) throw { code: 403, msg: '只能删除自己的评论' }

  const parentId = comment.parentId || 0
  let removed = 0
  if (parentId === 0) {
    // 级联删除该顶层评论下的全部回复
    const replies = db.postComments.filter(function (c) {
      return c.parentId === comment.id
    })
    removed = replies.length
    db.postComments = db.postComments.filter(function (c) {
      return c.parentId !== comment.id
    })
  }
  db.postComments = db.postComments.filter(function (c) {
    return c.id !== comment.id
  })
  const post = db.posts.filter(function (x) {
    return x.id === Number(postId)
  })[0]
  if (post) post.commentCount = Math.max(0, (post.commentCount || 0) - 1 - removed)
  save()
  return null
}

/* ---------- 商品评论（含楼中楼回复，结构与帖子评论一致） ---------- */

function goodsComments(id) {
  const g = db.goods.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!g) throw { code: 404, msg: '商品不存在或已下架' }
  db.goodsComments = db.goodsComments || []
  const all = db.goodsComments
    .filter(function (c) {
      return c.goodsId === Number(id)
    })
    .sort(function (a, b) {
      return a.createdAt < b.createdAt ? -1 : 1
    })
  return all
    .filter(function (c) {
      return !c.parentId
    })
    .map(function (c) {
      const replies = all
        .filter(function (r) {
          return r.parentId === c.id
        })
        .map(function (r) {
          const rc = Object.assign({}, r)
          rc.mine = db.session && r.userId === db.profile.id
          rc.imageList = r.images ? String(r.images).split(',').filter(Boolean) : []
          delete rc.userId
          return rc
        })
      const copy = Object.assign({}, c)
      copy.mine = db.session && c.userId === db.profile.id
      copy.imageList = c.images ? String(c.images).split(',').filter(Boolean) : []
      copy.replies = replies
      copy.replyCount = replies.length
      delete copy.userId
      return copy
    })
}

function addGoodsComment(id, q) {
  const p = requireVerified()
  const goods = db.goods.filter(function (x) {
    return x.id === Number(id)
  })[0]
  if (!goods || goods.status !== 1) throw { code: 404, msg: '商品不存在或已下架' }

  const content = q.content ? String(q.content).trim() : ''
  const images = (q.images || []).slice(0, 3).join(',')
  if (!content && !images) throw { code: 400, msg: '说点什么，或者配张图吧' }

  db.goodsComments = db.goodsComments || []
  const parentId = Number(q.parentId) || 0
  let replyToName = null
  if (parentId > 0) {
    const parent = db.goodsComments.filter(function (c) {
      return c.id === parentId && c.goodsId === goods.id
    })[0]
    if (!parent) throw { code: 400, msg: '要回复的评论不存在' }
    replyToName = parent.userName || '校园用户'
  }

  const comment = {
    id: nextId(),
    goodsId: goods.id,
    userId: p.id,
    userName: p.nickname,
    userAvatar: p.avatar,
    content: content,
    images: images,
    imageList: images ? images.split(',') : [],
    parentId: parentId,
    replyToName: replyToName,
    createdAt: fmt(new Date())
  }
  db.goodsComments.push(comment)
  goods.commentCount = (goods.commentCount || 0) + 1
  save()
  return comment.id
}

/** 删除自己的商品评论（删顶层评论时级联删除其回复） */
function deleteGoodsComment(goodsId, commentId) {
  const p = requireLogin()
  db.goodsComments = db.goodsComments || []
  const comment = db.goodsComments.filter(function (c) {
    return c.id === Number(commentId) && c.goodsId === Number(goodsId)
  })[0]
  if (!comment) throw { code: 404, msg: '评论不存在或已删除' }
  if (comment.userId !== p.id) throw { code: 403, msg: '只能删除自己的评论' }

  const parentId = comment.parentId || 0
  let removed = 0
  if (parentId === 0) {
    const replies = db.goodsComments.filter(function (c) {
      return c.parentId === comment.id
    })
    removed = replies.length
    db.goodsComments = db.goodsComments.filter(function (c) {
      return c.parentId !== comment.id
    })
  }
  db.goodsComments = db.goodsComments.filter(function (c) {
    return c.id !== comment.id
  })
  const goods = db.goods.filter(function (x) {
    return x.id === Number(goodsId)
  })[0]
  if (goods) goods.commentCount = Math.max(0, (goods.commentCount || 0) - 1 - removed)
  save()
  return null
}

/* ---------- 通用点赞 ---------- */

function toggleLike(type, id) {
  const p = requireLogin()
  const targetId = Number(id)
  let target = null
  if (type === 'GOODS') {
    target = db.goods.filter(function (x) {
      return x.id === targetId
    })[0]
  } else if (type === 'WALL') {
    target = db.wallPosts.filter(function (x) {
      return x.id === targetId
    })[0]
  } else if (type === 'POST') {
    target = db.posts.filter(function (x) {
      return x.id === targetId
    })[0]
  }
  if (!target) throw { code: 404, msg: '内容不存在' }

  const idx = db.likes.findIndex(function (l) {
    return l.targetType === type && l.targetId === targetId && l.userId === p.id
  })
  if (idx >= 0) {
    db.likes.splice(idx, 1)
    target.likeCount = Math.max(0, (target.likeCount || 0) - 1)
    save()
    return false
  }
  db.likes.push({ targetType: type, targetId: targetId, userId: p.id })
  target.likeCount = (target.likeCount || 0) + 1
  save()
  return true
}

/* ============================ 路由分发 ============================ */

function route(method, url, data) {
  const raw = String(url || '')
  const qIdx = raw.indexOf('?')
  const path = qIdx >= 0 ? raw.substring(0, qIdx) : raw
  // 查询参数：URL 上的 query 与 data 合并（data 优先），
  // 兼容「/user/follow/status?userId=9002」与「http.get(url, {userId})」两种调用姿势
  let q = data || {}
  if (qIdx >= 0) {
    const params = {}
    raw
      .substring(qIdx + 1)
      .split('&')
      .forEach(function (kv) {
        if (!kv) return
        const pair = kv.split('=')
        const k = decodeURIComponent(pair[0])
        const v = pair.length > 1 ? decodeURIComponent(pair.slice(1).join('=')) : ''
        if (k) params[k] = v
      })
    q = Object.assign({}, params, q)
  }
  const s = path.split('/').filter(Boolean)
  const M = String(method || 'GET').toUpperCase()
  load()

  // 登录 / 学校 / 位置
  if (s[0] === 'auth' && s[1] === 'login' && M === 'POST') return login(q)
  if (s[0] === 'auth' && s[1] === 'geo' && s[2] === 'city' && M === 'GET') {
    // 位置信息 API 的模拟：返回演示账号所在城市（真实模式走后端逆地理）
    return ok({ province: '浙江', city: '杭州', district: '西湖区', available: true })
  }
  if (s[0] === 'schools' && M === 'GET') {
    const kw = q.keyword ? String(q.keyword) : ''
    const list = db.schools.filter(function (x) {
      return !kw || x.name.indexOf(kw) >= 0 || x.city.indexOf(kw) >= 0
    })
    return ok(list)
  }

  // 用户
  if (s[0] === 'user') {
    if (s[1] === 'me' && M === 'GET') {
      requireLogin()
      return ok(meVO())
    }
    if (s[1] === 'me' && M === 'PUT') {
      const p = requireLogin()
      if (q.nickname) p.nickname = q.nickname
      if (q.avatar) p.avatar = q.avatar
      if (q.gender !== undefined) p.gender = q.gender
      if (q.phone !== undefined) p.phone = q.phone
      if (q.bio !== undefined) p.bio = q.bio
      save()
      return ok(meVO())
    }
    if (s[1] === 'verify' && M === 'POST') return submitVerify(q)
    // 关注状态（公开；未登录 following=false，不报 401）。
    // 与后端契约一致：data 为布尔值（R<Boolean>），true = 已关注
    if (s[1] === 'follow' && s[2] === 'status' && M === 'GET') {
      const targetId = Number(q.userId)
      const me = db.session ? db.profile : null
      return ok(!!me && !isNaN(targetId) && hasFollow(me.id, targetId))
    }
    // 关注 / 取消关注（需登录）
    if (s[1] === 'follow' && s.length === 3 && M === 'POST') return ok(doFollow(s[2]))
    if (s[1] === 'follow' && s.length === 3 && M === 'DELETE') return ok(doUnfollow(s[2]))
    // 公开主页（未登录可访问；用户缺失或 status=0 → 404）
    if (s.length === 3 && s[2] === 'profile' && M === 'GET') return ok(profileVO(s[1]))
  }

  // 二手商品
  if (s[0] === 'goods') {
    if (s.length === 1 && M === 'GET') return ok(queryGoods(q))
    if (s.length === 1 && M === 'POST') return ok(createGoods(q))
    if (s[1] === 'categories' && M === 'GET') return ok(CATEGORIES)
    if (s[1] === 'mine' && M === 'GET') return ok(myGoods(q))
    if (s[1] === 'favorites' && M === 'GET') return ok(myFavorites())
    if (s[1] === 'orders' && s[2] === 'mine' && M === 'GET') return ok(myOrders(q.role))
    if (s.length === 2 && M === 'GET') return ok(goodsDetail(s[1]))
    if (s.length === 2 && M === 'POST') return ok(createGoods(q))
    if (s.length === 2 && M === 'PUT') return ok(updateGoods(s[1], q))
    if (s.length === 2 && M === 'DELETE') return ok(deleteGoods(s[1]))
    if (s[2] === 'status' && M === 'POST') return ok(changeGoodsStatus(s[1], q.status))
    if (s[2] === 'like' && M === 'POST') return ok(toggleLike('GOODS', s[1]))
    if (s[2] === 'order' && M === 'POST') return ok(createOrder(s[1], q))
    if (s.length === 3 && s[2] === 'comments' && M === 'GET') return ok(goodsComments(s[1]))
    if (s.length === 3 && s[2] === 'comments' && M === 'POST') return ok(addGoodsComment(s[1], q))
    if (s.length === 4 && s[2] === 'comments' && M === 'DELETE') return ok(deleteGoodsComment(s[1], s[3]))
  }

  // 表白墙
  if (s[0] === 'wall' && s[1] === 'posts') {
    if (s.length === 2 && M === 'GET') return ok(queryWall(q))
    if (s.length === 2 && M === 'POST') return ok(createWall(q))
    if (s.length === 3 && M === 'GET') return ok(wallDetail(s[2]))
    if (s.length === 3 && M === 'PUT') return ok(updateWall(s[2], q))
    if (s.length === 3 && M === 'DELETE') return ok(deleteWall(s[2]))
    if (s.length === 4 && s[3] === 'like' && M === 'POST') return ok(toggleLike('WALL', s[2]))
    if (s.length === 4 && s[3] === 'comments' && M === 'GET') return ok(wallComments(s[2]))
    if (s.length === 4 && s[3] === 'comments' && M === 'POST') return ok(addWallComment(s[2], q))
    if (s.length === 5 && s[3] === 'comments' && M === 'DELETE') return ok(deleteWallComment(s[2], s[4]))
  }

  // 频道 / 帖子
  if (s[0] === 'channels' && M === 'GET') return ok(channels())
  if (s[0] === 'posts') {
    if (s.length === 1 && M === 'GET') return ok(queryPosts(q))
    if (s.length === 1 && M === 'POST') return ok(createPost(q))
    if (s.length === 2 && M === 'GET') return ok(postDetail(s[1]))
    if (s.length === 2 && M === 'PUT') return ok(updatePost(s[1], q))
    if (s.length === 2 && M === 'DELETE') return ok(deletePost(s[1]))
    if (s[2] === 'like' && M === 'POST') return ok(toggleLike('POST', s[1]))
    if (s.length === 3 && s[2] === 'comments' && M === 'GET') return ok(postComments(s[1]))
    if (s.length === 3 && s[2] === 'comments' && M === 'POST') return ok(addPostComment(s[1], q))
    if (s.length === 4 && s[2] === 'comments' && M === 'DELETE') return ok(deletePostComment(s[1], s[3]))
  }

  // 私信与消息中心（前端演示层）
  if (s[0] === 'messages') {
    if (s.length === 2 && M === 'GET' && s[1] === 'unread') return ok({ unread: unreadTotal() })
    if (s.length === 2 && M === 'POST' && s[1] === 'start') return ok(startConvo(q))
    if (s.length === 3 && M === 'POST' && s[2] === 'read') return ok(convoRead(s[1]))
    if (s.length === 3 && M === 'GET' && s[2] === 'messages') {
      const raw = (convoRaw(s[1]) || {}).messages || []
      return ok({ messages: raw.filter(function (m) { return !m.deleted }) })
    }
    if (s.length === 4 && M === 'DELETE' && s[2] === 'messages') return ok(deleteMessage(s[1], s[3]))
    if (s.length === 1 && M === 'GET') return ok(messageList())
    if (s.length === 2 && M === 'GET') return ok(convoDetail(s[1]))
    if (s.length === 2 && M === 'POST') return ok(sendMessage(s[1], q))
    if (s.length === 2 && M === 'DELETE') return ok(deleteConvo(s[1]))
  }

  return err(404, '模拟数据未覆盖该接口：' + M + ' ' + path)
}

/* ============================ 私信与消息中心 ============================ */

/** 会话列表（含快捷入口角标），按最新消息时间倒序 */
function messageList() {
  const d = load()
  const list = (d.convos || []).filter(function (c) {
    // 已隐藏（左滑删除）的会话不再出现在列表里
    return !c.hidden
  }).map(function (c) {
    const msgs = (c.messages || []).filter(function (m) { return !m.deleted })
    const last = msgs.length ? msgs[msgs.length - 1] : null
    return {
      id: c.id,
      peerName: c.peerName,
      peerAvatar: c.peerAvatar,
      anon: !!c.anon,
      official: !!c.official,
      peerOnline: !!c.peerOnline,
      peerVerified: !!c.peerVerified,
      peerTags: c.peerTags || [],
      unread: c.unread || 0,
      goodsCard: c.goodsCard || null,
      lastText: last ? last.content : '',
      lastFrom: last ? last.from : '',
      lastTime: last ? last.createdAt : ''
    }
  }).sort(function (a, b) {
    return a.lastTime < b.lastTime ? 1 : -1
  })
  return { summary: d.msgSummary || { likes: 0, followers: 0, mentions: 0, notice: '' }, convos: list }
}

/**
 * 左滑删除会话：只隐藏自己这一侧的会话（hidden 标记），
 * 与后端「会话隐藏表」语义一致——对方不受影响，消息本体不删。
 */
function deleteConvo(id) {
  requireLogin()
  const d = load()
  const c = convoRaw(id)
  if (!c) throw { code: 404, msg: '会话不存在或已删除' }
  c.hidden = true
  c.unread = 0
  save()
  return null
}

/**
 * 左滑撤回消息：仅「我发的」可撤，且撤回后从双方记录中消失。
 * 与后端软删除（deleted=1）语义一致。
 */
function deleteMessage(convoId, msgId) {
  requireLogin()
  const d = load()
  const c = convoRaw(convoId)
  if (!c) throw { code: 404, msg: '会话不存在或已删除' }
  const msgs = c.messages || []
  let target = null
  for (let i = 0; i < msgs.length; i++) {
    if (String(msgs[i].id) === String(msgId)) { target = msgs[i]; break }
  }
  if (!target) throw { code: 404, msg: '消息不存在' }
  if (target.deleted) throw { code: 400, msg: '该消息已删除' }
  if (target.from !== 'me') throw { code: 403, msg: '只能删除自己发送的消息' }
  target.deleted = true
  save()
  return null
}

/** 取原始会话对象（不清零未读） */
function convoRaw(id) {
  const d = load()
  return (d.convos || []).filter(function (x) {
    return String(x.id) === String(id)
  })[0]
}

/** 未读总数 */
function unreadTotal() {
  const d = load()
  return (d.convos || []).reduce(function (n, c) {
    return n + (c.unread || 0)
  }, 0)
}

/** 标记单个会话已读 */
function convoRead(id) {
  const c = convoRaw(id)
  if (c) {
    c.unread = 0
    ;(c.messages || []).forEach(function (m) { m.unread = false; m.read = true })
    save()
  }
  return null
}

/**
 * 发起（或复用）会话：按 peerId + goodsId 匹配已有会话，没有就新建。
 * 与后端 POST /messages/start 行为保持一致。
 */
function startConvo(q) {
  const d = load()
  const peerId = q.peerId ? String(q.peerId) : ''
  const goodsId = q.goodsId ? String(q.goodsId) : ''
  let c = null
  if (peerId) {
    c = (d.convos || []).filter(function (x) {
      if (String(x.peerId || '') !== peerId) return false
      const gid = x.goodsCard ? String(x.goodsCard.id || '') : ''
      return gid === goodsId
    })[0]
  }
  if (!c) {
    c = {
      id: nextId(),
      peerId: q.peerId || 0,
      peerName: '同学',
      peerAvatar: '',
      anon: false,
      peerVerified: false,
      peerOnline: false,
      peerTags: [],
      unread: 0,
      goodsCard: goodsId ? {
        id: q.goodsId,
        title: q.goodsTitle || '',
        price: q.goodsPrice || '',
        emoji: q.goodsEmoji || '🛍️',
        statusText: q.goodsStatus === 2 ? '已售出' : (q.goodsStatus === 0 ? '已下架' : '在售中')
      } : null,
      messages: []
    }
    d.convos = d.convos || []
    d.convos.unshift(c)
    save()
  }
  return {
    id: c.id,
    peerId: c.peerId,
    peerName: c.peerName,
    goodsCard: c.goodsCard || null,
    messages: c.messages || []
  }
}

/** 会话详情：进入后清零未读 */
function convoDetail(id) {
  const d = load()
  const c = (d.convos || []).filter(function (x) {
    return String(x.id) === String(id)
  })[0]
  if (!c) throw { code: 404, msg: '会话不存在或已删除' }
  c.unread = 0
  c.messages.forEach(function (m) {
    m.unread = false
    m.read = true
  })
  save()
  return {
    id: c.id,
    peerName: c.peerName,
    peerAvatar: c.peerAvatar,
    anon: !!c.anon,
    official: !!c.official,
    peerTags: c.peerTags || [],
    goodsCard: c.goodsCard || null,
    messages: c.messages.filter(function (m) { return !m.deleted })
  }
}

/** 发送消息：追加我方消息并模拟对方自动回复 */
function sendMessage(id, q) {
  requireLogin()
  const d = load()
  const c = (d.convos || []).filter(function (x) {
    return String(x.id) === String(id)
  })[0]
  if (!c) throw { code: 404, msg: '会话不存在或已删除' }
  const content = String(q.content || '').trim()
  if (!content) throw { code: 400, msg: '消息内容不能为空' }

  c.messages.push({ id: nextId(), from: 'me', type: 'text', content: content, createdAt: fmt(new Date()) })

  // 简单的自动回复，营造真实聊天感
  const replies = [
    '收到收到，稍等我下课回你详细说～',
    '好的呀，那就这么说定啦！',
    '嗯嗯，可以的，到时候面交再细聊 👌',
    '哈哈好嘞，有问题随时找我～'
  ]
  setTimeout(function () {
    c.messages.push({
      id: nextId(),
      from: 'peer',
      type: 'text',
      content: replies[Math.floor(Math.random() * replies.length)],
      createdAt: fmt(new Date())
    })
    save()
  }, 1200)

  save()
  return { messages: c.messages }
}

/**
 * 统一入口：永不抛异常，总是返回 {code, msg, data}
 */
function handle(method, url, data) {
  try {
    return route(method, url, data)
  } catch (e) {
    if (e && e.code) return err(e.code, e.msg || '请求失败')
    return err(500, '模拟数据异常：' + (e && e.message ? e.message : '未知错误'))
  }
}

module.exports = {
  handle: handle,
  reset: reset,
  logout: logout
}
