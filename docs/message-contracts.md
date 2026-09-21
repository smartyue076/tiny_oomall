# Message Contracts

消息统一包含 `eventId`、`version`、`occurredAt` 和业务 ID。禁止直接序列化 JPA Entity。

| Topic | Producer | Consumer | Payload |
| --- | --- | --- | --- |
| `payment-succeeded-v1` | payment | prodorder | `PaymentSucceededV1` |
| `order-canceled-v1` | prodorder | product | `OrderCanceledV1` |
| `product-changed-v1` | product | elasticsearch | `ProductChangedV1` |

Consumer 必须验证 `version`，以 `eventId` 去重；业务执行失败时抛出异常。Producer 的 Transaction Check 必须查询自己的事实数据库。
