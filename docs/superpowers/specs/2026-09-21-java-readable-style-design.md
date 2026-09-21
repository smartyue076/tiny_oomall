# Java 初学者可读性重构设计

## 目标

将 `core`、`customer`、`shop` 模块中的业务 Java 代码改写为更适合 Java 初学者逐步调试和阅读的形式，同时保持 HTTP 接口、数据库 SQL、异常码和业务行为不变。

## 范围

- 拆开业务代码中的链式调用，使用有含义的局部变量保存中间值。
- 将业务代码中的方法引用、三元表达式和一行多逻辑语句改为普通的 `if`、局部变量或显式循环。
- 将 `Page.map(...)` 改为显式 `for` 循环构造 View 列表，再使用 `PageImpl` 保留分页元数据。
- 保留 Java 框架要求的回调接口写法，例如 Testcontainers 向 `DynamicPropertyRegistry` 注册 `Supplier` 时的 Lambda；将其改成匿名类会降低而非提高可读性。
- 不修改注释掉的历史测试代码，不调整包结构，不改变字段名、Mapper SQL、Flyway 或 API 文档中的接口契约。

## 统一写法

原来的 Optional 链式查询：

```java
return shops.findById(shopId)
        .orElseThrow(() -> new BusinessException(
                "SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found"));
```

改为：

```java
Optional<Shop> optionalShop = shops.findById(shopId);
if (optionalShop.isEmpty()) {
    throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
}
return optionalShop.get();
```

原来的分页转换：

```java
return ApiResponse.ok(page.map(ShopManagementView::of));
```

改为显式循环：

```java
List<ShopManagementView> views = new ArrayList<>();
for (Shop shop : page.getContent()) {
    ShopManagementView view = ShopManagementView.of(shop);
    views.add(view);
}
Page<ShopManagementView> result = new PageImpl<>(views, page.getPageable(), page.getTotalElements());
return ApiResponse.ok(result);
```

## 文件边界

- `core`：只改影响阅读的公共辅助代码；不改变认证上下文和异常处理逻辑。
- `customer`：改 Controller 和 Service 中的业务流控制、分页 View 转换与 Optional 处理；保留 Mapper 和接口语义。
- `shop`：改 Merchant、PlatformAdmin、Shop 的 Controller/Service 中同类写法；保留管理员鉴权和状态流转边界。
- 测试：保留断言含义；仅拆开测试中影响阅读的链式断言或业务对象读取。

## 验收

- 所有修改前存在的 API 路径、请求字段、响应字段和业务错误码保持一致。
- `mvn -pl core,customer,shop -am test` 通过。
- `mvn -pl core,customer,shop -am package -DskipTests` 通过。
- 不为单纯消除 Lambda 而引入匿名内部类或新的抽象层。
