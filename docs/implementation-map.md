# 实现地图

当前仓库没有代码实现。下表说明各模块的设计输入和学生应完成的工程产物。

| 顺序 | 模块 | 设计输入 | 学生主要产出 |
| --- | --- | --- | --- |
| 1 | `core/customer` | ApiResponse、CurrentUser、Customer/Address/Session Port | Repository、Application Service、认证过滤器、地址归属测试 |
| 2 | `shop` | Shop 状态和管理 API | 商户申请、平台审核、暂停恢复及状态测试 |
| 3 | `product` | Product/OnSale/Snapshot、Stock Port、管理 API | 商品审核、上下架、条件扣减、Cache Aside 和并发测试 |
| 4 | `customer` | CartItem、ProductClient、Cart API | 合并购物车、归属检查、价格变化展示 |
| 5 | `prodorder` | Order、AddressSnapshot、OrderItem 和跨服务 Port | 订单快照、幂等创建、取消归还库存、状态测试 |
| 6 | `payment` | PayTrans、PayAdaptor、Fake Callback API | FakePay、金额校验、回调幂等和主动查询 |
| 7 | `product/payment/prodorder` | V1 Event、Transaction Listener/Consumer 契约 | 可靠发布、回查、消费去重和死信演练 |
| 8 | `prodorder` | 订单状态机 | 商户标记发货、顾客确认收货、资源归属测试 |
| 9 | `elasticsearch` | ProductChangedV1、ProductDocument、Search API | 增量索引、旧事件过滤、全量重建和搜索测试 |
| 10 | `gateway` 与最小闭环服务 | Nacos 配置、Gateway Route、OpenFeign 依赖 | Client Adapter、超时、错误映射、Route Test 和端到端验收 |

## 注释约定

- `LEARNING TODO`：实现过程中明确留给学生完成的行为。
- `Port`/接口：依赖方向边界；Infrastructure 才实现 MySQL、Redis、Elasticsearch、HTTP 或 MQ 细节。
- `Snapshot`：跨服务只读契约，不能替换为另一个模块的 Entity/PO。
- 已实现的领域方法：用于演示不变量和单元测试，不代表对应 Use Case 已完成。
