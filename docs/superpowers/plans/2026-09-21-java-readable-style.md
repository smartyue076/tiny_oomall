# Java Readable Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目业务 Java 代码改写为适合初学者阅读和断点调试的展开式写法，且不改变任何行为。

**Architecture:** 本次只调整 Java 源码的表达方式。Optional 查询先保存到 `Optional<T>` 变量再判断；分页 DTO 转换使用显式列表和循环；复杂的一行语句拆为顺序语句。Mapper XML、数据库结构、接口路径与异常契约保持不变。

**Tech Stack:** Java 17、Spring Boot 3、Spring Data `Page`/`PageImpl`、MyBatis、JUnit 5、Testcontainers。

**Spec:** `docs/superpowers/specs/2026-09-21-java-readable-style-design.md`

## Global Constraints

- 不改变 HTTP 路径、请求字段、响应字段、状态流转、SQL、Flyway 与错误码。
- 不修改注释掉的历史测试代码。
- 框架回调 Lambda 可以保留；不以匿名内部类替代它们。
- 所有行为改动必须先有失败测试；本次纯重构只能在现有测试通过后进行。

---

### Task 1: Customer 业务流展开

**Files:**
- Modify: `customer/src/main/java/cn/edu/ecnu/oomall/customer/service/CustomerService.java`
- Modify: `customer/src/main/java/cn/edu/ecnu/oomall/customer/web/CustomerController.java`
- Test: `customer/src/test/java/cn/edu/ecnu/oomall/customer/service/CustomerServiceIntegrationTest.java`

**Interfaces:**
- Consumes: 现有 `CustomerRepository`、`AddressRepository`、`Page<CustomerAddress>`。
- Produces: 不变的 `CustomerService` 公共方法和 `ApiResponse<Page<AddressView>>`。

- [ ] **Step 1: 运行 Customer 测试作为重构基线**

Run: `mvn -pl core,customer -am test`

Expected: 所有现有 Customer 测试通过。

- [ ] **Step 2: 拆开登录和地址查询中的 Optional 链式调用**

将：

```java
Customer customer = customers.findByMobile(mobile.trim()).orElseThrow(this::invalidCredential);
```

改为局部 `Optional<Customer>`、`isEmpty()` 判断、调用 `invalidCredential()` 后抛出异常，再用 `get()` 取得对象。地址归属查询使用相同规则。

- [ ] **Step 3: 拆开地址列表的 Page.map DTO 转换**

创建 `List<AddressView>`，遍历 `Page<CustomerAddress>.getContent()`，逐项调用 `AddressView.of`，用原 `pageable` 和 `totalElements` 构造 `PageImpl<AddressView>`。

- [ ] **Step 4: 拆开一行 View 工厂与辅助方法**

将 Controller 中一行的 `CustomerView.of`、`AddressView.of`，以及 Service 中一行的 `key`、`invalidCredential`、`unavailable` 方法改为多行普通方法体。

- [ ] **Step 5: 重新运行 Customer 测试**

Run: `mvn -pl core,customer -am test`

Expected: 通过；注册、登录、地址归属、默认地址事务行为不变。

### Task 2: Shop 业务流展开

**Files:**
- Modify: `shop/src/main/java/cn/edu/ecnu/oomall/shop/service/MerchantService.java`
- Modify: `shop/src/main/java/cn/edu/ecnu/oomall/shop/service/PlatformAdminService.java`
- Modify: `shop/src/main/java/cn/edu/ecnu/oomall/shop/web/PlatformAdminController.java`
- Test: `shop/src/test/java/cn/edu/ecnu/oomall/shop/service/MerchantServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `MerchantRepository`、`PlatformAdminRepository`、`ShopRepository`。
- Produces: 不变的登录、审核队列、店铺详情返回值和 `SHOP_NOT_FOUND` 错误码。

- [ ] **Step 1: 运行 Shop 测试作为重构基线**

Run: `mvn -pl core,shop -am test -Dtest=MerchantServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: 当前 3 个集成测试通过。

- [ ] **Step 2: 拆开 Merchant 和 PlatformAdmin 的登录查询**

分别将 `findByMobile(...).orElseThrow(...)` 改为 `Optional<Merchant>` 或 `Optional<PlatformAdmin>` 变量、空值判断、再取得实体。保留密码校验、Redis 写入和异常码。

- [ ] **Step 3: 拆开管理员详情查询和分页 DTO 转换**

`getManagementDetail` 改为 `Optional<Shop>` 变量加 `if`。审核队列 Controller 用 `List<ShopManagementView>` 和 `for` 循环转换 `Page<Shop>`，再以相同的 `Pageable`、`totalElements` 构造 `PageImpl<ShopManagementView>`。

- [ ] **Step 4: 拆开一行辅助方法**

将 MerchantService 与 PlatformAdminService 的 `key`、`invalidCredential`、`unavailable` 方法改成多行普通方法体。不要改变 Redis Key 格式和异常状态码。

- [ ] **Step 5: 重新运行 Shop 测试**

Run: `mvn -pl core,shop -am test -Dtest=MerchantServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: 3 个集成测试通过。

### Task 3: Domain 与可读性回归检查

**Files:**
- Modify: `customer/src/main/java/cn/edu/ecnu/oomall/customer/domain/Customer.java`
- Modify: `customer/src/main/java/cn/edu/ecnu/oomall/customer/domain/CustomerAddress.java`
- Modify: `shop/src/main/java/cn/edu/ecnu/oomall/shop/domain/Merchant.java`
- Modify: `shop/src/main/java/cn/edu/ecnu/oomall/shop/domain/PlatformAdmin.java`
- Test: `customer/src/test/java/cn/edu/ecnu/oomall/customer/**/*.java`
- Test: `shop/src/test/java/cn/edu/ecnu/oomall/shop/**/*.java`

**Interfaces:**
- Consumes: 已有 JavaBean getter/setter 的反射与 MyBatis 属性映射。
- Produces: 相同的方法签名与 JavaBean 属性。

- [ ] **Step 1: 将 Domain 的单行 getter/setter 拆为多行方法体**

例如：

```java
public Long getId() {
    return id;
}
```

方法名、参数、返回类型及字段赋值保持不变。

- [ ] **Step 2: 检查生产 Java 文件中的剩余难读写法**

Run: `rg -n --glob '*.java' '\\.orElseThrow\\(|\\.map\\(|\\?.*:' core/src/main customer/src/main shop/src/main`

Expected: 没有业务代码中的 Optional 链式调用、Page.map 或三元表达式；框架必要 Lambda 不作为失败条件。

- [ ] **Step 3: 运行完整测试与打包**

Run: `mvn -pl core,customer,shop -am test && mvn -pl core,customer,shop -am package -DskipTests`

Expected: 所有模块测试和打包成功。

- [ ] **Step 4: 人工检查接口契约**

检查 Controller 的 `@GetMapping`、`@PostMapping`、`@RequestParam`、`@RequestBody` 注解及 DTO record 字段没有修改；检查 Mapper XML 与 Flyway 文件没有改动。
