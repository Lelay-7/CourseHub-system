#!/usr/bin/env bash
# =============================================================================
# 不高山选课通 — 重启后端
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[INFO] 停止后端..."
bash "$SCRIPT_DIR/stop-backend.sh"

sleep 2

echo "[INFO] 启动后端..."
bash "$SCRIPT_DIR/start-backend.sh"

echo "[INFO] 重启完成"
