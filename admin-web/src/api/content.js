import request from '@/utils/request'

/**
 * 内容管理与举报工单。
 * 路径前缀 /admin 由网关 StripPrefix 剥成 /api/admin 后落到 campus-admin。
 */
const BASE = '/admin/content'

export const contentApi = {
  posts: (params) => request.get(BASE + '/posts', { params }),
  postDetail: (id) => request.get(BASE + '/posts/' + id),
  setPostStatus: (id, data) => request.post(BASE + '/posts/' + id + '/status', data),

  wall: (params) => request.get(BASE + '/wall', { params }),
  wallDetail: (id) => request.get(BASE + '/wall/' + id),
  setWallStatus: (id, data) => request.post(BASE + '/wall/' + id + '/status', data),

  goods: (params) => request.get(BASE + '/goods', { params }),
  goodsDetail: (id) => request.get(BASE + '/goods/' + id),
  setGoodsStatus: (id, data) => request.post(BASE + '/goods/' + id + '/status', data),

  // 需求广场（求物 / 求帮忙）
  demands: (params) => request.get(BASE + '/demands', { params }),
  demandDetail: (id) => request.get(BASE + '/demands/' + id),
  setDemandStatus: (id, data) => request.post(BASE + '/demands/' + id + '/status', data),
  // DELETE 带请求体：axios 的 delete 第二个参数是 config，body 必须走 data
  deleteDemand: (id, data) => request.delete(BASE + '/demands/' + id, { data }),
  demandCategories: () => request.get(BASE + '/demand-categories'),

  comments: (params) => request.get(BASE + '/comments', { params }),
  deleteComment: (id, targetType, targetId, data) =>
    request.post(BASE + '/comments/' + id + '/delete', data, { params: { targetType, targetId } }),

  audits: (params) => request.get(BASE + '/audits', { params })
}
