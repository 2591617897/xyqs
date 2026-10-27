<template>
  <div class="page-card">
    <SearchForm :model="query" @search="reload" @reset="reset">
      <el-form-item label="名称">
        <el-input v-model="query.keyword" placeholder="广告位名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="位置">
        <el-select v-model="query.position" placeholder="全部" clearable style="width: 160px">
          <el-option v-for="(label, k) in AD_POSITION" :key="k" :label="label" :value="Number(k)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
        </el-select>
      </el-form-item>
      <template #extra>
        <el-button v-permission="'admin:ad:slot:create'" type="success" :icon="Plus" @click="openCreate">
          新建广告位
        </el-button>
      </template>
    </SearchForm>

    <el-alert v-if="invalidRows.length" type="warning" :closable="false" show-icon class="mb12"
              title="存在小程序不会读取的广告位">
      <template #default>
        以下广告位的编码不在小程序的投放清单里，<b>无论怎么上下架都不会展示</b>，
        建议删除后按「广告位」下拉重建：
        <span v-for="r in invalidRows" :key="r.id" class="bad-code">{{ r.name }}（{{ r.code }}）</span>
      </template>
    </el-alert>

    <PageTable :data="rows" :total="total" v-model:page="query.page" v-model:size="query.size"
               :loading="loading" @load="load">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="编码" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.code }}</span>
          <el-tag v-if="!optionOf(row.code)" type="danger" size="small" effect="dark">未对应</el-tag>
          <el-tag v-else-if="!optionOf(row.code).connected" type="info" size="small">未接入</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="位置" width="130">
        <template #default="{ row }">{{ positionLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="展示规格" width="180">
        <template #default="{ row }">
          <span>{{ sizeTextOf(row) }}</span>
          <el-tooltip v-if="isSizeOffDefault(row)" placement="top"
                      :content="'该位页面按 ' + sizeOf(optionOf(row.code).defaultSize).sizeText + ' 排版，换成其它规格会让页面留白不统一'">
            <el-tag type="warning" size="small" class="target-tag">非推荐</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="投放范围" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="tagOf(AD_SCOPE, row.scopeType).type" size="small">
            {{ tagOf(AD_SCOPE, row.scopeType).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="定向" min-width="210">
        <template #default="{ row }">
          <span v-if="row.scopeType === 1" class="muted">不限</span>
          <template v-else>
            <el-tag v-for="t in targetTags(row).shown" :key="t.key" size="small"
                    :type="row.scopeType === 3 ? 'info' : ''" class="target-tag">{{ t.label }}</el-tag>
            <el-tooltip v-if="targetTags(row).more" :content="targetTags(row).full" placement="top">
              <el-tag size="small" type="warning" class="target-tag">+{{ targetTags(row).more }}</el-tag>
            </el-tooltip>
            <span v-if="!targetTags(row).shown.length" class="muted">未设置</span>
          </template>
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
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'admin:ad:slot:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'admin:ad:slot:update'" link type="warning" @click="openAudit(row)">审核</el-button>
          <el-button v-permission="'admin:ad:slot:status'" link :type="row.status === 1 ? 'info' : 'success'"
                     :disabled="row.status !== 1 && !canOnline(row)"
                     @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button v-permission="'admin:ad:slot:delete'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </PageTable>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑广告位' : '新建广告位'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="广告位" prop="code">
          <el-select
            v-model="form.code"
            :disabled="!!editing"
            filterable
            placeholder="选择小程序里的投放位置"
            style="width: 100%"
            @change="onCodeChange"
          >
            <el-option
              v-for="o in slotOptions"
              :key="o.code"
              :value="o.code"
              :label="o.name + (o.connected ? '' : '（小程序未接入）')"
            >
              <span>{{ o.name }}</span>
              <span class="opt-code">{{ o.code }}{{ o.connected ? '' : ' · 未接入' }}</span>
            </el-option>
          </el-select>
          <div v-if="currentOption" class="form-hint">
            {{ currentOption.pageHint }}　该位推荐 {{ sizeOf(currentOption.defaultSize).sizeText }}
          </div>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="默认与广告位同名，可改成对内可读的名字" />
        </el-form-item>
        <el-form-item label="投放范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType" @change="onScopeChange">
            <el-radio :value="1">全国</el-radio>
            <el-radio :value="2">城市</el-radio>
            <el-radio :value="3">校区</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 2" label="城市" prop="cities">
          <el-select
            v-model="form.cities"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择一个或多个城市"
            style="width: 100%"
          >
            <el-option v-for="c in targets.cities" :key="c" :label="c" :value="c" />
          </el-select>
          <div class="form-hint">
            选项来自学校库，与 t_school.city 逐字一致（不带「市」字）。
            手输「贵阳市」会和学校表里的「贵阳」匹配不上，广告对任何人都不生效。
          </div>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 3" label="校区" prop="schoolIds">
          <el-select
            v-model="form.schoolIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="按名称搜索，可多选"
            style="width: 100%"
          >
            <el-option-group v-for="g in schoolGroups" :key="g.city" :label="g.city">
              <el-option v-for="s in g.schools" :key="s.id" :label="s.name" :value="String(s.id)">
                <span>{{ s.name }}</span>
                <span class="opt-code">{{ g.city }}</span>
              </el-option>
            </el-option-group>
          </el-select>
          <div class="form-hint">
            已选 {{ form.schoolIds.length }} 所校区。一个广告位可同时投多所学校，
            改完请确认这条广告位的素材对这些学校都合适。
          </div>
        </el-form-item>
        <el-form-item label="素材上限" prop="maxCount">
          <el-input-number v-model="form.maxCount" :min="1" :max="20" />
        </el-form-item>
        <!-- 尺寸不是一个文本框，而是「小程序能渲染的几种高度」里选一种：
             选了之后小程序真的按这个高度撑容器（高度由接口下发），
             所以这里能选的值就是真实效果，不再出现「后台写 750x300、真机 240」。 -->
        <el-form-item label="展示规格" prop="sizeCode">
          <el-select v-model="form.sizeCode" placeholder="选择小程序支持的规格" style="width: 100%">
            <el-option v-for="s in sizeOptions" :key="s.code" :value="s.code" :label="s.label">
              <span>{{ s.label }}</span>
              <span class="opt-code">建议 {{ s.pixelHint }}</span>
            </el-option>
          </el-select>
          <div v-if="currentSize" class="size-row">
            <div class="size-box">
              <div class="size-rect" :style="{ aspectRatio: currentSize.widthRpx + ' / ' + currentSize.heightRpx }"></div>
            </div>
            <div class="form-hint">
              小程序按 <b>{{ currentSize.heightRpx }}rpx</b> 高度渲染（{{ currentSize.sizeText }}，比例 {{ currentSize.ratio }}:1），
              素材请按 <b>{{ currentSize.pixelHint }}</b> 制作。
            </div>
          </div>
          <el-alert v-if="sizeOffDefault" type="warning" :closable="false" show-icon class="size-warn"
                    :title="'该位页面是按 ' + sizeOf(cmDefaultSize).sizeText + ' 排的版，换档后请自己确认留白没问题'" />
          <div v-if="!sizeOptions.length" class="form-hint">
            规格清单加载失败，保存时会自动使用该位的推荐规格。
          </div>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import SearchForm from '@/components/SearchForm.vue'
import PageTable from '@/components/PageTable.vue'
import { adApi } from '@/api/operate'
import { AD_POSITION, AD_SCOPE, AUDIT_STATUS, tagOf } from '@/utils/dict'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ keyword: '', position: null, status: null, page: 1, size: 10 })

/**
 * 可选编码来自后端注册表（AdSlotCatalog）——小程序会去拉的编码就是这里列出的这些。
 * 编码以前是手填的，填错一个字符就永远投不出去，且后台毫无提示。
 */
const slotOptions = ref([])

/**
 * 可选展示规格（AdSizeCatalog）。尺寸以前是自由文本框：
 * 后台填「800x600」也能存，而小程序容器高度是写死的，素材被 aspectFill 裁掉一截，
 * 两端谁也不报错。现在只能从这几种规格里选，小程序按同一份规格渲染。
 */
const sizeOptions = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const formRef = ref()

/**
 * 定向候选值：城市 / 校区。
 *
 * 两者都必须是下拉多选而不是输入框——手填「贵阳市」或手打 19 位校区 id
 * 都曾经导致「后台显示已上架、C 端永远不展示」，而且两端都不报错。
 */
const targets = reactive({ cities: [], schools: [] })

/** 校区按城市分组，81 条平铺在选择器里很难找 */
const schoolGroups = computed(() => {
  const map = new Map()
  for (const s of targets.schools) {
    const city = s.city || '其他'
    if (!map.has(city)) map.set(city, [])
    map.get(city).push(s)
  }
  return [...map.entries()].map(([city, schools]) => ({ city, schools }))
})

const form = reactive({
  name: '', code: '', scopeType: 1, cities: [], schoolIds: [],
  maxCount: 3, sizeCode: '', sort: 0
})

/** 城市 / 校区是否已选，取决于当前投放范围——两个字段互斥，不能都要求 */
function validateCities (rule, value, cb) {
  if (form.scopeType === 2 && !form.cities.length) {
    return cb(new Error('请至少选择一个城市'))
  }
  cb()
}
function validateSchools (rule, value, cb) {
  if (form.scopeType === 3 && !form.schoolIds.length) {
    return cb(new Error('请至少选择一个校区'))
  }
  cb()
}

const rules = {
  code: [{ required: true, message: '请选择广告位', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  sizeCode: [{ required: true, message: '请选择展示规格', trigger: 'change' }],
  cities: [{ validator: validateCities, trigger: 'change' }],
  schoolIds: [{ validator: validateSchools, trigger: 'change' }]
}

const currentOption = computed(() => optionOf(form.code))

/** 当前选中的规格对象（下拉刚打开还没选时按兜底规格展示，避免空白预览） */
const currentSize = computed(() => sizeOf(form.sizeCode))

/** 该位的推荐规格编码；用来判断「运营是不是改成了非推荐档」 */
const cmDefaultSize = computed(() => (currentOption.value ? currentOption.value.defaultSize : ''))

/** 选了非推荐规格：允许，但页面留白是按推荐值排的，必须提醒 */
const sizeOffDefault = computed(
  () => !!(form.sizeCode && cmDefaultSize.value && form.sizeCode !== cmDefaultSize.value)
)

/** 编码不在注册表里 = 小程序不会读取（历史脏数据 / 手工插库） */
function optionOf (code) {
  if (!code) return null
  return slotOptions.value.find((o) => o.code === code) || null
}

/** 规格清单拿不到时的兜底（与后端 AdSizeCatalog.DEFAULT_CODE 一致），只为模板不炸 */
const FALLBACK_SIZE = {
  code: 'banner_240', label: '标准横版 750×240', widthRpx: 750, heightRpx: 240,
  ratio: 3.13, pixelHint: '1080×346', sizeText: '750×240'
}

function sizeOf (code) {
  const key = code || FALLBACK_SIZE.code
  return sizeOptions.value.find((s) => s.code === key) || sizeOptions.value[0] || FALLBACK_SIZE
}

/**
 * 列表里展示的规格：优先库里存的 size_code；
 * 老数据为空时按该位的推荐规格显示——与小程序实际渲染高度一致（后端同样兜底）。
 */
function sizeTextOf (row) {
  const code = row.sizeCode || (optionOf(row.code) ? optionOf(row.code).defaultSize : '')
  return sizeOf(code).sizeText
}

/** 存了具体规格、且不是该位的推荐档 → 列表里打个「非推荐」提醒 */
function isSizeOffDefault (row) {
  const o = optionOf(row.code)
  if (!o || !row.sizeCode) return false
  return row.sizeCode !== o.defaultSize
}

function positionLabel (row) {
  const o = optionOf(row.code)
  if (o) return o.name
  return AD_POSITION[row.position] || row.position
}

function canOnline (row) {
  const o = optionOf(row.code)
  return !!o && o.connected
}

const invalidRows = computed(() => rows.value.filter((r) => !optionOf(r.code)))

/** 逗号分隔字符串 → 数组（后端存的是多选结果） */
function splitCsv (v) {
  if (!v) return []
  return String(v).split(',').map((x) => x.trim()).filter(Boolean)
}

function schoolName (id) {
  const s = targets.schools.find((x) => String(x.id) === String(id))
  return s ? s.name : id
}

/**
 * 列表里最多展示 2 个标签，其余折成 +N。
 * 不做整段文字：定向可能选了十几个校区，撑开列宽后其他列全被挤扁。
 */
function targetTags (row) {
  const all = row.scopeType === 3 ? splitCsv(row.schoolIds) : splitCsv(row.city)
  const labels = row.scopeType === 3 ? all.map(schoolName) : all
  const shown = labels.slice(0, 2).map((label, i) => ({ key: i, label }))
  return { shown, more: Math.max(0, labels.length - 2), full: labels.join('、') }
}

async function loadTargets () {
  try {
    const res = await adApi.targets()
    targets.cities = res.cities || []
    targets.schools = res.schools || []
  } catch (e) {
    // 拿不到候选值时选择器为空，但不该阻断列表浏览
    targets.cities = []
    targets.schools = []
  }
}

async function loadOptions () {
  // 三个接口互不阻塞：任意一个挂了，其它下拉/列表照常可用
  const [codes, sizes] = await Promise.all([
    adApi.slotCodes().catch(() => []),
    adApi.slotSizes().catch(() => [])
  ])
  slotOptions.value = codes || []
  sizeOptions.value = sizes || []
}

async function load () {
  loading.value = true
  try {
    const res = await adApi.slots(query)
    rows.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function reload () { query.page = 1; load() }
function reset () { query.keyword = ''; query.position = null; query.status = null; reload() }

function onCodeChange (code) {
  const o = optionOf(code)
  if (!o) return
  // 选中即带出默认值，运营不用再手打名称和尺寸
  if (!form.name || slotOptions.value.some((x) => x.name === form.name)) form.name = o.name
  // 规格跟着位走：换位就换成该位的推荐档（编辑时编码不可改，不会冲掉已选规格）
  form.sizeCode = o.defaultSize || ''
}

function openCreate () {
  editing.value = null
  Object.assign(form, {
    name: '', code: '', scopeType: 1, cities: [], schoolIds: [],
    maxCount: 3, sizeCode: '', sort: 0
  })
  dialogVisible.value = true
  formRef.value && formRef.value.clearValidate()
}

function openEdit (row) {
  editing.value = row
  const o = optionOf(row.code)
  Object.assign(form, {
    name: row.name, code: row.code, scopeType: row.scopeType,
    cities: splitCsv(row.city), schoolIds: splitCsv(row.schoolIds),
    maxCount: row.maxCount,
    // 老数据 size_code 为空：表单显示该位的推荐值，保存后落成明确规格
    sizeCode: row.sizeCode || (o ? o.defaultSize : ''),
    sort: row.sort
  })
  dialogVisible.value = true
  formRef.value && formRef.value.clearValidate()
}

/** 切换投放范围时清掉上一侧的错误提示（另一个字段会变成不显示） */
function onScopeChange () {
  formRef.value && formRef.value.clearValidate(['cities', 'schoolIds'])
}

async function submit () {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 只提交当前范围对应的那一侧，另一侧传空，后端会落 NULL
    const payload = {
      name: form.name,
      code: form.code,
      scopeType: form.scopeType,
      city: form.scopeType === 2 ? form.cities.join(',') : '',
      schoolIds: form.scopeType === 3 ? form.schoolIds.join(',') : '',
      maxCount: form.maxCount,
      // 尺寸只提交规格编码，size_desc 由后端派生（自由文本口径已经废弃）
      sizeCode: form.sizeCode,
      sort: form.sort
    }
    if (editing.value) {
      await adApi.updateSlot(editing.value.id, payload)
    } else {
      await adApi.createSlot(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openAudit (row) {
  const pass = await ElMessageBox.confirm('选择「确定」表示审核通过，允许上架；「取消」表示驳回',
    '审核广告位', { confirmButtonText: '通过', cancelButtonText: '驳回', type: 'warning' })
    .then(() => true).catch(() => false)
  let remark = null
  if (!pass) {
    const { value } = await ElMessageBox.prompt('填写驳回原因', '驳回', {}).catch(() => ({ value: null }))
    if (!value) return
    remark = value
  }
  await adApi.auditSlot(row.id, pass, remark)
  ElMessage.success(pass ? '已通过' : '已驳回')
  load()
}

async function toggle (row) {
  await adApi.setSlotStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  load()
}

async function remove (row) {
  await ElMessageBox.confirm('删除广告位「' + row.name + '」？(需先清空其下素材)', '危险操作', { type: 'warning' })
    .catch(() => false)
  await adApi.removeSlot(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  // 两个请求互不阻塞：候选值拿不到也要能看列表
  await Promise.all([loadOptions(), loadTargets()])
  load()
})
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.bad-code {
  display: inline-block;
  margin: 0 8px 4px 0;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(230, 162, 60, 0.12);
  color: var(--warning-600, #b88230);
  font-size: 12px;
}
.opt-code {
  float: right;
  margin-left: 16px;
  color: var(--text-3, #909399);
  font-size: 12px;
}
.form-hint {
  width: 100%;
  margin-top: 4px;
  color: var(--text-3, #909399);
  font-size: 12px;
  line-height: 1.5;
}
.target-tag { margin-right: 4px; }
.muted { color: var(--text-3, #909399); font-size: 12px; }

/* 规格预览：按真实宽高比画一个小色块，运营一眼能看出「这个档有多扁」 */
.size-row { display: flex; gap: 10px; align-items: flex-start; width: 100%; margin-top: 8px; }
.size-box {
  flex: 0 0 76px; width: 76px; padding: 4px;
  background: var(--fill-2, #f5f7fa); border-radius: 4px;
}
.size-rect {
  width: 100%;
  border-radius: 2px;
  background: linear-gradient(135deg, var(--brand-400, #a78bfa), var(--brand-600, #7c3aed));
}
.size-warn { margin-top: 8px; }
</style>
