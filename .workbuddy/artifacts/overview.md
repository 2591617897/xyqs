# 首页「闲置交换」卡片改版 · 对齐参考图商品排版

## 一句话结论

参考图（拼多多式商品卡）的**结构**照搬，**内容**全换成我们的真实字段：标题 → 描述 → 红色卖点行 → 浅黄特色条 → 价格行 + 「看详情」按钮。参考图里的「全网热销 20 万+件」「7 天无理由退货」「包邮」「抢」一个都没抄 —— 前三个是虚假宣传会被审核打回，「抢」字违反「闲置仅信息展示、禁止线上交易」的项目红线。

## 卡片五层结构

| 层 | 参考图 | 我们的实现 | 数据来源 |
|---|---|---|---|
| 标题 | 商品标题 | `zcard-goods-title`（clamp2） | `title` |
| 描述 | — | `zcard-goods-desc`（clamp1，灰色） | `description` |
| 卖点行（红字） | 全网热销 / 包邮 / 退货 | `zcard-goods-sell`（红字，不换行） | `location` · `categoryName` · 成色 `conditionLevel` · `viewCount` 浏览 |
| 特色条（浅黄底） | 券 · 已补 X 元 | `zcard-goods-feature`（左「N 人想要」/ 右「发布时间」） | `likeCount` / `commentCount` / `createdAt` |
| 价格行 + 按钮 | ¥2.2 优惠前 3.9 元 + 「抢」 | `zcard-goods-price`（橙色价格 + 划线原价 + 绿色「看详情」） | `price` / `originalPrice` |

## 改了什么

- `miniprogram/utils/zones.js`：`goodsItem` 新增 `sellParts`（分段数组，每段带稳定 `k` 做 `wx:key`）、`featureMain`、`featureSub`
- `miniprogram/pages/index/index.wxml`：闲置卡结构重排为上面五层
- `miniprogram/pages/index/index.wxss`：新增 `.zcard-goods-sell` / `.zcard-goods-feature` / `.zcard-goods-cta`；删掉已成死代码的 `.zcard-goods-tags` / `.zcard-goods-tag` / `.zcard-goods-foot` / `.zcard-goods-loc` / `.zcard-goods-stat`

## 几个刻意的取舍

- **想要数为 0 不写「0 人想要」**（是噪音），退到留言数，再没有就说「✨ 校内在售」
- **位置可能很长**：单独限宽 220rpx + 省略，不把后面的成色、浏览量挤没
- **价格行 `margin-top:auto` 吸底**：描述长短都不影响卡片底部对齐，左图 `align-items:stretch` 同步拉高，两张卡视觉齐平
- **零后端改动**：`GET /goods` 直接返回 `Goods` 实体，成色 / 分类 / 位置 / 浏览 / 想要 / 原价字段本来就全
- **只动首页功能区**：`pages/goods/list` 用的是独立 class `.goods-card`，未受影响

## 验证

- `check-miniprogram`（23 主包 + 12 分包）PASS
- `home-zones-selftest` 35 / 35 PASS
- `node --check utils/zones.js` PASS
- `goodsItem` 四种数据分支实测输出正确：正常 / 零想要 / 有留言 / 全空属性

## 下一步

- 真机看一眼红色卖点行在长地址下会不会挤掉成色，必要时把 `sell-loc` 的 220rpx 再收一点
- 「看详情」按钮点下去就是卡片原本的详情跳转，没有新逻辑，不需要后端配合
