#!/usr/bin/env bash
# =============================================================================
# 不高山选课通 — 启动后端
# =============================================================================
set -euo pipefail

APP_DIR="/opt/bugaoshan"
JAR_PATH="$APP_DIR/backend/bugaoshan-backend.jar"
LOG_FILE="$APP_DIR/logs/app.log"
PID_FILE="$APP_DIR/backend/app.pid"
UPLOAD_DIR="$APP_DIR/backend/uploads"

# ---- 前置检查 ----
if [ ! -f "$JAR_PATH" ]; then
  echo "[ERROR] JAR 文件不存在: $JAR_PATH"
  exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "[ERROR] 环境变量 JWT_SECRET 未设置"
  exit 1
fi

if [ -z "${DB_PASSWORD:-}" ] || [ "$DB_PASSWORD" = "CHANGE_ME" ]; then
  echo "[ERROR] 环境变量 DB_PASSWORD 未设置或仍为占位值"
  exit 1
fi

# ---- 已运行检查 ----
if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo "[INFO] 后端已在运行 (PID $PID)"
    exit 0
  else
    echo "[WARN] PID 文件存在但进程无效，清理后重新启动"
    rm -f "$PID_FILE"
  fi
fi

# ---- 目录准备 ----
mkdir -p "$APP_DIR/logs"
mkdir -p "$UPLOAD_DIR"

# ---- 启动 ----
echo "[INFO] 启动后端..."
nohup java -jar "$JAR_PATH" >> "$LOG_FILE" 2>&1 &
PID=$!
echo "$PID" > "$PID_FILE"
echo "[INFO] 后端已启动 (PID $PID)"
echo "[INFO] 日志: $LOG_FILE"
