#!/usr/bin/env bash
#
# 数据库加密备份
#
# 为什么必须加密：
#   库里的 phone / student_no / real_name / contact 已经是 AES-GCM 密文，
#   但 t_message、t_post、t_admin_user 等表仍是明文；一份 .sql 备份落到
#   随便一台机器或一个网盘上，就等于把全部用户数据泄出去了。
#
# 用法：
#   export CAMPUS_BACKUP_PASSPHRASE='一串足够长的口令'   # 或用 --passphrase-file
#   ./scripts/backup-db.sh [输出目录]
#
# 恢复：
#   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in backup.sql.enc -pass env:CAMPUS_BACKUP_PASSPHRASE | mysql -uroot -p guizhou_campus_fun
#
# 注意：
#   * 备份口令必须与数据分开保管（密码管理器 / KMS），不能和备份放在同一台机器；
#   * 建议配合 crontab 每日执行，并设置保留期（默认保留 7 天，见 KEEP_DAYS）。
set -euo pipefail

BACKUP_DIR="${1:-${CAMPUS_BACKUP_DIR:-./backups}}"
DB_HOST="${MYSQL_HOST:-127.0.0.1}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_NAME="${MYSQL_DB:-guizhou_campus_fun}"
DB_USER="${MYSQL_USER:-root}"
DB_PWD="${MYSQL_PWD:-}"
KEEP_DAYS="${KEEP_DAYS:-7}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

if [ -z "${CAMPUS_BACKUP_PASSPHRASE:-}" ]; then
  echo "错误：未设置 CAMPUS_BACKUP_PASSPHRASE，拒绝生成明文备份。" >&2
  echo "      生成方法：openssl rand -base64 32" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
PLAIN="$(mktemp "${TMPDIR:-/tmp}/campus-${STAMP}-XXXXXX.sql")"
ENC="${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.enc"

# 中途失败也要把明文临时文件删掉，否则 /tmp 里会留一份未加密的全量数据
cleanup() { rm -f "$PLAIN"; }
trap cleanup EXIT INT TERM

echo "[1/3] 导出 ${DB_NAME} ..."
DUMP_ARGS=(
  --single-transaction --quick --no-tablespaces
  --default-character-set=utf8mb4
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER"
)
if [ -n "$DB_PWD" ]; then
  MYSQL_PWD="$DB_PWD" "$MYSQLDUMP_BIN" "${DUMP_ARGS[@]}" "$DB_NAME" > "$PLAIN"
else
  "$MYSQLDUMP_BIN" "${DUMP_ARGS[@]}" "$DB_NAME" > "$PLAIN"
fi

echo "[2/3] AES-256-CBC 加密 ..."
# pbkdf2 + 200000 次迭代，抵御对口令的离线暴力破解
openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
  -in "$PLAIN" -out "$ENC" -pass env:CAMPUS_BACKUP_PASSPHRASE
chmod 600 "$ENC"

# 只有加密文件成功生成且非空，才认为这次备份有效
if [ ! -s "$ENC" ]; then
  echo "错误：加密备份文件为空，备份失败。" >&2
  exit 1
fi

echo "[3/3] 清理超过 ${KEEP_DAYS} 天的旧备份 ..."
find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.enc" -type f -mtime "+${KEEP_DAYS}" -print -delete || true

SIZE="$(du -h "$ENC" | cut -f1)"
echo ""
echo "完成：$ENC ($SIZE)"
echo "校验：openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in \"$ENC\" -pass env:CAMPUS_BACKUP_PASSPHRASE | head -5"
