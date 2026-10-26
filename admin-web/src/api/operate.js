import request from '@/utils/request'

/** 广告位与素材 */
export const adApi = {
  slotCodes: () => request.get('/admin/ads/slots/codes'),
  slots: (params) => request.get('/admin/ads/slots', { params }),
  createSlot: (data) => request.post('/admin/ads/slots', data),
  updateSlot: (id, data) => request.put('/admin/ads/slots/' + id, data),
  removeSlot: (id) => request.delete('/admin/ads/slots/' + id),
  auditSlot: (id, pass, reason) => request.post('/admin/ads/slots/' + id + '/audit', { pass, reason }),
  setSlotStatus: (id, status) => request.post('/admin/ads/slots/' + id + '/status', null, { params: { status } }),

  materials: (params) => request.get('/admin/ads/materials', { params }),
  createMaterial: (data) => request.post('/admin/ads/materials', data),
  updateMaterial: (id, data) => request.put('/admin/ads/materials/' + id, data),
  removeMaterial: (id) => request.delete('/admin/ads/materials/' + id),
  auditMaterial: (id, pass, reason) => request.post('/admin/ads/materials/' + id + '/audit', { pass, reason }),
  setMaterialStatus: (id, status) => request.post('/admin/ads/materials/' + id + '/status', null, { params: { status } })
}

/** 通知公告 */
export const noticeApi = {
  page: (params) => request.get('/admin/notices', { params }),
  detail: (id) => request.get('/admin/notices/' + id),
  create: (data) => request.post('/admin/notices', data),
  update: (id, data) => request.put('/admin/notices/' + id, data),
  remove: (id) => request.delete('/admin/notices/' + id),
  publish: (id) => request.post('/admin/notices/' + id + '/publish'),
  offline: (id) => request.post('/admin/notices/' + id + '/offline')
}

/** 敏感词 */
export const sensitiveApi = {
  page: (params) => request.get('/admin/sensitive', { params }),
  create: (data) => request.post('/admin/sensitive', data),
  update: (id, data) => request.put('/admin/sensitive/' + id, data),
  remove: (id) => request.delete('/admin/sensitive/' + id),
  reload: () => request.post('/admin/sensitive/reload'),
  check: (text) => request.get('/admin/sensitive/check', { params: { text } })
}

/** 数据看板 */
export const dashboardApi = {
  overview: () => request.get('/admin/dashboard/overview'),
  daily: (params) => request.get('/admin/dashboard/daily', { params }),
  rebuild: (date) => request.post('/admin/dashboard/rebuild', null, { params: { date } })
}

/** 组织与负责人 */
export const regionApi = {
  tree: () => request.get('/admin/regions/tree'),
  /** 省 / 市下拉（取自 t_school，保证与学校字典逐字一致） */
  areas: () => request.get('/admin/regions/areas'),
  /** 校区下拉（可搜索），替代手填 t_school.id */
  schools: (params) => request.get('/admin/regions/schools', { params }),
  create: (data) => request.post('/admin/regions', data),
  update: (id, data) => request.put('/admin/regions/' + id, data),
  remove: (id) => request.delete('/admin/regions/' + id),
  leaders: (regionId) => request.get('/admin/regions/leaders', { params: { regionId } }),
  assignLeader: (regionId, adminId, leaderType) =>
    request.post('/admin/regions/leaders/assign', null, { params: { regionId, adminId, leaderType } }),
  revokeLeader: (id) => request.post('/admin/regions/leaders/' + id + '/revoke')
}

/** 后台图片上传（广告素材 / 公告配图），返回可直接访问的完整 URL */
export const uploadApi = {
  image: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/admin/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
}
