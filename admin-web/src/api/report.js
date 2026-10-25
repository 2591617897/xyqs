import request from '@/utils/request'

const BASE = '/admin'

export const reportApi = {
  page: (params) => request.get(BASE + '/reports', { params }),
  detail: (id) => request.get(BASE + '/reports/' + id),
  handle: (id, data) => request.post(BASE + '/reports/' + id + '/handle', data)
}
