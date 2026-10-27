import request from '@/utils/request'

const BASE = '/admin/users'

export const userApi = {
  page: (params) => request.get(BASE, { params }),
  detail: (id) => request.get(BASE + '/' + id),
  stats: (id) => request.get(BASE + '/' + id + '/stats'),
  ban: (id, reason) => request.post(BASE + '/' + id + '/ban', { reason }),
  unban: (id) => request.post(BASE + '/' + id + '/unban'),
  blacklist: (id, reason) => request.post(BASE + '/' + id + '/blacklist', { reason }),
  releaseBlacklist: (id) => request.post(BASE + '/' + id + '/blacklist/release'),
  mute: (id, data) => request.post(BASE + '/' + id + '/mute', data),
  unmute: (id, scope) => request.post(BASE + '/' + id + '/unmute', null, { params: { scope } }),
  mutes: (params) => request.get(BASE + '/mutes', { params }),
  releaseExpiredMutes: () => request.post(BASE + '/mutes/release-expired'),
  blacklistPage: (params) => request.get(BASE + '/blacklist', { params }),
  verifyPending: (params) => request.get(BASE + '/verify/pending', { params }),
  verify: (id, pass, reason) => request.post(BASE + '/' + id + '/verify', { pass, reason }),
  revokeVerify: (id, reason) => request.post(BASE + '/' + id + '/verify/revoke', { reason }),
  verifyLogs: (params) => request.get(BASE + '/verify/logs', { params })
}

export const chatApi = {
  conversations: (params) => request.get('/admin/chat/conversations', { params }),
  messages: (params) => request.get('/admin/chat/messages', { params }),
  deleteMessage: (id, reason) => request.post('/admin/chat/messages/' + id + '/delete', { reason })
}
