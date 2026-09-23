#!/usr/bin/env bash
# ============================================================
# MySQL 逻辑备份（Linux 服务器用）
#   在运行中的 MySQL 容器内执行 mysqldump，宿主机无需安装 mysql 客户端
#
# 用法：
#   chmod +x deploy/mysql-backup.sh
#   ./deploy/mysql-backup.sh
#
# crontab（每天 02:30）：
#   30 2 * * * cd /opt/diary-fullstack-platform && ./deploy/mysql-backup.sh >> deploy/backups/backup.log 2>&1
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$SCRIPT_DIR/backups"
RETAIN_DAYS=7
SERVICE=mysql

cd "$REPO_ROOT"

if [[ ! -f .env ]]; then
    echo "找不到 $REPO_ROOT/.env" >&2
    exit 1
fi

# 只取需要的键；不 source 整个 .env，避免其中含特殊字符的值被执行
DB_NAME="$(grep -E '^DB_NAME=' .env | head -1 | cut -d= -f2- | tr -d '\r' || true)"
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' .env | head -1 | cut -d= -f2- | tr -d '\r' || true)"
DB_NAME="${DB_NAME:-diary}"

if [[ -z "$DB_PASSWORD" ]]; then
    echo "未在 .env 中找到 DB_PASSWORD" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/diary-$STAMP.sql"

echo "开始备份 $DB_NAME ..."

# 密码经 MYSQL_PWD 传入，不出现在容器内的进程命令行里
docker compose exec -T -e "MYSQL_PWD=$DB_PASSWORD" "$SERVICE" \
    mysqldump -uroot --single-transaction --routines --triggers \
    --databases "$DB_NAME" > "$TARGET"

SIZE="$(du -h "$TARGET" | cut -f1)"
echo "备份完成: $TARGET ($SIZE)"

# 清理过期备份
DELETED="$(find "$BACKUP_DIR" -maxdepth 1 -name 'diary-*.sql' -mtime "+$RETAIN_DAYS" -print -delete | wc -l)"
if [[ "$DELETED" -gt 0 ]]; then
    echo "已清理 $DELETED 份过期备份"
fi

echo "当前保留 $(find "$BACKUP_DIR" -maxdepth 1 -name 'diary-*.sql' | wc -l) 份备份（保留 $RETAIN_DAYS 天）"
