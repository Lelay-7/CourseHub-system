#!/usr/bin/env bash
# =============================================================================
# 不高山选课通 — 停止后端
# =============================================================================
set -euo pipefail

APP_DIR="/opt/bugaoshan"
PID_FILE="$APP_DIR/backend/app.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "[INFO] PID 文件不存在，后端可能已停止"
  exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
  echo "[INFO] 进程不存在 (PID $PID)，清理 PID 文件"
  rm -f "$PID_FILE"
  exit 0
fi

echo "[INFO] 正在停止后端 (PID $PID)..."
kill "$PID"

# 等待最多 15 秒
for i in $(seq 1 15); do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "[INFO] 后端已停止"
    rm -f "$PID_FILE"
    exit 0
  fi
  sleep 1
done

echo "[WARN] 优雅停止超时，强制终止..."
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "[INFO] 后端已强制停止"
