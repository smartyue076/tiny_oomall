#!/usr/bin/env bash
set -euo pipefail

run_dir="${TMPDIR:-/tmp}/tiny-oomall"

stop_process() {
    local pid_file="$1"
    local name="$2"

    if [[ ! -f "$pid_file" ]]; then
        return
    fi

    local pid
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
        echo "$name 已停止，PID $pid"
    else
        echo "$name 已不在运行，清理 PID 文件"
    fi
    rm -f "$pid_file"
}

stop_process "$run_dir/customer.pid" "customer"
stop_process "$run_dir/mysql-port-forward.pid" "MySQL 端口转发"
stop_process "$run_dir/redis-port-forward.pid" "Redis 端口转发"

echo "Kubernetes 基础设施和数据未删除。"
