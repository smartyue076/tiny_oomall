#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"

mvn -pl core,customer -am package -DskipTests

exec env \
  SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13306/customer?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
  REDIS_PORT=16379 \
  java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 \
  -jar customer/target/customer-0.1.0-SNAPSHOT.jar
