import request from '@/utils/request'

/**
 * 所有后台接口统一走 /api/admin 前缀（网关 StripPrefix=1 后落到 campus-admin）。
 */
const BASE = '/admin'

export const authApi = {
  login: (data) => request.post(BASE + '/auth/login', data),
  logout: () => request.post(BASE + '/auth/logout'),
  info: () => request.get(BASE + '/auth/info'),
  menus: () => request.get(BASE + '/auth/menus'),
  changePassword: (data) => request.post(BASE + '/auth/password', data)
}

export const adminApi = {
  page: (params) => request.get(BASE + '/admins', { params }),
  detail: (id) => request.get(BASE + '/admins/' + id),
  create: (data) => request.post(BASE + '/admins', data),
  update: (id, data) => request.put(BASE + '/admins/' + id, data),
  remove: (id) => request.delete(BASE + '/admins/' + id),
  changeStatus: (id, status) => request.post(BASE + '/admins/' + id + '/status', null, { params: { status } }),
  resetPassword: (id, newPassword) => request.post(BASE + '/admins/' + id + '/reset-pwd', { newPassword })
}

export const roleApi = {
  page: (params) => request.get(BASE + '/roles', { params }),
  detail: (id) => request.get(BASE + '/roles/' + id),
  create: (data) => request.post(BASE + '/roles', data),
  update: (id, data) => request.put(BASE + '/roles/' + id, data),
  remove: (id) => request.delete(BASE + '/roles/' + id),
  menuIds: (id) => request.get(BASE + '/roles/' + id + '/menus'),
  grantMenus: (id, menuIds) => request.put(BASE + '/roles/' + id + '/menus', { menuIds })
}

export const menuApi = {
  tree: (params) => request.get(BASE + '/menus', { params }),
  detail: (id) => request.get(BASE + '/menus/' + id),
  create: (data) => request.post(BASE + '/menus', data),
  update: (id, data) => request.put(BASE + '/menus/' + id, data),
  remove: (id) => request.delete(BASE + '/menus/' + id)
}

export const logApi = {
  operations: (params) => request.get(BASE + '/logs/operations', { params }),
  operationDetail: (id) => request.get(BASE + '/logs/operations/' + id),
  logins: (params) => request.get(BASE + '/logs/logins', { params })
}
