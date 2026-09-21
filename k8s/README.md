# Kubernetes 本地环境

部署实验 0 所需的共享基础设施：

```bash
kubectl apply -f k8s/infrastructure.yaml
kubectl -n tiny-oomall get pods
kubectl -n tiny-oomall wait --for=condition=ready pod -l app=mysql --timeout=180s
```

当前 `customer` 服务在宿主机启动时，另开两个终端转发 MySQL 和 Redis。这里使用 `13306`、`16379`，避免与本机已有的 MySQL、Redis 占用默认端口：

```bash
kubectl -n tiny-oomall port-forward service/mysql 13306:3306
```

```bash
kubectl -n tiny-oomall port-forward service/redis 16379:6379
```

在第三个终端构建并启动 `customer`：

```bash
mvn -pl core,customer -am package -DskipTests
env SPRING_DATASOURCE_URL='jdbc:mysql://localhost:13306/customer?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' REDIS_PORT=16379 \
  java -jar customer/target/customer-0.1.0-SNAPSHOT.jar
```

验证：

```bash
curl http://127.0.0.1:8081/actuator/health
```

不要从根工程执行 `mvn spring-boot:run` 或 `mvn -pl customer -am spring-boot:run`：根工程是聚合模块，没有 Spring Boot 主类。

## 终止本次运行

`customer` 和两个 `kubectl port-forward` 都在前台运行。在各自的终端按 `Ctrl-C` 即可停止；不会删除 Kubernetes 中的 MySQL、Redis 数据卷。

如需清理本地基础设施资源：

```bash
kubectl delete -f k8s/infrastructure.yaml
```

该命令会删除 `tiny-oomall` 命名空间及其中的 PVC；存储类可能继续删除底层卷。只想停止本次开发时，不要执行它，使用各终端的 `Ctrl-C` 即可。

后续实验为各业务服务增加容器镜像与 Deployment 后，它们会通过命名空间内的 `mysql`、`redis` 等 Service 名称访问依赖，而不需要宿主机端口转发。

开发用凭据在 `infrastructure-credentials` Secret 中；生产环境应改为外部密钥管理服务或由 CI 注入，不能提交真实密钥。
