/**
 * 实体 ↔ 数据库列一致性检查
 * 用法：node scripts/db-schema-check.js
 *
 * 为什么需要它：
 *   2026-10-27 的事故——`User` 实体先加了 `verify_source` 等 4 个字段，
 *   迁移脚本写好了但没在库上执行。MyBatis-Plus 会把实体所有
 *   `exist != false` 的字段带进 SQL，于是任何读写 t_user 全部
 *   `Unknown column 'verify_source'`，表现为「微信登录失败，请重试」，
 *   登录、我的页、发布一起挂掉。
 *
 *   静态检查（security-baseline-check.js）只能发现「压根没写迁移」，
 *   发现不了「写了但没执行」。这个脚本直连数据库比对，把后者也堵住。
 *
 * 连接参数（默认对齐 application.yml 的 localhost 默认值）：
 *   MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_USER=root
 *   MYSQL_PASSWORD=123456 MYSQL_DB=guizhou_campus_fun
 *
 * 退出码：0 一致 / 1 有缺失列或无法连接
 */
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const ROOT = path.join(__dirname, '..')

const HOST = process.env.MYSQL_HOST || 'localhost'
const PORT = process.env.MYSQL_PORT || '3306'
const USER = process.env.MYSQL_USER || 'root'
const PASSWORD = process.env.MYSQL_PASSWORD || '123456'
const DB = process.env.MYSQL_DB || 'guizhou_campus_fun'

/** 常见安装位置 + PATH 兜底 */
const MYSQL_CANDIDATES = [
  'mysql',
  'D:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe',
  'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe',
  '/c/xampp/mysql/bin/mysql.exe',
  '/usr/bin/mysql',
  '/usr/local/bin/mysql'
]

function findMysql() {
  for (const c of MYSQL_CANDIDATES) {
    try {
      execFileSync(c, ['--version'], { stdio: 'ignore' })
      return c
    } catch (e) {
      /* 继续试下一个 */
    }
  }
  return null
}

function camelToSnake(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
}

/** 收集所有实体：@TableName("xxx") + 持久化字段 */
function collectEntities() {
  const out = []
  const roots = fs
    .readdirSync(path.join(ROOT, 'backend'))
    .filter((d) => d.startsWith('campus-'))
    .map((d) => path.join(ROOT, 'backend', d, 'src', 'main', 'java'))

  function walk(dir) {
    if (!fs.existsSync(dir)) return
    for (const name of fs.readdirSync(dir)) {
      const p = path.join(dir, name)
      const st = fs.statSync(p)
      if (st.isDirectory()) walk(p)
      else if (name.endsWith('.java') && dir.replace(/\\/g, '/').endsWith('/entity')) {
        const src = fs.readFileSync(p, 'utf8')
        const table = /@TableName\(\s*(?:value\s*=\s*)?"([a-z0-9_]+)"/i.exec(src)
        if (!table) continue
        const fields = []
        const lines = src.split(/\r?\n/)
        lines.forEach((line, i) => {
          const m = /^[ \t]*private[ \t]+(?!static)([\w<>\[\], .]+?)[ \t]+(\w+)[ \t]*;/.exec(line)
          if (!m) return
          // 往上找最近的注解行，判断是否 exist = false
          for (let j = i - 1; j >= 0 && j >= i - 3; j--) {
            const prev = lines[j].trim()
            if (prev.startsWith('@TableField') && /exist\s*=\s*false/.test(prev)) return
            if (!prev.startsWith('@') && prev !== '') break
          }
          fields.push(camelToSnake(m[2]))
        })
        out.push({ file: path.relative(ROOT, p), table: table[1], fields })
      }
    }
  }
  roots.forEach(walk)
  return out
}

function queryColumns(mysqlBin) {
  const sql =
    "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS " +
    `WHERE TABLE_SCHEMA='${DB}'`
  const raw = execFileSync(
    mysqlBin,
    ['-h', HOST, '-P', PORT, '-u', USER, `-p${PASSWORD}`, '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }
  )
  const map = new Map()
  for (const line of raw.split(/\r?\n/)) {
    if (!line.trim()) continue
    const [table, col] = line.split('\t')
    if (!table || !col) continue
    if (!map.has(table)) map.set(table, new Set())
    map.get(table).add(col.toLowerCase())
  }
  return map
}

function main() {
  const mysqlBin = findMysql()
  if (!mysqlBin) {
    console.error('✗ 找不到 mysql 客户端。请把 mysql.exe 所在目录加入 PATH，')
    console.error('  或用 MYSQL_BIN 指定绝对路径后重跑。')
    process.exit(1)
  }

  const entities = collectEntities()
  if (!entities.length) {
    console.error('✗ 没有解析到任何实体，检查 backend/*/entity 目录是否存在')
    process.exit(1)
  }

  let dbCols
  try {
    dbCols = queryColumns(process.env.MYSQL_BIN || mysqlBin)
  } catch (e) {
    const detail = (e.stderr || e.message || String(e)).toString().split('\n')[0]
    console.error(`✗ 连不上数据库 ${USER}@${HOST}:${PORT}/${DB}：${detail}`)
    process.exit(1)
  }

  const problems = []
  const missingTables = []
  for (const ent of entities) {
    const cols = dbCols.get(ent.table)
    if (!cols) {
      missingTables.push(ent.table)
      continue
    }
    const miss = ent.fields.filter((f) => !cols.has(f))
    if (miss.length) problems.push({ table: ent.table, file: ent.file, miss })
  }

  console.log('')
  console.log('数据库列一致性检查')
  console.log('─'.repeat(60))
  console.log(`实体 ${entities.length} 个 / 表 ${dbCols.size} 张 / 库 ${DB}@${HOST}`)

  if (missingTables.length) {
    console.log(`  ✗ 库里不存在的表：${missingTables.join(', ')}`)
    console.log('     处理：先执行 backend/sql/schema.sql 建表')
  }
  for (const p of problems) {
    console.log(`  ✗ ${p.table} 缺列：${p.miss.join(', ')}`)
    console.log(`     实体 ${p.file}`)
    console.log('     处理：补/执行 backend/sql/migration-*.sql，否则该表所有读写都会 Unknown column')
  }
  console.log('─'.repeat(60))
  const bad = problems.length + missingTables.length
  console.log(bad ? `未通过：${problems.length} 张表缺列、${missingTables.length} 张表不存在` : '通过：实体字段与数据库列完全一致')
  process.exit(bad ? 1 : 0)
}

main()
