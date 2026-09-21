# tiny-oomall

tiny-oomall 是一个覆盖需求、设计、实现、测试和部署的 Spring Boot 商城教学项目。

当前已完成工程基线、`customer` 服务，以及 `shop` 服务的商户注册登录、店铺申请、平台审核和上下线流程。Java 包名统一为 `cn.edu.ecnu.oomall`，不再使用 `xmu`。

## 快速启动

`tiny-oomall` 根工程是 Maven 聚合工程，不是 Spring Boot 应用，不能在根目录执行 `mvn spring-boot:run`。当前可独立运行的业务模块是 `customer` 和 `shop`。

先部署基础设施：

```bash
kubectl apply -f k8s/infrastructure.yaml
kubectl -n tiny-oomall wait --for=condition=ready pod -l app=mysql --timeout=180s
```

```bash
kubectl -n tiny-oomall port-forward service/mysql 3307:3306
```

```bash
kubectl -n tiny-oomall port-forward service/redis 6380:6379
```

两个端口转发都要保持运行。随后可按需要启动一个业务服务。

启动 `shop`：

```bash
mvn -pl core,shop -am package -DskipTests
java -jar shop/target/shop-0.1.0-SNAPSHOT.jar
```

验证 `shop`：

```bash
curl http://127.0.0.1:8082/actuator/health
```

启动 `customer` 时需要覆盖默认连接端口：

```bash
mvn -pl core,customer -am package -DskipTests
env SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/customer?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' REDIS_PORT=6380 \
  java -jar customer/target/customer-0.1.0-SNAPSHOT.jar
```

验证 `customer`：

```bash
curl http://127.0.0.1:8081/actuator/health
```

`customer` 默认监听 `8081`，`shop` 默认监听 `8082`；健康检查均为 `GET /actuator/health`。业务接口统一前缀为 `/api`，会话通过 `X-Session-Id` 请求头传递。接口调用示例见 [examples/customer-api-curl.sh](examples/customer-api-curl.sh) 与 [examples/shop-api-curl.sh](examples/shop-api-curl.sh)。基础设施由 [k8s/infrastructure.yaml](k8s/infrastructure.yaml) 部署到 `tiny-oomall` 命名空间。

## 终止服务

业务服务和两个端口转发均以前台方式运行。在对应终端按 `Ctrl-C` 即可停止。基础设施会继续在 Kind 集群中运行，供下次启动复用。

## 断点调试

先保持 MySQL、Redis 的端口转发运行，再执行：

```bash
./scripts/debug-customer.sh
```

脚本会构建并启动 `customer`，同时在 `127.0.0.1:5005` 开放 JDWP 调试端口。IntelliJ IDEA 新建 `Remote JVM Debug`，Host 填 `localhost`、Port 填 `5005`；VS Code 可直接使用本地 `.vscode/launch.json` 中的调试配置。连接后可在 `CustomerController`、`CustomerService` 或 `SessionFilter` 设置断点。

## 阅读入口

1. [tiny-oomall 精简商城系统设计与实现指导书](docs/simple-mall-engineering-lab.md)
   主线文档。直接说明系统模块、功能、流程、技术、测试和部署。
2. [开发实验手册](docs/development-manual.md)
   面向初级开发人员，按业务优先的实验顺序说明 API 契约、处理逻辑、测试验收、独立微服务条件和 OOMALL 代码参考。
3. [需求分析、用例与 UML 建模](docs/requirements-and-uml.md)
   保存系统边界、文字用例、活动图、系统顺序图、领域类图、状态机和验收矩阵。
4. [架构约束](docs/architecture.md)
   在出现跨模块协作后阅读。
5. [实现地图](docs/implementation-map.md)
   核对各模块的核心类、工程任务和测试产物。
6. [消息契约](docs/message-contracts.md)
   说明支付、订单、商品和搜索之间的 RocketMQ 事件。

## 业务范围

系统只实现顾客、商户、平台管理员和支付平台参与的最小交易闭环：

```text
商户申请店铺 → 平台审核 → 商户提交并上架商品
→ 顾客加入购物车 → 创建订单 → 支付
→ 商户标记发货 → 顾客确认收货
```

收货地址使用普通文本。项目不实现优惠、售后、退款、仓储、运费计算、配送渠道、运单和轨迹。

## 使用方式

开发一个模块时：

1. 从主指导书确认模块职责、功能、流程和技术；
2. 阅读相关用例与 UML；
3. 建立接口、迁移和自动化测试；
4. 完成实现并执行模块验收；
5. 按推荐顺序进行跨模块联调和部署。

需求、UML、接口、代码和测试必须保持可追踪关系。
