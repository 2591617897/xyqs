import request from '@/utils/request'

const BASE = '/admin'

/**
 * 校友群管理。
 *
 * 注意：这组接口全部经网关 `/api/admin/groups/**` → campus-admin → Feign → campus-message，
 * 所以新增接口后必须**重启 campus-admin（8107）**，否则网关透传会遇到 405。
 */
export const groupApi = {
  page: (params) => request.get(BASE + '/groups', { params }),
  stats: () => request.get(BASE + '/groups/stats'),
  detail: (id) => request.get(BASE + '/groups/' + id),
  update: (id, data) => request.put(BASE + '/groups/' + id, data),

  members: (id, params) => request.get(BASE + '/groups/' + id + '/members', { params }),
  setRole: (id, userId, role) => request.post(BASE + '/groups/' + id + '/members/' + userId + '/role', { role }),

  mute: (id, data) => request.post(BASE + '/groups/' + id + '/mute', data),
  unmute: (id, userId) => request.post(BASE + '/groups/' + id + '/unmute', { userId }),
  kick: (id, userId) => request.post(BASE + '/groups/' + id + '/kick', { userId }),
  mutes: (id, params) => request.get(BASE + '/groups/' + id + '/mutes', { params }),

  messages: (id, params) => request.get(BASE + '/groups/' + id + '/messages', { params }),
  deleteMessage: (id, messageId) => request.post(BASE + '/groups/' + id + '/messages/' + messageId + '/delete')
}
