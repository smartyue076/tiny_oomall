#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
run_dir="${TMPDIR:-/tmp}/tiny-oomall"
mkdir -p "$run_dir"
cd "$project_root"

pid_file="$run_dir/customer.pid"
mysql_pid_file="$run_dir/mysql-port-forward.pid"
redis_pid_file="$run_dir/redis-port-forward.pid"

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "Docker Desktop 未运行，请先启动 Docker Desktop。" >&2
    exit 1
fi

if ! kubectl cluster-info >/dev/null 2>&1; then
    echo "Kubernetes 集群不可用，请先启动 kind 集群：kind create cluster --name tiny-oomall" >&2
    exit 1
fi

is_running() {
    [[ -f "$1" ]] && kill -0 "$(cat "$1")" 2>/dev/null
}

wait_for_port() {
    local port="$1"
    local name="$2"
    local attempt=0

    until nc -z 127.0.0.1 "$port" >/dev/null 2>&1; do
        attempt=$((attempt + 1))
        if (( attempt >= 30 )); then
            echo "$name 端口 127.0.0.1:$port 未就绪，请查看 $run_dir/${name}-port-forward.log" >&2
            exit 1
        fi
        sleep 1
    done
}

if is_running "$pid_file"; then
    echo "customer 已经运行，PID $(cat "$pid_file")"
    exit 0
fi

kubectl apply -f k8s/infrastructure.yaml
kubectl -n tiny-oomall wait --for=condition=ready pod -l app=mysql --timeout=180s
kubectl -n tiny-oomall wait --for=condition=ready pod -l app=redis --timeout=180s

if ! is_running "$mysql_pid_file"; then
    nohup kubectl -n tiny-oomall port-forward service/mysql 13306:3306 \
        >"$run_dir/mysql-port-forward.log" 2>&1 &
    echo $! >"$mysql_pid_file"
fi

if ! is_running "$redis_pid_file"; then
    nohup kubectl -n tiny-oomall port-forward service/redis 16379:6379 \
        >"$run_dir/redis-port-forward.log" 2>&1 &
    echo $! >"$redis_pid_file"
fi

wait_for_port 13306 "mysql"
wait_for_port 16379 "redis"

mvn -pl core,customer -am package -DskipTests

nohup env \
    SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13306/customer?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
    REDIS_PORT=16379 \
    java -jar customer/target/customer-0.1.0-SNAPSHOT.jar \
    >"$run_dir/customer.log" 2>&1 &

echo $! >"$pid_file"
echo "customer 启动中，PID $(cat "$pid_file")"
echo "日志: $run_dir/customer.log"
echo "健康检查: curl http://127.0.0.1:8081/actuator/health"
