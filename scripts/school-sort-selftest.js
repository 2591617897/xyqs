/**
 * 学校选择器排序回归测试（scripts/school-sort-selftest.js）
 * ------------------------------------------------------------------
 * 覆盖 utils/school.js 的三段逻辑，都是线上真踩过的：
 *   1. 自治州 ↔ 州府：腾讯 IP 定位给「黔南布依族苗族自治州」，
 *      t_school.city 存「都匀」，老代码按字符串比永远对不上 → 同城恒为空；
 *   2. 定位失败（key 配额 status=121）时必须降级成「按城市分组」，
 *      不能退回一条 80 多所铺到底的平铺列表；
 *   3. 搜索必须平铺，且每条都带 _key（wx:key/_key 缺失会告警 + 选中态错乱）。
 */
const path = require('path')

/* ---------- 宿主环境桩（utils/util.js 在载入时就要读 wx） ---------- */
const store = {}
const mp = {
  getStorageSync: (k) => (k in store ? store[k] : ''),
  setStorageSync: (k, v) => { store[k] = JSON.parse(JSON.stringify(v)) },
  removeStorageSync: (k) => { delete store[k] },
  showToast: () => {},
  request: () => {}
}
global.wx = mp

const schoolMod = require(path.join(__dirname, '../miniprogram/utils/school.js'))

let pass = 0
let fail = 0
function ok(name, cond, extra) {
  if (cond) {
    pass++
    console.log('  ok   ' + name)
  } else {
    fail++
    console.log('  FAIL ' + name + (extra ? '  → ' + JSON.stringify(extra) : ''))
  }
}

/** 真实数据形状：贵州三个自治州 + 省会 + 外省 */
const SCHOOLS = [
  { id: 1, name: '贵州大学', province: '贵州', city: '贵阳' },
  { id: 2, name: '贵州师范大学', province: '贵州', city: '贵阳' },
  { id: 3, name: '贵州医科大学', province: '贵州', city: '贵阳' },
  { id: 4, name: '黔南民族师范学院', province: '贵州', city: '都匀' },
  { id: 5, name: '贵州黔南经济学院', province: '贵州', city: '都匀' },
  { id: 6, name: '凯里学院', province: '贵州', city: '凯里' },
  { id: 7, name: '兴义民族师范学院', province: '贵州', city: '兴义' },
  { id: 8, name: '四川大学', province: '四川', city: '成都' }
]

console.log('\n[1] 自治州 ↔ 州府映射')
ok('黔南州 = 都匀', schoolMod.sameRegion('黔南布依族苗族自治州', '都匀') === true)
ok('黔东南州 = 凯里', schoolMod.sameRegion('黔东南苗族侗族自治州', '凯里') === true)
ok('黔西南州 = 兴义', schoolMod.sameRegion('黔西南布依族苗族自治州', '兴义') === true)
ok('黔南州 ≠ 凯里', schoolMod.sameRegion('黔南布依族苗族自治州', '凯里') === false)
ok('黔南州 ≠ 黔东南州', schoolMod.sameRegion('黔南布依族苗族自治州', '黔东南苗族侗族自治州') === false)
ok('贵阳市 = 贵阳', schoolMod.sameRegion('贵阳市', '贵阳') === true)
ok('贵阳 ≠ 遵义', schoolMod.sameRegion('贵阳', '遵义') === false)
ok('都匀不是自治州，seatOf 为空', schoolMod.seatOf('都匀') === '')
ok('非自治州 seatOf 为空', schoolMod.seatOf('贵阳市') === '')

console.log('\n[2] 定位命中：同城置顶')
const nearList = schoolMod.buildList(SCHOOLS, '黔南布依族苗族自治州')
ok('首项是「同城」分组头', !!nearList[0].group && nearList[0].tone === 'near', nearList[0])
ok('分组头写明同城 2 所', /同城 2 所/.test(nearList[0].group), nearList[0].group)
ok('同城学校是都匀的两所', nearList[1].city === '都匀' && nearList[2].city === '都匀', nearList.slice(1, 3))
ok('贵阳学校不在同城区', nearList[1].name !== '贵州大学')
const allIdx = nearList.findIndex((x) => x.group === '全部学校')
ok('其余学校归入「全部学校」', allIdx > 0 && nearList.length === 1 + 2 + 1 + 6, { len: nearList.length, allIdx })

console.log('\n[3] 定位失败：按城市分组（不是平铺）')
const grouped = schoolMod.buildList(SCHOOLS, '')
ok('首项是分组头', !!grouped[0].group, grouped[0])
ok('组间按学校数降序：贵阳 · 3 所', grouped[0].group === '贵阳 · 3 所', grouped[0].group)
ok('分组头数量 = 城市数（5）', grouped.filter((x) => x.group).length === 5, grouped.filter((x) => x.group).map((x) => x.group))
ok('学校节点数量 = 8', grouped.filter((x) => !x.group).length === 8)
ok('每条都有 _key', grouped.every((x) => !!x._key))
ok('_key 不重复', new Set(grouped.map((x) => x._key)).size === grouped.length)
ok('没有分组的裸平铺回归', grouped.some((x) => x.group))

console.log('\n[4] 定位到但无同城：先说明再分组')
const missList = schoolMod.buildList(SCHOOLS, '火星')
ok('首项是「暂无同城学校」提示', /暂无同城学校/.test(missList[0].group), missList[0].group)
ok('提示之后仍然分组', missList[1].group === '贵阳 · 3 所', missList[1].group)

console.log('\n[5] 搜索：平铺 + 带 _key')
const hit = schoolMod.flatList(schoolMod.search(SCHOOLS, '黔南'))
ok('命中 2 所', hit.length === 2, hit.map((x) => x.name))
ok('搜索结果没有分组头', hit.every((x) => !x.group))
ok('搜索结果带 _key', hit.every((x) => !!x._key))
ok('按城市也能搜', schoolMod.search(SCHOOLS, '都匀').length === 2)

console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
process.exit(fail ? 1 : 0)
