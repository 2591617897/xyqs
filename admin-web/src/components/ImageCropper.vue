<template>
  <el-dialog v-model="visible" title="调整素材图" width="620px" append-to-body
             :close-on-click-modal="false" @opened="fit" @closed="release">
    <div class="editor">
      <div ref="wrapRef" class="wrap" :style="{ height: wrapH + 'px' }"
           @pointerdown="startDrag" @wheel.prevent="onWheel">
        <img v-if="src" ref="imgRef" :src="src" class="pic" :style="picStyle"
             draggable="false" @load="onLoad" @error="onError" />
        <div class="crop" :style="cropStyle">
          <i class="corner tl"></i><i class="corner tr"></i>
          <i class="corner bl"></i><i class="corner br"></i>
        </div>
      </div>

      <div class="bar">
        <span class="lbl">缩放</span>
        <el-slider v-model="zoom" :min="1" :max="4" :step="0.01" class="slider" />
        <span class="pct">{{ Math.round(zoom * 100) }}%</span>
      </div>

      <div class="meta">
        <span>规格 <b>{{ ratioText }}</b></span>
        <span>导出 <b>{{ outW }}×{{ outH }}</b></span>
        <span>上限 <b>{{ maxMb }}MB</b>（自动压缩）</span>
      </div>
      <div class="hint">
        拖动图片调整位置，滚轮或滑块缩放；框外的部分会被裁掉{{ mime === 'image/png' ? '，透明区域会填白' : '' }}。
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="busy" @click="confirm">压缩并上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 后台素材图裁剪 + 压缩。
 *
 * <p>为什么要有这个：素材按规格 aspectFill 填充，运营直接把手机原图（4000px、8MB）
 * 丢上来，一是超后端大小限制直接失败，二是比例不对被裁掉一截而他在后台看不出来。
 * 这里在浏览器里先按广告位比例裁好、缩到建议像素、压到 1MB 以内，
 * 传上去的就是「小程序真正会显示的那一版」。
 *
 * <p>纯 canvas 实现，不引入 cropper 依赖：交互只有拖动 + 缩放，够用且不增加包体。
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 待处理图片（objectURL） */
  src: { type: String, default: '' },
  /** 目标宽高比（宽/高），与广告位规格一致 */
  ratio: { type: Number, default: 3 },
  /** 建议导出宽度（px），原图更小则按原图，不放大 */
  targetWidth: { type: Number, default: 1080 },
  /** 原文件 MIME，用于保留 PNG 透明 */
  mime: { type: String, default: '' },
  /** 压缩上限（字节） */
  maxBytes: { type: Number, default: 1024 * 1024 }
})

const emit = defineEmits(['update:modelValue', 'done', 'cancel'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

/** 裁剪框左右留白：留出来才能看见被裁掉的部分，不然用户不知道自己在裁什么 */
const PAD = 24
const MAX_CROP_H = 320

const wrapRef = ref()
const imgRef = ref()
const wrapW = ref(520)
const natural = ref({ w: 0, h: 0 })
const zoom = ref(1)
const offX = ref(0)
const offY = ref(0)
const busy = ref(false)

const maxMb = computed(() => (props.maxBytes / 1024 / 1024).toFixed(1))
const ratioText = computed(() => props.ratio.toFixed(2) + ':1')

const crop = computed(() => {
  const avail = Math.max(120, wrapW.value - PAD * 2)
  let w = avail
  let h = w / props.ratio
  // 竖图比例（如 750x1334）直接按宽度算会高得离谱，改成按高度封顶反算宽度
  if (h > MAX_CROP_H) {
    h = MAX_CROP_H
    w = h * props.ratio
  }
  return { w, h }
})
const wrapH = computed(() => Math.round(crop.value.h + PAD * 2))
const cropLeft = computed(() => (wrapW.value - crop.value.w) / 2)
const cropTop = computed(() => (wrapH.value - crop.value.h) / 2)
const cropStyle = computed(() => ({
  width: crop.value.w + 'px',
  height: crop.value.h + 'px',
  left: cropLeft.value + 'px',
  top: cropTop.value + 'px'
}))

/** 原图像素 → 显示像素的基础倍率（zoom=1 时刚好铺满裁剪框） */
const base = computed(() => {
  const { w, h } = natural.value
  if (!w || !h) return 1
  return Math.max(crop.value.w / w, crop.value.h / h)
})
const disp = computed(() => ({
  w: natural.value.w * base.value * zoom.value,
  h: natural.value.h * base.value * zoom.value
}))
const origin = computed(() => ({
  x: (wrapW.value - disp.value.w) / 2 + offX.value,
  y: (wrapH.value - disp.value.h) / 2 + offY.value
}))
const picStyle = computed(() => ({
  width: disp.value.w + 'px',
  height: disp.value.h + 'px',
  left: origin.value.x + 'px',
  top: origin.value.y + 'px'
}))

const outW = computed(() => {
  // 显示裁剪框对应的原图像素宽度，作为导出宽度上限，避免把小图放大糊掉
  const scale = base.value * zoom.value
  const srcW = scale > 0 ? crop.value.w / scale : 0
  const w = Math.min(props.targetWidth || 1080, srcW || props.targetWidth || 1080)
  return Math.max(160, Math.round(w))
})
const outH = computed(() => Math.max(1, Math.round(outW.value / props.ratio)))

function clamp () {
  const mx = Math.max(0, (disp.value.w - crop.value.w) / 2)
  const my = Math.max(0, (disp.value.h - crop.value.h) / 2)
  offX.value = Math.min(mx, Math.max(-mx, offX.value))
  offY.value = Math.min(my, Math.max(-my, offY.value))
}

watch(zoom, clamp)

function onLoad () {
  const img = imgRef.value
  if (!img) return
  natural.value = { w: img.naturalWidth, h: img.naturalHeight }
  if (wrapRef.value) wrapW.value = wrapRef.value.clientWidth || wrapW.value
  zoom.value = 1
  offX.value = 0
  offY.value = 0
}

/** 解码失败（改名的非图片、损坏文件）：别让用户对着空白框干瞪眼 */
function onError () {
  ElMessage.error('图片无法读取，请换一张')
  visible.value = false
}

async function fit () {
  await nextTick()
  if (wrapRef.value) wrapW.value = wrapRef.value.clientWidth || wrapW.value
  // 缓存命中时 load 不会再触发，这里补一次
  if (imgRef.value && imgRef.value.complete && imgRef.value.naturalWidth) onLoad()
}

function release () {
  natural.value = { w: 0, h: 0 }
  zoom.value = 1
  offX.value = 0
  offY.value = 0
  endDrag()
  emit('cancel')
}

let drag = null
function startDrag (e) {
  if (!e.isPrimary) return
  drag = { x: e.clientX, y: e.clientY, ox: offX.value, oy: offY.value }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', endDrag)
}
function onMove (e) {
  if (!drag) return
  offX.value = drag.ox + (e.clientX - drag.x)
  offY.value = drag.oy + (e.clientY - drag.y)
  clamp()
}
function endDrag () {
  drag = null
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', endDrag)
}

function onWheel (e) {
  const next = zoom.value - e.deltaY * 0.0012
  zoom.value = Math.min(4, Math.max(1, Number(next.toFixed(3))))
  clamp()
}

onBeforeUnmount(endDrag)

function toBlob (canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

function draw (canvas, img, box) {
  canvas.width = box.dw
  canvas.height = box.dh
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  if (box.fillWhite) {
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, box.dw, box.dh)
  }
  ctx.drawImage(img, box.sx, box.sy, box.sw, box.sh, 0, 0, box.dw, box.dh)
  return ctx
}

async function confirm () {
  const img = imgRef.value
  if (!img || !img.naturalWidth) return
  busy.value = true
  try {
    const scale = base.value * zoom.value
    let sx = (cropLeft.value - origin.value.x) / scale
    let sy = (cropTop.value - origin.value.y) / scale
    const sw = crop.value.w / scale
    const sh = crop.value.h / scale
    // 浮点误差可能让裁剪框越界 1px，收敛回图内，否则 drawImage 会画出空白边
    sx = Math.max(0, Math.min(sx, img.naturalWidth - sw))
    sy = Math.max(0, Math.min(sy, img.naturalHeight - sh))
    if (sw <= 0 || sh <= 0) return

    const box = { sx, sy, sw, sh, dw: outW.value, dh: outH.value }

    // PNG 优先保留透明；压不进上限时再白底转 JPEG
    let blob = null
    if (props.mime === 'image/png') {
      const canvas = document.createElement('canvas')
      draw(canvas, img, box)
      blob = await toBlob(canvas, 'image/png')
      if (blob && blob.size <= props.maxBytes) {
        emit('done', blob)
        return
      }
    }

    const canvas = document.createElement('canvas')
    draw(canvas, img, { ...box, fillWhite: true })
    let quality = 0.9
    blob = await toBlob(canvas, 'image/jpeg', quality)
    while (blob && blob.size > props.maxBytes && quality > 0.45) {
      quality = Number((quality - 0.1).toFixed(2))
      blob = await toBlob(canvas, 'image/jpeg', quality)
    }
    if (blob) emit('done', blob)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.editor { user-select: none; }
.wrap {
  position: relative; width: 100%; overflow: hidden; border-radius: 6px;
  background: #1f2430; cursor: grab; touch-action: none;
}
.wrap:active { cursor: grabbing; }
.pic { position: absolute; will-change: left, top; pointer-events: none; }
/* 裁剪框：巨大的外阴影就是遮罩，比再叠四个 div 稳 */
.crop {
  position: absolute; box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, .9);
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, .5);
  pointer-events: none;
}
.corner {
  position: absolute; width: 12px; height: 12px;
  border: 2px solid #fff; border-radius: 1px;
}
.corner.tl { top: -2px; left: -2px; border-right: 0; border-bottom: 0; }
.corner.tr { top: -2px; right: -2px; border-left: 0; border-bottom: 0; }
.corner.bl { bottom: -2px; left: -2px; border-right: 0; border-top: 0; }
.corner.br { bottom: -2px; right: -2px; border-left: 0; border-top: 0; }
.bar { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.bar .lbl { font-size: 12px; color: #909399; }
.bar .slider { flex: 1; }
.bar .pct { width: 46px; text-align: right; font-size: 12px; color: #606266; }
.meta {
  display: flex; flex-wrap: wrap; gap: 16px; margin-top: 6px;
  font-size: 12px; color: #606266;
}
.meta b { color: #303133; font-weight: 600; }
.hint { margin-top: 6px; font-size: 12px; line-height: 1.6; color: #909399; }
</style>
