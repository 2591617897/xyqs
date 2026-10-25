const { UPLOAD_URL, USE_MOCK } = require('./config')
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

  // 服务端 multipart 上限 5MB：真机照片（尤其是原图）很容易超限，
  // 超限时 Spring 直接抛 MaxUploadSizeExceeded → 网关包成 500「服务器开小差了」，
  // 用户侧看起来就是「发图片失败」。先本地压缩再上传，从根上避开。
  return shrinkIfNeeded(filePath).then(function (path) {
    return new Promise(function (resolve, reject) {
      mp.uploadFile({
        url: UPLOAD_URL,
        filePath: path,
        name: 'file',
        timeout: 60000,
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
          if (res.statusCode >= 500) {
            toast('图片上传失败：可能是图片超过 5MB，请换一张或截图后重试')
            reject({ code: 500, msg: '上传失败（图片可能超过 5MB）' })
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
  })
}

/** 超过 4MB 的图片先本地压缩（质量逐级下调），控制在服务端 5MB 上限以内 */
function shrinkIfNeeded(filePath) {
  return fileSize(filePath).then(function (size) {
    if (!size || size <= 4 * 1024 * 1024) return filePath
    return compressChain(filePath, [80, 60, 40])
  })
}

function fileSize(filePath) {
  return new Promise(function (resolve) {
    try {
      mp.getFileSystemManager().stat({
        path: filePath,
        success: function (res) {
          resolve(res.stats ? res.stats.size : 0)
        },
        fail: function () {
          resolve(0)
        }
      })
    } catch (e) {
      resolve(0)
    }
  })
}

/** 按质量梯度反复压缩，直到 < 4MB 或梯度用完 */
function compressChain(filePath, qualities) {
  if (!qualities.length) return Promise.resolve(filePath)
  const quality = qualities[0]
  return new Promise(function (resolve) {
    mp.compressImage({
      src: filePath,
      quality: quality,
      success: function (res) {
        const next = res.compressedFilePath || res.tempFilePath || filePath
        fileSize(next).then(function (size) {
          if (size && size <= 4 * 1024 * 1024) {
            resolve(next)
          } else {
            resolve(compressChain(next, qualities.slice(1)))
          }
        })
      },
      fail: function () {
        // 压缩不可用（低版本基础库等）：放弃压缩，走原文件让服务端报错
        resolve(filePath)
      }
    })
  })
}

/**
 * 上传视频（聊天视频消息）。不做压缩，服务端上限 50MB，
 * 超限直接本地拒绝，避免白传一大半才报错。
 */
function uploadVideo(filePath) {
  if (!filePath) {
    return Promise.reject({ code: 400, msg: '视频路径为空' })
  }
  // 模拟数据模式：本地路径直接可用
  if (USE_MOCK) return Promise.resolve(filePath)
  if (isServerUrl(filePath)) return Promise.resolve(filePath)

  return fileSize(filePath).then(function (size) {
    if (size && size > 50 * 1024 * 1024) {
      toast('视频不能超过 50MB，请截短后再发')
      return Promise.reject({ code: 400, msg: '视频超过 50MB' })
    }
    return uploadRaw(filePath, '视频')
  })
}

/** 通用 multipart 上传（不压缩），kind 仅用于错误文案 */
function uploadRaw(filePath, kind) {
  return new Promise(function (resolve, reject) {
    mp.uploadFile({
      url: UPLOAD_URL,
      filePath: filePath,
      name: 'file',
      timeout: 120000,
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
        if (res.statusCode >= 500) {
          toast(kind + '上传失败，请重试')
          reject({ code: 500, msg: '上传失败' })
          return
        }
        toast((body && body.msg) || kind + '上传失败')
        reject({ code: (body && body.code) || 500, msg: (body && body.msg) || '上传失败' })
      },
      fail: function () {
        toast(kind + '上传失败，请检查网络')
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
  uploadVideo: uploadVideo,
  isServerUrl: isServerUrl
}
