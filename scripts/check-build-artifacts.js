/**
 * 构建产物自检
 * 用法：node scripts/check-build-artifacts.js
 *
 * 目的：拦住一类「源码没问题、产物不能跑」的事故。
 *
 * 背景（真实踩过）：用 IDE（VS Code / IDEA 配 Eclipse 编译器）跑服务时，
 * 改代码过程中编译失败，IDE 仍会把**带错误桩的 class** 写进 target/classes，
 * 桩里只有一句 throw new Error("Unresolved compilation problem: ...")。
 * 此时如果跑 mvn install，Maven 默认增量编译只看「class 是否比 java 新」，
 * 发现 class 更新 → 跳过编译 → 桩 class 被原样打进 jar。
 * 表现就是线上某个接口必然 500，堆栈里是 java.lang.Error 而不是业务异常，
 * 排查时极容易误判成「依赖没装好」而反复重启浪费一小时。
 *
 * 三道检查：
 *   1. target/classes 里不得出现 "Unresolved compilation problem"
 *   2. 打出来的 jar 内部同样不得出现（jar 才是真正上线的产物）
 *   3. build-jars/ 若存在，不得比 target 的 jar 旧（否则部署时传上去的是老代码）
 *
 * 纯读文件，不连库不启服务，可直接进 CI / 提交前钩子。
 */
const fs = require('fs')
const path = require('path')
const { execSync } = require('child_process')

const ROOT = path.join(__dirname, '..')
const BACKEND = path.join(ROOT, 'backend')
const MARKER = 'Unresolved compilation problem'
const failures = []
const warnings = []

function fail(rule, detail) {
  failures.push({ rule, detail })
}

function warn(rule, detail) {
  warnings.push({ rule, detail })
}

function listDirs(parent) {
  if (!fs.existsSync(parent)) return []
  return fs
    .readdirSync(parent, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.startsWith('campus-'))
    .map((d) => d.name)
}

/** 在目录下找包含指定字面量的文件（class 是二进制，必须按 Buffer 搜） */
function grepDir(dir, needle, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    let st
    try {
      st = fs.statSync(p)
    } catch {
      continue
    }
    if (st.isDirectory()) {
      grepDir(p, needle, out)
    } else if (st.isFile()) {
      let buf
      try {
        buf = fs.readFileSync(p)
      } catch {
        continue
      }
      if (buf.includes(needle)) out.push(p)
    }
  }
  return out
}

function jarName(mod) {
  return `${mod}-1.0.0.jar`
}

const modules = listDirs(BACKEND)
if (!modules.length) {
  console.log('未找到 backend/campus-* 模块，跳过')
  process.exit(0)
}

// ---------------------------------------------------------------- 1. target/classes
for (const mod of modules) {
  const classesDir = path.join(BACKEND, mod, 'target', 'classes')
  if (!fs.existsSync(classesDir)) continue
  const hits = grepDir(classesDir, MARKER)
  if (hits.length) {
    const rel = hits.slice(0, 3).map((p) => path.relative(ROOT, p)).join(', ')
    fail(
      '编译桩进产物',
      `${mod}/target/classes 有 ${hits.length} 个带 ECJ 错误桩的 class（${rel}）—— ` +
        'IDE 编译失败留下的，必须 `mvn clean install` 重编，光重启服务没用'
    )
  }
}

// ---------------------------------------------------------------- 2. jar 内部
let unzipAvailable = true
try {
  execSync('unzip -v', { stdio: 'ignore' })
} catch {
  unzipAvailable = false
  warn('编译桩进产物', '未找到 unzip，跳过 jar 内部扫描（target/classes 已检查）')
}

if (unzipAvailable) {
  for (const mod of modules) {
    const jar = path.join(BACKEND, mod, 'target', jarName(mod))
    if (!fs.existsSync(jar)) continue
    let classes = []
    try {
      classes = execSync(`unzip -Z1 "${jar}"`, { encoding: 'utf8' })
        .split(/\r?\n/)
        .filter((l) => l.startsWith('BOOT-INF/classes/') && l.endsWith('.class'))
    } catch {
      warn('编译桩进产物', `${mod} 的 jar 无法列出内容，已跳过`)
      continue
    }
    const bad = []
    for (const entry of classes) {
      let buf
      try {
        buf = execSync(`unzip -p "${jar}" "${entry}"`, {
          encoding: 'buffer',
          maxBuffer: 16 * 1024 * 1024,
        })
      } catch {
        continue
      }
      if (buf.includes(MARKER)) bad.push(entry)
      if (bad.length >= 3) break
    }
    if (bad.length) {
      fail('编译桩进产物', `${jarName(mod)} 内含错误桩 class：${bad.join(', ')}`)
    }
  }
}

// ---------------------------------------------------------------- 3. 部署暂存目录新旧
const staging = path.join(ROOT, 'build-jars')
if (fs.existsSync(staging)) {
  for (const mod of modules) {
    const built = path.join(BACKEND, mod, 'target', jarName(mod))
    const staged = path.join(staging, jarName(mod))
    if (!fs.existsSync(built) || !fs.existsSync(staged)) continue
    const a = fs.statSync(built).mtimeMs
    const b = fs.statSync(staged).mtimeMs
    // 容差 2 秒：copy 之后 mtime 会被保留/微调，避免误报
    if (a - b > 2000) {
      warn(
        '部署暂存过期',
        `build-jars/${jarName(mod)} 比 target 里的旧 ${Math.round((a - b) / 60000)} 分钟 —— ` +
          '按文档从这里上传会漏掉最新修复'
      )
    }
  }
}

// ---------------------------------------------------------------- 输出
console.log('')
console.log('构建产物自检')
console.log('─'.repeat(60))
for (const w of warnings) console.log('  ⚠ ' + w.rule + '：' + w.detail)
for (const f of failures) console.log('  ✗ ' + f.rule + '：' + f.detail)
console.log('─'.repeat(60))
console.log(`失败 ${failures.length} 项，告警 ${warnings.length} 项，检查模块 ${modules.length} 个`)
console.log(failures.length ? '未通过：产物不可直接发布' : '通过：产物与源码一致')
process.exit(failures.length ? 1 : 0)
