# 架构约束

- 业务边界以 `requirements-and-uml.md` 为准，只实现顾客、商户、平台管理员和支付平台参与的最小交易闭环。
- 收货地址由 `customer` 保存为普通文本，订单创建时复制快照；商户发货和顾客确认收货只改变订单状态。
- 每个业务服务拥有自己的数据，禁止跨库查询。
- Controller/Adapter 只处理协议；Application Service 编排用例；Domain 保存规则；Infrastructure 实现 Port。
- Redis 数据必须可以从事实数据库恢复。
- RocketMQ Consumer 必须按 `eventId` 幂等，失败时抛异常触发重试。
- Gateway 只做入口治理，资源归属仍由业务服务校验。

可以使用 `*ModuleDesign` 教学索引集中列出模块需要的 Command、Snapshot、Repository Port 和 Application Port；正式实现时再拆成独立文件。
