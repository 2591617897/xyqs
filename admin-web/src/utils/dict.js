/**
 * 状态字典。统一放「值 -> 标签 + el-tag 类型」，页面里不要再手写分支。
 */

export const STATUS_NORMAL = {
  0: { label: '禁用', type: 'danger' },
  1: { label: '正常', type: 'success' }
}

export const RESULT = {
  0: { label: '成功', type: 'success' },
  1: { label: '失败', type: 'danger' }
}

export const DATA_SCOPE = {
  1: { label: '全部数据', type: '' },
  2: { label: '仅辖区', type: 'warning' }
}

export const MENU_TYPE = {
  1: { label: '目录', type: 'info' },
  2: { label: '菜单', type: 'success' },
  3: { label: '按钮', type: 'warning' }
}

/** 内容可见状态：帖子/表白墙 0隐藏 1正常 */
export const CONTENT_STATUS = {
  0: { label: '已隐藏', type: 'danger' },
  1: { label: '正常', type: 'success' }
}

/** 商品状态 0已下架 1在售 2已售出 */
export const GOODS_STATUS = {
  0: { label: '已下架', type: 'info' },
  1: { label: '在售', type: 'success' },
  2: { label: '已售出', type: 'warning' }
}

/** 需求大类 1求物 2求帮忙 */
export const DEMAND_TYPE = {
  1: { label: '求物', type: 'primary' },
  2: { label: '求帮忙', type: 'success' }
}

/** 需求状态 0已下架 1进行中 2已完成 */
export const DEMAND_STATUS = {
  0: { label: '已下架', type: 'info' },
  1: { label: '进行中', type: 'success' },
  2: { label: '已完成', type: 'warning' }
}

/** 实名认证 0未认证 1审核中 2已认证 3驳回 */
export const VERIFY_STATUS = {
  0: { label: '未认证', type: 'info' },
  1: { label: '审核中', type: 'warning' },
  2: { label: '已认证', type: 'success' },
  3: { label: '已驳回', type: 'danger' }
}

/** 用户状态 0封禁 1正常 */
export const USER_STATUS = {
  0: { label: '已封禁', type: 'danger' },
  1: { label: '正常', type: 'success' }
}

/** 工单状态 0待处理 1已处理 2已驳回 */
export const TICKET_STATUS = {
  0: { label: '待处理', type: 'warning' },
  1: { label: '已处理', type: 'success' },
  2: { label: '已驳回', type: 'info' }
}

/** 审核状态 0待审 1通过 2驳回 */
export const AUDIT_STATUS = {
  0: { label: '待审', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}

/** 公告状态 0草稿 1已发布 2已下线 */
export const NOTICE_STATUS = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已下线', type: 'info' }
}

/** 敏感词等级 1提示 2替换 3禁止 */
export const SENSITIVE_LEVEL = {
  1: { label: '仅记录', type: 'info' },
  2: { label: '替换', type: 'warning' },
  3: { label: '禁止发布', type: 'danger' }
}

/** 禁言范围 */
export const MUTE_SCOPE = {
  1: { label: '全站', type: 'danger' },
  2: { label: '发帖', type: 'warning' },
  3: { label: '评论', type: 'warning' },
  4: { label: '私信', type: 'warning' }
}

/** 广告位置 */
export const AD_POSITION = {
  1: '首页Banner', 2: '频道页', 3: '表白墙', 4: '商品列表', 5: '开屏', 6: '个人中心'
}

/** 投放范围 */
export const AD_SCOPE = {
  1: { label: '全国', type: 'info' },
  2: { label: '城市', type: 'warning' },
  3: { label: '校区', type: 'success' }
}

/** 组织节点类型 */
export const REGION_TYPE = {
  1: { label: '地区', type: 'info' },
  2: { label: '校区', type: 'success' }
}

/** 内容类型（处置流水 / 评论归属） */
export const TARGET_TYPE = {
  POST: '帖子', WALL: '表白墙', GOODS: '商品',
  P_COMMENT: '帖子评论', W_COMMENT: '墙评论', G_COMMENT: '商品评论',
  MESSAGE: '私信'
}

export function tagOf (dict, value) {
  return dict[value] || { label: String(value ?? '-'), type: 'info' }
}
