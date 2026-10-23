const { BASE_URL, USE_MOCK } = require('./config')
const { mp, toast } = require('./util')
const http = require('./request')

/**
 * 判断是否已经是服务端地址（编辑场景下回显的旧图不需要重复上传）
 */
function isServerUrl(url) {
  if (!url) return false
  if (url.indexOf('/uploads/') >= 0) return true
  if (!/^https?:\/\//.test(url)) return false
  // 排除小程序临时文件协议
  return url.indexOf('tmp') < 0 && url.indexOf('wxfile') < 0
}

/**
 * 上传单张图片，返回可直接渲染的 URL
 */
function uploadImage(filePath) {
  if (!filePath) {
    return Promise.reject({ code: 400, msg: '图片路径为空' })
  }
  // 模拟数据模式：本地路径直接可用
  if (USE_MOCK) return Promise.resolve(filePath)
  if (isServerUrl(filePath)) return Promise.resolve(filePath)

  return new Promise(function (resolve, reject) {
    mp.uploadFile({
      url: BASE_URL + '/upload',
      filePath: filePath,
      name: 'file',
      timeout: 30000,
      header: {
        Authorization: 'Bearer ' + (http.getToken() || '')
      },
      success: function (res) {
        let body = res.data
        if (typeof body === 'string') {
          try {
            body = JSON.parse(body)
          } catch (e) {
            body = {}
          }
        }
        if (res.statusCode === 401 || (body && body.code === 401)) {
          http.cleanLogin()
          toast('登录已失效，请重新登录')
          reject({ code: 401, msg: '未登录' })
          return
        }
        if (body && body.code === 0 && body.data && body.data.url) {
          resolve(body.data.url)
          return
        }
        toast((body && body.msg) || '图片上传失败')
        reject({ code: (body && body.code) || 500, msg: (body && body.msg) || '上传失败' })
      },
      fail: function () {
        toast('图片上传失败，请检查网络')
        reject({ code: -1, msg: 'upload fail' })
      }
    })
  })
}

/**
 * 批量上传（保持顺序），已上传过的地址会跳过
 */
function uploadImages(paths) {
  const list = paths || []
  if (!list.length) return Promise.resolve([])
  return Promise.all(
    list.map(function (p) {
      return uploadImage(p)
    })
  )
}

module.exports = {
  uploadImage: uploadImage,
  uploadImages: uploadImages,
  isServerUrl: isServerUrl
}
