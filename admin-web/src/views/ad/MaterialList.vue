<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip">
      素材必须先通过审核才能上架；所属广告位未上架时，素材也不能上架。
    </el-alert>

    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="广告位">
        <el-select v-model="query.slotId" placeholder="全部" clearable filterable style="width: 200px">
          <el-option v-for="s in slots" :key="s.id" :label="s.name + '（' + s.code + '）'" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="query.keyword" placeholder="素材标题" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item label="审核">
        <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="(v, k) in AUDIT_STATUS" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:ad:material:create'" type="success" :icon="Plus" @click="openCreate">
          新建素材
        </el-button>
      </template>
    </SearchForm>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column label="素材图" width="110" align="center">
        <template #default="{ row }">
          <!-- ⚠️ preview-teleported 不能省：el-image 的预览层默认渲染在原地（表格单元格里），
               它的 position:fixed 会被 el-table 那几层 overflow:hidden 的祖先形成自己的层叠上下文，
               表现就是「点开图片后遮罩不铺满、表格浮在图片上面、整个界面糊在一起」。
               挂到 body 才是干净的「暗底 + 居中大图 + 右上角关闭」。
               z-index 抬高是因为本项目的 el-dialog 也在 2000+，别让弹窗压住预览。 -->
          <el-image :src="row.image" :preview-src-list="[row.image]"
                    preview-teleported :z-index="3000" fit="cover" class="thumb" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="slotName" label="广告位" width="150" show-overflow-tooltip />
      <el-table-column label="跳转" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ LINK_TYPE[row.linkType] || '无' }}{{ row.linkValue ? ' · ' + row.linkValue : '' }}
        </template>
      </el-table-column>
      <el-table-column label="审核" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(AUDIT_STATUS, row.auditStatus).type" size="small">
            {{ tagOf(AUDIT_STATUS, row.auditStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据" width="130" align="center">
        <template #default="{ row }">曝光 {{ row.viewCount }} · 点击 {{ row.clickCount }}</template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:ad:material:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:ad:material:audit'" link type="warning" @click="openAudit(row)">审核</el-button>
          <el-button v-permission="'admin:ad:material:online'" link :type="row.status === 1 ? 'info' : 'success'"
                     @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-permission="'admin:ad:material:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑素材' : '新建素材'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="广告位" prop="slotId">
          <el-select v-model="form.slotId" filterable style="width: 100%">
            <el-option v-for="s in slots" :key="s.id" :label="s.name + '（' + s.code + '）'" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="素材图" prop="image">
          <!-- 尺寸来自广告位的「展示规格」：后台不再让运营自己记 750x240，
               选中广告位就把真实渲染高度、建议像素、比例摆出来，上传后还会校验比例。 -->
          <div v-if="form.slotId" class="size-hint">
            该位规格 <b>{{ currentSize.sizeText }}</b>（{{ currentSize.ratio }}:1）
            · 小程序渲染高度 {{ currentSize.heightRpx }}rpx
            · 建议上传 <b>{{ currentSize.pixelHint }}</b>
            <el-tag v-if="!currentSlot.sizeCode" type="warning" size="small" class="ml6">广告位未设规格，按 240 渲染</el-tag>
          </div>
          <!-- 不用 el-upload：它的点击是根 div 上 handler 里调 input.click()，
               一旦组件实例持有的 input 引用失效（热更新、v-if 切换），点了就静默没反应，
               而且没有任何报错可查。这里把原生 input 直接铺满整个框（opacity:0），
               点框内任何位置 = 直接点 input 本身，由浏览器原生弹框，绕开这类问题。 -->
          <div class="uploader" :class="{ 'is-busy': uploading }">
            <img v-if="form.image" :src="form.image" class="preview" />
            <el-icon v-else class="uploader-icon"><Plus /></el-icon>
            <span v-if="form.image" class="repick">{{ uploading ? '上传中…' : '点击更换' }}</span>
            <input class="file-input" type="file" accept="image/*"
                   :disabled="uploading" @change="onFileChange" />
          </div>
          <div class="hint">
            支持 jpg/png/gif/webp。选图后会按规格裁剪并自动压缩，动图（GIF）按原图上传。
          </div>
        </el-form-item>
        <el-form-item label="跳转类型" prop="linkType">
          <el-select v-model="form.linkType" style="width: 100%">
            <el-option v-for="(label, k) in LINK_TYPE" :key="k" :label="label" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.linkType !== 0" label="跳转目标" prop="linkValue">
          <el-input v-model="form.linkValue" placeholder="帖子ID / 商品ID / URL / 页面路径" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 上传前的浏览器端裁剪：按广告位规格裁好、压到 1MB 内再传，
         避免「原图 8MB 直接被后端拒绝」和「比例不对被小程序裁掉」 -->
    <ImageCropper v-model="cropperVisible" :src="cropSrc" :mime="cropMime"
                  :ratio="currentSize.ratio" :target-width="cropTargetWidth"
                  @done="onCropped" @cancel="releaseCrop" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import { adApi, uploadApi } from '@/api/operate'
import { AUDIT_STATUS, tagOf } from '@/utils/dict'

const LINK_TYPE = { 0: '无跳转', 1: '帖子', 2: '商品', 3: '外链', 4: '小程序页面' }

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const slots = ref([])
/** 规格清单（与广告位表单同源：campus-common 的 AdSizeCatalog） */
const sizeOptions = ref([])
const query = reactive({ slotId: null, keyword: '', auditStatus: null, page: 1, size: 10 })

/** 规格兜底：清单没拿到时也不能让模板报错（与后端 DEFAULT_CODE 一致） */
const FALLBACK_SIZE = {
  code: 'banner_240', widthRpx: 750, heightRpx: 240, ratio: 3.13,
  pixelHint: '1080×346', sizeText: '750×240'
}

function sizeOf (code) {
  const key = code || FALLBACK_SIZE.code
  return sizeOptions.value.find((s) => s.code === key) || sizeOptions.value[0] || FALLBACK_SIZE
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({ slotId: null, title: '', image: '', linkType: 0, linkValue: '', sort: 0 })
const rules = {
  slotId: [{ required: true, message: '请选择广告位', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  image: [{ required: true, message: '请上传素材图', trigger: 'change' }]
}

const currentSlot = computed(
  () => slots.value.find((s) => String(s.id) === String(form.slotId)) || {}
)

/** 当前选中广告位的规格——素材该做多大，这里直接告诉运营 */
const currentSize = computed(() => sizeOf(currentSlot.value.sizeCode))

/** 建议像素（"1080×346"）里的宽度，作为裁剪导出的目标宽度 */
const cropTargetWidth = computed(() => {
  const n = parseInt(String(currentSize.value.pixelHint || ''), 10)
  return Number.isFinite(n) && n > 0 ? n : 1080
})

const uploading = ref(false)
const cropperVisible = ref(false)
const cropSrc = ref('')
const cropMime = ref('')
let pendingFile = null
/** 后端 multipart 放到 20MB，业务上限 5MB；这里压到 1MB 内，真机加载也更快 */
const UPLOAD_MAX_BYTES = 1024 * 1024

async function loadSlots () {
  // 两个请求互不阻塞：规格拿不到不影响选广告位（只是不显示尺寸提示）
  const [list, sizes] = await Promise.all([
    adApi.slots({ size: 100 }).catch(() => ({ records: [] })),
    adApi.slotSizes().catch(() => [])
  ])
  slots.value = list.records || []
  sizeOptions.value = sizes || []
}

async function load () {
  loading.value = true
  try {
    const res = await adApi.materials(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.slotId = null; query.keyword = ''; query.auditStatus = null; reload() }

/**
 * 选中文件后的分流：能裁的进裁剪器，GIF 直传。
 *
 * <p>以前这段挂在 el-upload 的 before-upload 上，靠「返回 false 拦住默认上传」，
 * 现在 input 是我们自己管的，直接在这里处理更直观。
 */
function handlePicked (file) {
  const ok = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!ok) {
    ElMessage.error('只支持 jpg / png / gif / webp')
    return
  }
  // 20MB 之外连浏览器都别接了：解码一张 100MB 的图容易把标签页搞崩
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('图片不能超过 20MB')
    return
  }
  if (file.type === 'image/gif') {
    // 动图不走裁剪（canvas 会把它压成静态图），体积只能按后端上限自己卡：
    // 后端 campus.admin.upload.max-size 默认 5MB，两边不一致就会出现「点了没反应」
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.error('动图不能超过 5MB')
      return
    }
    doUpload(file)
    return
  }
  openCropper(file)
}

function onFileChange (e) {
  const input = e.target
  const file = input.files && input.files[0]
  // 必须清空 value：不清的话第二次选同一个文件不会触发 change，
  // 表现就是「点了没反应」——换图时选回原图最容易撞上
  input.value = ''
  if (file) handlePicked(file)
}

function openCropper (file) {
  releaseCrop()
  pendingFile = file
  cropMime.value = file.type
  cropSrc.value = URL.createObjectURL(file)
  cropperVisible.value = true
}

function releaseCrop () {
  if (cropSrc.value) {
    URL.revokeObjectURL(cropSrc.value)
    cropSrc.value = ''
  }
  pendingFile = null
}

/** 裁剪器导出的 Blob 换成 File 再传，文件名保留可读性 */
function onCropped (blob) {
  const base = (pendingFile && pendingFile.name ? pendingFile.name : 'ad').replace(/\.[^.]+$/, '')
  const ext = blob.type === 'image/png' ? 'png' : 'jpg'
  const file = new File([blob], base + '.' + ext, { type: blob.type })
  cropperVisible.value = false
  doUpload(file)
}

/**
 * 上传后按广告位规格校验比例。
 *
 * <p>只提醒不拦截：偶尔有必须整张展示的素材，硬拦会让运营上传不了。
 * 但默认要说出来——小程序用 aspectFill 填充，比例差太多会把两侧或上下裁掉，
 * 而运营在后台看到的是完整图片，根本发现不了。
 */
function checkRatio (url) {
  const s = currentSize.value
  if (!s || !s.ratio) return
  const img = new Image()
  img.onload = () => {
    if (!img.naturalWidth || !img.naturalHeight) return
    const actual = img.naturalWidth / img.naturalHeight
    const dev = Math.abs(actual - s.ratio) / s.ratio
    if (dev > 0.12) {
      ElMessage.warning(
        '素材比例 ' + actual.toFixed(2) + ':1 与规格 ' + s.ratio + ':1 相差 '
        + Math.round(dev * 100) + '%，小程序会裁切显示'
      )
    }
  }
  img.src = url
}

async function doUpload (file) {
  uploading.value = true
  try {
    form.image = await uploadApi.image(file)
    ElMessage.success('上传成功')
    checkRatio(form.image)
  } catch (e) {
    // 请求层已经弹过后端返回的 msg（如「图片不能超过 5MB」），
    // 这里再补一句「上传失败」只会变成截图里那种两条红条压在一起
  } finally {
    uploading.value = false
    releaseCrop()
  }
}

function openCreate () {
  editing.value = null
  Object.assign(form, { slotId: query.slotId, title: '', image: '', linkType: 0, linkValue: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit (row) {
  editing.value = row
  Object.assign(form, {
    slotId: row.slotId, title: row.title, image: row.image,
    linkType: row.linkType, linkValue: row.linkValue || '', sort: row.sort
  })
  dialogVisible.value = true
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await adApi.updateMaterial(editing.value.id, { ...form })
    } else {
      await adApi.createMaterial({ ...form })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openAudit (row) {
  const pass = await ElMessageBox.confirm('「确定」= 通过（允许上架），「取消」= 驳回',
    '审核素材', { confirmButtonText: '通过', cancelButtonText: '驳回', type: 'warning' })
    .then(() => true).catch(() => false)
  let remark = null
  if (!pass) {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回', {}).catch(() => ({ value: null }))
    if (!value) return
    remark = value
  }
  await adApi.auditMaterial(row.id, pass, remark)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

async function toggle (row) {
  await adApi.setMaterialStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  load()
}

async function remove (row) {
  await ElMessageBox.confirm('删除素材「' + row.title + '」？', '危险操作', { type: 'warning' }).catch(() => false)
  await adApi.removeMaterial(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => { loadSlots(); load() })
</script>

<style scoped>
.tip { margin-bottom: 14px; }
.thumb { width: 72px; height: 44px; border-radius: 4px; }
.uploader { position: relative; width: 200px; height: 120px; border: 1px dashed #d9d9d9; border-radius: 6px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; overflow: hidden; }
.uploader.is-busy { cursor: progress; opacity: .7; }
/* 铺满整框的透明文件输入：点哪都是点它，不依赖任何 JS 转发点击 */
.file-input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.uploader-icon { font-size: 28px; color: #8c939d; }
.preview { width: 200px; height: 120px; object-fit: cover; display: block; }
/* 已上传时盖一层「点击更换」：不加这句运营根本不知道这块还能再点。
   z-index 必须低于 .file-input，否则这行字会把点击吃掉 */
.repick {
  position: absolute; left: 0; right: 0; bottom: 0; z-index: 1;
  padding: 3px 0; font-size: 12px; color: #fff; text-align: center;
  background: rgba(0, 0, 0, .45);
}
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
/* 规格提示放在上传框上方：先看尺寸要求，再选文件，比上传完才发现比例不对强 */
.size-hint {
  width: 100%; margin-bottom: 8px; padding: 6px 8px;
  font-size: 12px; line-height: 1.6; color: #606266;
  background: var(--fill-2, #f5f7fa); border-radius: 4px;
}
.ml6 { margin-left: 6px; }
</style>
