# 单图显示修复 · 宽度铺满 + 高度限定 + 放大预览

## 一句话结论

上一轮用 `widthFix` 只做到了「铺满」，做不到「限高」—— 一张长截图能把整张卡片撑到几千像素高。现在全站单图统一改成 **`aspectFill` + CSS 固定高度**：宽度照旧水平铺满，超长竖图上下裁掉一点（可裁内容），点图仍能放大看原图。

## 改了什么

| 位置 | 改动 |
|---|---|
| `miniprogram/utils/img.js` | `imgMode()` 统一返回 `aspectFill`，注释写明「单图为什么不用 widthFix」 |
| `pages/index/index.wxss` | `.cols-1 .wall-img` → `width:100%; height:420rpx`（首页热帖/帖子/树洞三卡共用） |
| `pages/post/detail.wxss` | `.cols-1 .article-img` → `width:100%; height:480rpx`（详情页给高一点，看得清） |
| `pages/wall/detail.wxml` | `mode="widthFix"` → `mode="aspectFill"`，容器加 `single` 档 |
| `pages/wall/detail.wxss` | `.img` → `height:340rpx`；`.imgs.single .img` → `height:480rpx` |
| `pages/wall/index.wxss` | `.feed-img-wrap.single` 宽度 `78%` → `100%`（原来根本没铺满），高度保持 340rpx |

## 为什么是 aspectFill

`widthFix` 的高度 = 图片原始比例 × 容器宽，**天生无法限高**；要限高只能换 `aspectFill` + CSS 写死 `height`。
代价是超长竖图会被裁掉上下一点 —— 这正是「可裁内容」要的效果，且三个入口的放大预览本来就都在：

- 首页 `previewZoneImage` → `mp.previewImage`
- 帖子详情 `previewImage` → `mp.previewImage`
- 树洞详情 `preview` → `mp.previewImage`

多图档位（2/4 张两列、3 张以上九宫格）不受影响，仍是统一格子裁切。

## 验证

- `check-miniprogram`：23 主包 + 12 分包，页面配置完整 PASS
- `home-zones-selftest`：35 / 35 PASS
- `msg-subpackage-check`：全部通过
- `node --check` 6 个改动 js 全过；`require('utils/img.js')` 导出与档位映射实测正确
- 本轮**零后端改动**，纯前端样式

## 顺手修掉的一个坑

批量替换时用 Python 正则跨函数匹配，把 `utils/img.js` 从第一个注释块一路吃到 `imgMode`，中间三个函数（`resolve` / `resolveList` / `imgGridClass`）被整段替换没了 —— 已整文件重写恢复，并复核 git diff 只剩 35 行增量。

> 教训已写进项目长期约定：跨函数的多行正则必须锚定唯一标识；写文件前先判 CRLF / LF。

## 下一步

- 真机看一眼三处单图的实际高度是否合意（420 / 480 / 480rpx），要调只改各页那一个 `height` 值
- `campus-post` 服务仍在跑旧包（详情页新评论能力未生效），需要时重启再联调
