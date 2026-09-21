# tiny-oomall 精简商城系统设计与实现指导书

> 业务范围：顾客购买商品，商户经营店铺并履行订单，平台管理员审核店铺与商品，支付平台完成付款。
> 工程形态：Spring Boot 微服务、独立数据所有权、同步 Contract 与领域事件协作。
> API 的 Method、Path、处理逻辑、错误分支与逐接口验收以[开发实验手册](development-manual.md)为实现契约；本文中的 API 表用于说明模块能力。

## 1. 系统功能

tiny-oomall 完成以下交易闭环：

~~~mermaid
flowchart LR
    A[商户申请店铺] --> B[平台管理员审核]
    B --> C[商户提交商品]
    C --> D[平台管理员审核]
    D --> E[商户上架销售]
    E --> F[顾客浏览并加入购物车]
    F --> G[顾客创建订单]
    G --> H[顾客支付]
    H --> I[商户标记发货]
    I --> J[顾客确认收货]
~~~

流程说明：店铺和商品通过审核后才能销售；创建订单时重新确认价格并扣减库存；支付成功后商户才能发货；顾客确认收货后订单完成。

### 1.1 功能范围

| 业务领域 | 必须实现的功能 |
| --- | --- |
| 顾客 | 注册、登录、退出、个人资料、收货地址 |
| 店铺 | 商户申请，平台管理员批准、拒绝、暂停和恢复 |
| 商品 | 类目、商品申请与审核、销售价格、库存、销售期、上下架 |
| 商品查询 | 在售列表、详情、关键词搜索 |
| 购物车 | 加入、修改数量、删除、查询 |
| 订单 | 创建、查询、取消待付款订单 |
| 支付 | FakePay 交易、回调、主动查询、重复通知处理 |
| 履约 | 商户查询本店订单并标记发货，顾客确认收货 |

不实现优惠、售后、退款、分账、发票、仓储、运费、配送渠道、运单和轨迹。收货地址只保存收件人、手机号和文本地址。

完整参与者、用例和验收分支见[需求分析、用例与 UML 建模](requirements-and-uml.md)。

## 2. 模块与技术栈

### 2.1 模块清单

| 模块 | 职责 | 数据 | 技术 |
| --- | --- | --- | --- |
| **core** | 统一响应、异常、身份上下文、审计、事件基类 | 无业务库 | Java 21、Spring Web、Validation |
| **customer** | 顾客、地址、Session、购物车 | customer_customer、customer_address、customer_cart | Spring Boot、MySQL、Redis、Spring Security |
| **shop** | 店铺申请、审核和经营状态 | shop_shop | Spring Boot、MySQL |
| **product** | 类目、商品、销售项、价格、库存、商品事实查询 | goods_category、goods_product、goods_onsale | Spring Boot、MySQL、Redis、RocketMQ |
| **prodorder** | 订单、订单项、快照、取消、付款、发货、收货状态 | order_order、order_item | Spring Boot、MySQL、RocketMQ |
| **payment** | 支付单、FakePay、回调幂等和状态查询 | payment_pay_trans | Spring Boot、MySQL、Redis、RocketMQ |
| **elasticsearch** | 商品搜索投影与关键词检索 | product 索引 | Spring Boot、Elasticsearch、RocketMQ |
| **gateway** | 统一入口、路由、认证信息、CORS、Request ID | 无业务库 | Spring Cloud Gateway、Nacos |

平台管理员功能位于 **shop** 和 **product**，不建立独立 platform 服务。商户发货属于订单生命周期，不建立额外服务。

### 2.2 基础设施

| 技术 | 用途 |
| --- | --- |
| MySQL 8 | 业务事实、唯一约束、事务和库存条件更新 |
| Redis | 登录 Session、商品缓存、短期幂等过滤 |
| RocketMQ | 支付成功、订单取消、商品变化事件 |
| Elasticsearch | 商品名称与简介的关键词检索 |
| Nacos | 服务注册发现和非敏感配置 |
| Gateway | 对外统一 API 入口 |
| Flyway | 数据库版本迁移 |
| Maven | 多模块构建 |
| JUnit 5、Mockito | 领域和应用测试 |
| Testcontainers | MySQL、Redis、Elasticsearch 集成测试 |
| Kubernetes | 本地与测试环境的基础设施编排 |

### 2.3 运行时组件图

~~~mermaid
flowchart LR
    Client --> Gateway
    Gateway --> Customer
    Gateway --> Shop
    Gateway --> Product
    Gateway --> Order[prodorder]
    Gateway --> Payment
    Gateway --> Search[elasticsearch]

    Customer --> Product
    Product --> Shop
    Order --> Customer
    Order --> Product
    Payment --> Order
    Payment --> FakePay

    Payment -.PaymentSucceededV1.-> MQ[RocketMQ]
    MQ --> Order
    Order -.OrderCanceledV1.-> MQ
    MQ --> Product
    Product -.ProductChangedV1.-> MQ
    MQ --> Search
    Search --> ES[Elasticsearch]

    Gateway --> Nacos
    Customer --> Nacos
    Shop --> Nacos
    Product --> Nacos
    Order --> Nacos
    Payment --> Nacos
    Search --> Nacos
~~~

各服务只访问自己的数据，禁止跨库 Join。

### 2.4 包分层

~~~text
adapter/
  web/             Controller、Request、Response
  message/         Event Consumer
application/
  command/         用例输入
  service/         用例编排、权限、事务
  view/            应用输出
domain/
  model/           Aggregate、Entity、Value Object
  repository/      Repository Port
infrastructure/
  persistence/     PO、Mapper、Repository Adapter
  client/          HTTP Client Adapter
  cache/           Redis Adapter
  message/         RocketMQ Adapter
~~~

依赖方向为 Adapter → Application → Domain。Controller 不直接访问 Mapper，Domain 不依赖 HTTP、Redis 或消息 SDK。

## 3. core 模块

### 3.1 功能

- ApiResponse 统一响应；
- ErrorCode、BusinessException 和 GlobalExceptionHandler；
- CurrentUser、Role 和资源归属检查；
- DomainEvent 统一事件字段；
- Clock、Request ID 和审计字段约定；
- 测试 Fixture 与 Testcontainers 基类。

core 是普通 Maven Library，不保存业务数据，也没有独立启动类。

### 3.2 关键类型

~~~java
public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }
}

public record CurrentUser(Long id, Role role, String tokenId) {
    public enum Role { CUSTOMER, MERCHANT, PLATFORM_ADMIN }
}
~~~

| 错误码 | HTTP Status | 含义 |
| --- | ---: | --- |
| AUTH_INVALID_CREDENTIAL | 401 | 登录凭据错误 |
| RESOURCE_FORBIDDEN | 403 | 无权操作资源 |
| SHOP_NOT_ACTIVE | 409 | 店铺不能经营 |
| PRODUCT_NOT_ON_SALE | 409 | 销售项不可购买 |
| STOCK_NOT_ENOUGH | 409 | 库存不足 |
| ORDER_STATE_INVALID | 409 | 订单状态不允许操作 |
| PAYMENT_AMOUNT_MISMATCH | 409 | 支付金额不一致 |
| DEPENDENCY_UNAVAILABLE | 503 | 依赖服务不可用 |

## 4. customer 模块

### 4.1 功能和规则

- 手机号注册、密码登录和退出；
- 密码只保存 BCrypt 或 Argon2 哈希；
- Token 为随机字符串并设置 TTL；
- 顾客维护自己的收货地址；
- 顾客管理自己的购物车；
- 同一顾客、同一销售项只有一个购物车项；
- 购物车价格只用于展示，下单重新取价。

### 4.2 领域模型

~~~mermaid
classDiagram
    class Customer {
        +Long id
        +String mobile
        +String passwordHash
        +String name
        +CustomerStatus status
        +verifyPassword(raw)
        +disable()
    }
    class CustomerAddress {
        +Long id
        +Long customerId
        +String consignee
        +String mobile
        +String address
        +boolean defaultAddress
        +change(command)
        +setDefault()
        +belongsTo(customerId)
    }
    class CustomerSession {
        +String tokenId
        +Long customerId
        +Role role
        +Instant expiresAt
    }
    class CartItem {
        +Long id
        +Long customerId
        +Long onSaleId
        +int quantity
        +long displayedPrice
        +merge(quantity)
        +changeQuantity(quantity)
        +priceChanged(currentPrice)
    }
    Customer "1" *-- "0..*" CustomerAddress
    Customer "1" --> "0..*" CustomerSession
    Customer "1" *-- "0..*" CartItem
~~~

### 4.3 核心类

| 类 | 主要属性 | 主要方法 |
| --- | --- | --- |
| Customer | id、mobile、passwordHash、name、status | register、verifyPassword、changeProfile、disable |
| CustomerAddress | id、customerId、consignee、mobile、address、defaultAddress | create、change、setDefault、belongsTo |
| CartItem | id、customerId、onSaleId、quantity、displayedPrice、version | create、merge、changeQuantity、priceChanged |
| CustomerApplicationService | CustomerRepository、SessionStore、PasswordEncoder | register、login、logout、profile |
| AddressApplicationService | AddressRepository | create、update、delete、list、getOwnedSnapshot |
| CartApplicationService | CartRepository、ProductClient | add、change、remove、list |

### 4.4 注册登录流程

~~~mermaid
sequenceDiagram
    actor C as 顾客
    participant U as customer
    participant DB as MySQL
    participant R as Redis

    C->>U: 注册手机号、密码、昵称
    U->>DB: 保存密码哈希
    U-->>C: 顾客标识
    C->>U: 登录
    U->>DB: 查询顾客并核对密码
    U->>R: 保存随机 Token 和 TTL
    U-->>C: Token、expiresAt
~~~

### 4.5 购物车流程

~~~mermaid
sequenceDiagram
    actor C as 顾客
    participant Cart as customer
    participant P as product

    C->>Cart: 加入 onSaleId、quantity
    Cart->>P: 查询展示快照
    alt 不可购买
        P-->>Cart: 原因
        Cart-->>C: 拒绝
    else 可购买
        P-->>Cart: name、price、maxQuantity
        Cart->>Cart: 新增或合并
        Cart-->>C: 更新后的购物车
    end
~~~

### 4.6 数据表和 API

~~~text
customer_customer:
id, mobile, password, name, status, audit fields

customer_address:
id, customer_id, consignee, mobile, address, be_default, audit fields

customer_cart:
id, customer_id, onsale_id, quantity, price, version, audit fields
~~~

| Method | Path | 功能 |
| --- | --- | --- |
| POST | /api/customers | 注册 |
| POST | /api/auth/sessions | 登录 |
| DELETE | /api/auth/sessions/current | 退出 |
| GET/POST | /api/addresses | 查询、新增地址 |
| PUT/DELETE | /api/addresses/{id} | 修改、删除自己的地址 |
| GET/POST | /api/cart/items | 查询、加入购物车 |
| PUT/DELETE | /api/cart/items/{id} | 修改、删除购物车项 |
| GET | /internal/customers/{customerId}/addresses/{id} | 地址快照 |

### 4.7 验收

覆盖重复手机号、密码哈希、错误密码、禁用账号、Session 过期、退出失效、地址越权、购物车越权、重复商品合并、商品下架和价格变化。

## 5. shop 模块

### 5.1 功能

- 商户申请店铺并查看审核结果；
- 平台管理员查看待审核申请；
- 批准或拒绝，拒绝必须填写原因；
- 暂停和恢复店铺；
- 向 product 和 prodorder 提供 ShopSnapshot。

### 5.2 状态机

~~~mermaid
stateDiagram-v2
    [*] --> PENDING: 商户提交
    PENDING --> ACTIVE: 平台批准
    PENDING --> REJECTED: 平台拒绝
    REJECTED --> PENDING: 修正并重提
    ACTIVE --> SUSPENDED: 平台暂停
    SUSPENDED --> ACTIVE: 平台恢复
~~~

只有 ACTIVE 店铺可以提交商品、上架和处理新订单。暂停不删除历史数据。

### 5.3 类、数据和 API

| 类 | 属性 | 方法 |
| --- | --- | --- |
| Shop | id、merchantId、name、contact、mobile、status、rejectReason、audit fields | apply、revise、approve、reject、suspend、resume、canOperate、belongsTo |
| ShopApplicationService | ShopRepository、Clock | apply、review、suspend、resume、getSnapshot |
| ShopRepository | 无状态 Port | save、findById、findByMerchantId、findPending、existsByName |

~~~text
shop_shop:
id, merchant_id, name, contact, mobile,
status, reject_reason, auditor_id, audit_time, audit fields
~~~

| Method | Path | 功能 |
| --- | --- | --- |
| POST | /api/shops | 商户申请 |
| GET | /api/shops/mine | 商户查看自己的店铺 |
| PUT | /api/shops/{id}/application | 修正并重提 |
| GET | /api/admin/shops?status=PENDING | 待审核列表 |
| PUT | /api/admin/shops/{id}/review | 批准或拒绝 |
| PUT | /api/admin/shops/{id}/suspend | 暂停 |
| PUT | /api/admin/shops/{id}/resume | 恢复 |
| GET | /internal/shops/{id} | ShopSnapshot |

验收覆盖重复名称、重复申请、商户自审、拒绝原因、并发审核、暂停恢复和资源归属。

## 6. product 模块

### 6.1 功能

- 类目管理；
- 商户提交和修改商品；
- 平台管理员审核、拒绝和禁售；
- 商户创建销售项并设置价格、库存、限购和销售期；
- 上下架；
- 顾客查询商品；
- 为购物车提供展示快照；
- 为订单提供成交快照；
- 条件扣减和幂等归还库存；
- Redis 商品缓存；
- 发布 ProductChangedV1。

### 6.2 领域模型

~~~mermaid
classDiagram
    class Product {
        +Long id
        +Long shopId
        +Long categoryId
        +String name
        +long originalPrice
        +String unit
        +ProductStatus status
        +approve()
        +reject(reason)
        +ban()
        +allowSale()
    }
    class OnSale {
        +Long id
        +Long productId
        +long price
        +int quantity
        +int maxQuantity
        +Instant beginTime
        +Instant endTime
        +OnSaleStatus status
        +activeAt(now)
        +deduct(quantity)
        +restore(quantity)
        +offSale()
    }
    Product "1" *-- "0..*" OnSale
~~~

Product 是商品资料，OnSale 是具有价格、库存和时间窗口的一次销售安排。

### 6.3 发布流程

~~~mermaid
flowchart TD
    A[商户提交商品] --> P[(PENDING_REVIEW)]
    P --> R[平台管理员审核]
    R --> D{结果}
    D -->|拒绝| X[(REJECTED)]
    X -->|修正重提| P
    D -->|批准| OK[(APPROVED)]
    OK --> S[商户设置价格、库存和销售期]
    S --> O[(ON_SALE)]
    O -->|下架/到期/售罄| OFF[(OFF_SALE)]
~~~

### 6.4 库存与缓存

库存扣减使用单条条件更新：

~~~sql
UPDATE goods_onsale
SET quantity = quantity - :requested,
    version = version + 1
WHERE id = :onSaleId
  AND quantity >= :requested
  AND status = :onSaleStatus;
~~~

影响行数为 0 表示不可售或库存不足。归还库存以 orderSn 或 eventId 幂等。

商品详情使用 Cache Aside：先查 Redis，未命中查 MySQL 并写入 TTL；审核、上下架、改价和库存归零后删除缓存。订单成交快照不能从缓存取得。

### 6.5 核心类和数据

| 类 | 属性 | 方法 |
| --- | --- | --- |
| Category | id、pid、name、status | create、rename、enable、disable |
| Product | id、shopId、categoryId、name、originalPrice、unit、description、status | submit、revise、approve、reject、ban、allowSale |
| OnSale | id、productId、price、quantity、maxQuantity、beginTime、endTime、status、version | create、activeAt、deduct、restore、offSale |
| ProductApplicationService | Repository、ShopClient、Cache、Publisher | submit、review、createOnSale、offSale、detail、snapshots、deduct、restore |

~~~text
goods_category:
id, pid, name, status, audit fields

goods_product:
id, shop_id, category_id, name, original_price, unit,
description, status, reject_reason, auditor_id, audit_time, audit fields

goods_onsale:
id, product_id, price, quantity, max_quantity,
begin_time, end_time, status, version, audit fields

stock_operation_record:
operation_id, order_sn, operation_type, payload, gmt_create
~~~

### 6.6 API 与验收

| Method | Path | 功能 |
| --- | --- | --- |
| POST/PUT | /api/merchant/products | 提交、修改商品 |
| POST | /api/merchant/products/{id}/onsales | 创建销售项 |
| PUT | /api/merchant/onsales/{id}/off | 下架 |
| GET | /api/admin/products?status=PENDING_REVIEW | 待审核商品 |
| PUT | /api/admin/products/{id}/review | 审核 |
| PUT | /api/admin/products/{id}/ban | 禁售 |
| GET | /api/products | 在售列表 |
| GET | /api/products/{onSaleId} | 商品详情 |
| POST | /internal/onsales/snapshots | 批量成交快照 |
| POST | /internal/onsales/deduct | 条件扣库存 |
| POST | /internal/onsales/restore | 幂等归还 |

验收覆盖店铺状态、商品审核、销售时间、商户归属、并发最后一件、重复归还、缓存命中与失效、Redis 故障降级和事件契约。

## 7. elasticsearch 模块

### 7.1 功能与模型

消费 ProductChangedV1，维护在售商品搜索投影；提供关键词、分页和排序；支持全量重建。搜索结果不作为价格和库存事实。

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| onSaleId、productId、shopId | keyword | 标识与过滤 |
| name | text + keyword | 全文检索和排序 |
| description | text | 全文检索 |
| price | long | 展示和排序 |
| purchasable | boolean | 可见性 |
| aggregateVersion | long | 防止旧事件覆盖 |
| updatedAt | date | 排序和诊断 |

~~~mermaid
sequenceDiagram
    participant P as product
    participant MQ as RocketMQ
    participant S as elasticsearch
    participant ES as Elasticsearch

    P->>MQ: ProductChangedV1
    MQ->>S: 投递
    S->>S: 检查 eventId 和 aggregateVersion
    alt 在售
        S->>ES: upsert 文档
    else 不可售
        S->>ES: 删除或隐藏
    end
~~~

API：GET /api/search/products，POST /internal/search/products/rebuild。

验收覆盖分词、分页、重复和旧版本事件、下架移除、索引清空重建，以及进入购买前由 product 再次核对。

## 8. prodorder 模块

### 8.1 功能和规则

- 从顾客地址与购物车创建订单；
- 重新取得商品状态、成交价和库存；
- 一张订单只属于一个顾客、一个商户；
- 保存商品与地址快照；
- requestId 幂等；
- 取消待付款订单并发布 OrderCanceledV1；
- 消费 PaymentSucceededV1；
- 商户标记发货；
- 顾客确认收货。

客户端提交的 customerId、price、amount 和 status 均不可信。应付金额等于订单项小计之和。

### 8.2 模型与状态

~~~mermaid
classDiagram
    class Order {
        +Long id
        +String orderSn
        +Long customerId
        +Long shopId
        +long productAmount
        +OrderStatus status
        +Instant paymentDeadline
        +payableAmount()
        +markPaid()
        +cancel()
        +markShipped()
        +complete()
    }
    class OrderItem {
        +Long onSaleId
        +Long productId
        +String productName
        +long price
        +int quantity
        +subtotal()
    }
    class AddressSnapshot {
        +String consignee
        +String mobile
        +String address
    }
    Order "1" *-- "1..*" OrderItem
    Order "1" *-- "1" AddressSnapshot
~~~

~~~mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: 创建并扣库存
    PENDING_PAYMENT --> PAID: PaymentSucceededV1
    PENDING_PAYMENT --> CANCELED: 顾客取消/付款超时
    PAID --> SHIPPED: 所属商户发货
    SHIPPED --> COMPLETED: 所属顾客确认收货
~~~

### 8.3 创建订单流程

~~~mermaid
sequenceDiagram
    actor C as 顾客
    participant O as prodorder
    participant U as customer
    participant P as product

    C->>O: cartItemIds、addressId、requestId
    O->>U: 取得地址和购物车选择
    U-->>O: AddressSnapshot、CartSelection
    O->>P: 取得成交快照
    P-->>O: shopId、name、price、status、stock
    O->>O: 检查单商户并计算金额
    O->>P: 条件扣库存(orderSn, items)
    alt 成功
        O->>O: 保存 Order 与 OrderItem
        O-->>C: 待付款订单
    else 失败
        O-->>C: 商品和原因
    end
~~~

商品扣减和订单保存不在同一事务。订单保存失败时同步归还库存；补偿失败进入重试记录和告警。取消成功后通过事件最终归还库存。

### 8.4 数据、API 与验收

~~~text
order_order:
id, order_sn, customer_id, shop_id,
consignee, mobile, address, product_amount,
status, payment_deadline, shipped_at, completed_at,
request_id, audit fields

order_item:
id, order_id, onsale_id, product_id,
product_name, price, quantity, gmt_create

event_consume_record:
event_id, event_type, aggregate_id, consume_time
~~~

| Method | Path | 功能 |
| --- | --- | --- |
| POST/GET | /api/orders | 创建、查询自己的订单 |
| GET | /api/orders/{id} | 订单详情 |
| PUT | /api/orders/{id}/cancel | 取消待付款订单 |
| PUT | /api/orders/{id}/confirm | 确认收货 |
| GET | /api/merchant/orders | 商户查询本店订单 |
| PUT | /api/merchant/orders/{id}/ship | 标记发货 |
| GET | /internal/orders/{id}/payable | 可支付订单快照 |

验收覆盖资源归属、单商户、价格变化、库存不足、并发、requestId、保存失败补偿、重复取消、支付事件乱序、跨店铺发货和错误状态迁移。

## 9. payment 模块

### 9.1 功能和规则

- 从 prodorder 获取应付金额；
- 创建本地支付单；
- PayAdaptor 调用 FakePay；
- 验证回调来源和金额；
- 处理重复回调；
- UNKNOWN 状态主动查询；
- 发布 PaymentSucceededV1。

金额只来自 OrderPayable。只有支付平台权威通知或查询结果能够确认 SUCCESS。

### 9.2 模型和状态

~~~mermaid
classDiagram
    class PayTrans {
        +Long id
        +String outNo
        +String orderSn
        +Long customerId
        +long amount
        +String channelTransNo
        +PayStatus status
        +succeed(channelNo, amount)
        +fail(reason)
        +markUnknown()
    }
    class PayAdaptor {
        <<interface>>
        +create(request) result
        +query(channelTransNo) status
    }
    PayTrans --> PayAdaptor
~~~

~~~mermaid
stateDiagram-v2
    [*] --> PENDING: 创建支付单
    PENDING --> SUCCESS: 权威成功且金额一致
    PENDING --> FAILED: 权威失败
    PENDING --> UNKNOWN: 调用结果不确定
    UNKNOWN --> SUCCESS: 主动查询成功
    UNKNOWN --> FAILED: 主动查询失败
~~~

### 9.3 支付流程

~~~mermaid
sequenceDiagram
    actor C as 顾客
    participant Pay as payment
    participant O as prodorder
    participant Fake as FakePay
    participant MQ as RocketMQ

    C->>Pay: 支付 orderId
    Pay->>O: 查询 OrderPayable
    O-->>Pay: orderSn、customerId、amount、deadline
    Pay->>Pay: 保存 PENDING PayTrans
    Pay->>Fake: create(outNo, amount)
    Fake-->>Pay: transNo、payUrl
    Pay-->>C: 支付凭据
    Fake-->>Pay: callback(outNo, transNo, amount, status)
    Pay->>Pay: 验证并幂等确认
    Pay->>MQ: PaymentSucceededV1
    MQ-->>O: 投递
    O->>O: 幂等标记 PAID
~~~

### 9.4 数据、API 与验收

~~~text
payment_pay_trans:
id, out_no, order_sn, customer_id, amount,
channel_trans_no, status, fail_reason, deadline,
success_time, version, audit fields

event_publish_record:
event_id, event_type, aggregate_id, payload,
status, retry_count, next_retry_time, gmt_create
~~~

| Method | Path | 功能 |
| --- | --- | --- |
| POST | /api/payments | 创建支付 |
| GET | /api/payments/{outNo} | 查询结果 |
| POST | /internal/fake-pay/callbacks | FakePay 回调 |
| POST | /internal/payments/{outNo}/query | 主动查询 |

验收覆盖金额篡改、订单所有者、状态与期限、金额不符、重复通知、唯一渠道号、UNKNOWN 恢复、可靠事件发布和 MQ 故障恢复。

## 10. 消息契约

详见[消息契约](message-contracts.md)。

| Topic | Producer | Consumer | 作用 |
| --- | --- | --- | --- |
| payment-succeeded-v1 | payment | prodorder | 确认订单付款 |
| order-canceled-v1 | prodorder | product | 归还库存 |
| product-changed-v1 | product | elasticsearch | 更新搜索投影 |

所有消息包含 eventId、version、occurredAt、aggregateId。Consumer 按 eventId 去重，校验版本，失败时抛异常触发重试。消息不能直接序列化 Entity 或 PO。

## 11. Gateway、Nacos 与 Contract

### 11.1 路由

| 路径 | 服务 |
| --- | --- |
| /api/auth/**、/api/customers/**、/api/addresses/**、/api/cart/** | customer |
| /api/shops/**、/api/admin/shops/** | shop |
| /api/products/**、/api/merchant/products/**、/api/admin/products/** | product |
| /api/orders/**、/api/merchant/orders/** | prodorder |
| /api/payments/** | payment |
| /api/search/** | elasticsearch |

Gateway 不暴露 /internal/**。它处理路由、CORS、Request ID、基础认证转发、请求大小和限流；业务服务仍负责角色和资源归属。

### 11.2 跨服务 Snapshot

| Contract | 提供者 | 使用者 | 字段 |
| --- | --- | --- | --- |
| ShopSnapshot | shop | product、prodorder | shopId、merchantId、status、canOperate |
| ProductBrief | product | customer | onSaleId、name、currentPrice、purchasable |
| ProductSnapshot | product | prodorder | onSaleId、productId、shopId、name、price、stock、purchasable |
| AddressSnapshot | customer | prodorder | consignee、mobile、address |
| OrderPayable | prodorder | payment | orderSn、customerId、amount、status、deadline |

Nacos 服务名、Path、Method、字段和错误码由 Contract Test 固定。写操作不能盲目重试，必须使用 requestId、orderSn、outNo 或 eventId。

## 12. 数据所有权

~~~text
customer: customer_customer、customer_address、customer_cart
shop: shop_shop
product: goods_category、goods_product、goods_onsale、stock_operation_record
prodorder: order_order、order_item、event_consume_record
payment: payment_pay_trans、event_publish_record
elasticsearch: product index
~~~

禁止 prodorder Join customer/product 表、payment 修改 order_order、customer 把购物车价格作为成交价、搜索结果作为价格库存事实，以及只在 Redis 保存不可恢复的业务数据。

## 13. 完整业务流程

~~~mermaid
flowchart TD
    A[商户申请店铺] --> B[平台审核店铺]
    B --> C[商户提交商品]
    C --> D[平台审核商品]
    D --> E[商户创建销售项]
    E --> F[商品进入搜索索引]
    F --> G[顾客浏览并加入购物车]
    G --> H[创建订单并扣库存]
    H --> Q{顾客决定}
    Q -->|取消| X[取消并发布库存归还事件]
    Q -->|支付| P[FakePay]
    P --> R{权威支付结果}
    R -->|未知/失败| H
    R -->|成功| S[支付成功事件]
    S --> T[(订单 PAID)]
    T --> U[商户标记发货]
    U --> V[(订单 SHIPPED)]
    V --> W[顾客确认收货]
    W --> Z[(订单 COMPLETED)]
~~~

## 14. 测试体系

| 测试 | 验证内容 | 外部组件 |
| --- | --- | --- |
| Domain Unit Test | 状态、金额、库存、归属 | 无 |
| Application Test | 用例编排、权限、事务、补偿 | Fake/Mock Port |
| Repository Test | SQL、唯一索引、条件更新 | MySQL Testcontainer |
| Redis Integration Test | Session、缓存、TTL | Redis Container |
| Controller Test | Path、参数、状态码、响应 | MockMvc |
| Contract Test | HTTP 与 Event Schema | 通常无 |
| MQ Integration Test | 发布、重试、消费幂等 | RocketMQ |
| Search Integration Test | Mapping、查询、重建 | Elasticsearch Container |
| End-to-End Test | 完整交易闭环 | 全环境 |

端到端成功场景必须从店铺申请执行到订单 COMPLETED。失败场景至少覆盖未审核、暂停店铺、商品下架、价格变化、并发库存、重复订单、支付金额不符、重复回调、MQ 暂停、跨顾客访问和跨商户发货。

## 15. CI、部署与观测

### 15.1 CI

~~~mermaid
flowchart LR
    A[Checkout] --> B[编译与格式检查]
    B --> C[单元与应用测试]
    C --> D[Repository/Integration Test]
    D --> E[Contract Test]
    E --> F[打包 Jar]
    F --> G[构建镜像]
    G --> H[测试环境部署]
    H --> I[End-to-End Test]
~~~

### 15.2 Kubernetes

Kubernetes 包含 MySQL、Redis、RocketMQ NameServer/Broker、Nacos、Elasticsearch、六个业务服务和 Gateway。所有密钥来自 Kubernetes Secret 或外部 Secret Manager；有状态组件使用 PVC，服务发现使用 ClusterIP Service。

### 15.3 观测

- Liveness 和 Readiness；
- service、requestId、orderSn、outNo、eventId 和 errorCode 结构化日志；
- 请求延迟、错误率、数据库连接、Redis 命中率、库存冲突、MQ 积压、重复消费和支付冲突指标；
- 订单号与支付单号的完整链路查询。

### 15.4 故障演练

- Redis 清空后事实数据保持完整；
- Product 不可用时不形成半订单；
- RocketMQ 恢复后积压事件最终处理；
- 重复事件不产生重复副作用；
- Elasticsearch 清空后可重建；
- 应用回滚与数据库 Migration 兼容。

## 16. 推荐实现顺序

| 顺序 | 内容 | 验收结果 |
| ---: | --- | --- |
| 1 | core、customer 注册登录和地址 | 身份、Session、地址归属 |
| 2 | shop 申请、审核和治理 | ACTIVE 店铺 |
| 3 | product 审核、销售项、库存和缓存 | 可购买商品与并发库存 |
| 4 | customer 购物车 | 合并、归属、变价 |
| 5 | prodorder 创建、查询和取消 | 快照、幂等、补偿 |
| 6 | payment、FakePay | 回调、查询、金额一致 |
| 7 | RocketMQ 事件 | 支付确认和库存归还最终一致 |
| 8 | prodorder 发货和确认收货 | 完整订单状态机 |
| 9 | elasticsearch 搜索 | 增量索引和重建 |
| 10 | Nacos、Gateway | 服务发现和统一入口 |
| 11 | 完整测试、CI、部署 | 成功与失败链自动验收 |

## 17. 最终验收

- 模块职责能追溯到参与者目标和用例；
- 用例、活动图、顺序图、类图和状态图一致；
- Controller 不包含金额、库存和状态规则；
- Domain 不依赖 Web、Redis 或 MQ SDK；
- 跨服务只使用 Contract，不跨库；
- 金额使用整数分，时间格式统一；
- 库存、订单、支付和消息具有并发与幂等保护；
- MySQL、Redis、RocketMQ 和 Elasticsearch 具有真实集成测试；
- 空环境可依据 README 部署；
- 健康检查、日志、指标、故障恢复和回滚可执行。

## 18. 参考资料

- [需求分析、用例与 UML 建模](requirements-and-uml.md)
- [架构约束](architecture.md)
- [实现地图](implementation-map.md)
- [消息契约](message-contracts.md)
- [需求分析课程视频](https://www.bilibili.com/video/BV1zMpaeTEJa)
- [对象模型设计课程视频](https://www.bilibili.com/video/BV1pH1RYjEh5/)
- [GRASP 课程视频](https://www.bilibili.com/video/BV18wUzYREDV/)
- [API 设计课程视频](https://www.bilibili.com/video/BV1m1SaYKEbY/)
