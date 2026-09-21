# Customer 模块 MyBatis XML 迁移实施计划

> **供执行代理使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务执行本计划。步骤以复选框（`- [ ]`）追踪。

**目标：** 将 `customer` 模块从 Spring Data JPA/Hibernate 迁移到 MyBatis XML Mapper，且不改变 HTTP API、数据库表结构归属和既有业务行为。

**架构：** `CustomerService` 保持业务边界并调用两个 Mapper 接口。XML 文件保存全部 SQL，负责将数据库列映射为普通 Java 领域对象；Flyway 仍负责建表；Spring 事务仍保证地址默认标记的更新与写入操作具有原子性。

**技术栈：** Java 17、Spring Boot 3.3.12、MyBatis Spring Boot Starter、MySQL、Flyway、Redis、JUnit 5、Testcontainers MySQL。

**设计文档：** `docs/superpowers/specs/2026-09-08-customer-mybatis-design.md`

## 全局约束

- 保留既有 Controller 路径、请求/响应 DTO、HTTP 状态码、Redis Session Key 和业务错误码。
- 删除 `spring-boot-starter-data-jpa`，不得保留 Hibernate 作为 ORM 实现。
- SQL 必须放在 `customer/src/main/resources/mapper/` 下的 XML 文件中；Mapper 接口不得使用 SQL 注解。
- Flyway 仍是 `customer_customer`、`customer_address` 表 DDL 的唯一管理者。
- 地址变更方法保留 `@Transactional`。
- 当前工作区没有 `.git` 仓库，所有提交步骤均跳过。

---

### 任务 1：替换依赖与应用配置

**文件：**
- 修改：`customer/pom.xml`
- 修改：`customer/src/main/resources/application.yaml`
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/CustomerApplication.java`
- 新建：`customer/src/test/resources/application-test.yaml`
- 新建：`customer/src/test/java/cn/edu/ecnu/oomall/customer/CustomerApplicationTest.java`

**接口：**
- 输入：Spring Boot 3.3.12 依赖管理。
- 输出：扫描 `cn.edu.ecnu.oomall.customer.repository` 下的 Mapper，加载 `classpath*:mapper/*.xml`。

- [ ] **步骤 1：先编写应用上下文测试**

```java
@SpringBootTest
@ActiveProfiles("test")
class CustomerApplicationTest {
    @Test
    void contextLoads() { }
}
```

- [ ] **步骤 2：运行基线测试**

执行：`mvn -pl customer -am test -Dtest=CustomerApplicationTest`

预期：在本机 MySQL、Redis 可用时，迁移前通过。

- [ ] **步骤 3：替换 JPA 依赖**

从 `customer/pom.xml` 移除：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

加入：

```xml
<dependency>
  <groupId>org.mybatis.spring.boot</groupId>
  <artifactId>mybatis-spring-boot-starter</artifactId>
  <version>3.0.3</version>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>mysql</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **步骤 4：切换配置**

删除 `application.yaml` 的 `spring.jpa` 块，加入：

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

在 `CustomerApplication` 加入：

```java
@MapperScan("cn.edu.ecnu.oomall.customer.repository")
```

测试配置 `application-test.yaml`：

```yaml
spring:
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **步骤 5：编译验证**

执行：`mvn -pl customer -am test-compile`

预期：通过，且不再需要 JPA 配置。

### 任务 2：将 JPA 实体转换为普通领域对象

**文件：**
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/domain/Customer.java`
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/domain/CustomerAddress.java`
- 新建：`customer/src/test/java/cn/edu/ecnu/oomall/customer/repository/CustomerMapperIntegrationTest.java`

**接口：**
- 输入：任务 1 的下划线转驼峰配置。
- 输出：不含 JPA 注解的 JavaBean，以及 MyBatis 结果映射和主键回填所需 setter。

- [ ] **步骤 1：编写会失败的主键回填测试**

```java
@Test
void insertCustomerAssignsMySqlGeneratedId() {
    Customer customer = new Customer("13800000000", "hash", "Alice");
    customerMapper.insert(customer);
    assertThat(customer.getId()).isPositive();
}
```

测试类使用 `@MybatisTest`、`@AutoConfigureTestDatabase(replace = Replace.NONE)`、`@Testcontainers`、`MySQLContainer<>("mysql:8.4")` 和 `@DynamicPropertySource` 提供 JDBC 参数；使用 `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` 执行 Flyway 建表脚本。

- [ ] **步骤 2：运行测试确认失败**

执行：`mvn -pl customer -am test -Dtest=CustomerMapperIntegrationTest#insertCustomerAssignsMySqlGeneratedId`

预期：失败，因为 MyBatis Mapper 尚未实现。

- [ ] **步骤 3：去除 JPA 注解并补齐 setter**

从两个领域类删除全部 `jakarta.persistence` 导入和注解，保留构造器、getter 与 `CustomerAddress.update(...)`。`Customer` 至少新增：

```java
public void setId(Long id) { this.id = id; }
public void setStatus(String status) { this.status = status; }
public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
```

`CustomerAddress` 新增 `id`、`customerId`、`consignee`、`mobile`、`address`、`beDefault`、`createdAt` 的 setter。

- [ ] **步骤 4：编译验证**

执行：`mvn -pl customer -am test-compile`

预期：通过；Customer 源码不再引用 JPA 类。

### 任务 3：实现 Customer XML Mapper 并保持注册与登录行为

**文件：**
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/repository/CustomerRepository.java`
- 新建：`customer/src/main/resources/mapper/CustomerRepository.xml`
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/service/CustomerService.java`
- 修改：`customer/src/test/java/cn/edu/ecnu/oomall/customer/repository/CustomerMapperIntegrationTest.java`

**接口：**
- 输入：任务 2 的 `Customer`。
- 输出：由 XML 实现的 `findByMobile`、`existsByMobile`、`insert`，以及包含数据库生成 id 的 `register` 返回值。

- [ ] **步骤 1：补充读写 Mapper 测试**

```java
@Test
void findByMobileReturnsPersistedCustomer() {
    Customer written = new Customer("13900000000", "hash", "Bob");
    customerMapper.insert(written);

    Customer read = customerMapper.findByMobile("13900000000").orElseThrow();
    assertThat(read.getId()).isEqualTo(written.getId());
    assertThat(read.getPassword()).isEqualTo("hash");
    assertThat(customerMapper.existsByMobile("13900000000")).isTrue();
}
```

- [ ] **步骤 2：定义 Mapper 接口**

```java
public interface CustomerRepository {
    Optional<Customer> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    int insert(Customer customer);
}
```

- [ ] **步骤 3：编写 XML SQL**

`CustomerRepository.xml` 的 namespace 是 `cn.edu.ecnu.oomall.customer.repository.CustomerRepository`。定义 `CustomerResult`，其中 `gmt_create` 映射为 `createdAt`，并实现：

```xml
<select id="findByMobile" resultMap="CustomerResult">
  SELECT id, mobile, password, name, status, gmt_create
  FROM customer_customer WHERE mobile = #{mobile}
</select>
<select id="existsByMobile" resultType="boolean">
  SELECT EXISTS(SELECT 1 FROM customer_customer WHERE mobile = #{mobile})
</select>
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
  INSERT INTO customer_customer (mobile, password, name, status, gmt_create)
  VALUES (#{mobile}, #{password}, #{name}, #{status}, #{createdAt})
</insert>
```

- [ ] **步骤 4：改为显式插入**

将 `CustomerService.register` 中的 `save` 替换为：

```java
Customer customer = new Customer(mobile, encoder.encode(password), name.trim());
customers.insert(customer);
return customer;
```

登录逻辑不变。

- [ ] **步骤 5：验证 Customer Mapper**

执行：`mvn -pl customer -am test -Dtest=CustomerMapperIntegrationTest`

预期：通过。插入回填 id，查询能读出密码哈希，手机号存在性判断正确。

### 任务 4：实现 Address XML Mapper 与显式更新逻辑

**文件：**
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/repository/AddressRepository.java`
- 新建：`customer/src/main/resources/mapper/AddressRepository.xml`
- 修改：`customer/src/main/java/cn/edu/ecnu/oomall/customer/service/CustomerService.java`
- 新建：`customer/src/test/java/cn/edu/ecnu/oomall/customer/repository/AddressMapperIntegrationTest.java`

**接口：**
- 输入：任务 2 的 `CustomerAddress` 与 Spring 事务。
- 输出：地址插入、更新、删除、归属查询、默认项清理、数量统计和分页查询。

- [ ] **步骤 1：编写会失败的地址 Mapper 测试**

```java
@Test
void listByCustomerReturnsNewestPageAndTotal() {
    insertAddress(7L, "first", false);
    insertAddress(7L, "second", true);

    assertThat(addressMapper.countByCustomerId(7L)).isEqualTo(2);
    assertThat(addressMapper.findByCustomerId(7L, 1, 0))
        .extracting(CustomerAddress::getAddress)
        .containsExactly("second");
}
```

再测试 `clearDefault(7L)` 仅影响顾客 7 的地址，不影响顾客 8 的默认地址。

- [ ] **步骤 2：运行测试确认失败**

执行：`mvn -pl customer -am test -Dtest=AddressMapperIntegrationTest`

预期：失败，因为地址 XML Mapper 不存在。

- [ ] **步骤 3：定义 Mapper 接口**

```java
Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);
long countByCustomerId(Long customerId);
int clearDefault(Long customerId);
int insert(CustomerAddress address);
int update(CustomerAddress address);
int deleteByIdAndCustomerId(Long id, Long customerId);
List<CustomerAddress> findByCustomerId(Long customerId, int limit, long offset);
long countAllByCustomerId(Long customerId);
```

- [ ] **步骤 4：编写地址 XML SQL**

`AddressRepository.xml` 将 `customer_id`、`be_default`、`gmt_create` 分别映射到 `customerId`、`beDefault`、`createdAt`。插入使用 `useGeneratedKeys="true" keyProperty="id"`。清除默认地址的 SQL 是：

```sql
UPDATE customer_address SET be_default = FALSE WHERE customer_id = #{customerId}
```

更新与删除均需使用 `WHERE id = #{id} AND customer_id = #{customerId}` 限制归属；分页查询使用：

```sql
ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
```

- [ ] **步骤 5：调整 Service 调用点**

创建地址时显式调用 `addresses.insert(address)`；更新地址时调用 `customerAddress.update(...)` 后显式 `addresses.update(customerAddress)`；删除使用 `addresses.deleteByIdAndCustomerId(id, customerId)`。分页返回值使用：

```java
long total = addresses.countAllByCustomerId(customerId);
long offset = (long) page * pageSize;
var content = addresses.findByCustomerId(customerId, pageSize, offset);
return new PageImpl<>(content, PageRequest.of(page, pageSize, Sort.by("id").descending()), total);
```

- [ ] **步骤 6：验证地址 Mapper**

执行：`mvn -pl customer -am test -Dtest=AddressMapperIntegrationTest`

预期：通过。id 会回填；默认项清理只影响指定顾客；归属查询同时按两个 id 限制；分页按 id 从新到旧。

### 任务 5：回归验证并清理 ORM 遗留内容

**文件：**
- 修改：`docs/development-manual.md`
- 新建：`customer/src/test/java/cn/edu/ecnu/oomall/customer/service/CustomerServiceIntegrationTest.java`
- 验证：`customer/src/main/java/cn/edu/ecnu/oomall/customer/**/*.java`
- 验证：`customer/src/main/resources/application.yaml`

**接口：**
- 输入：任务 1 至任务 4 的 Mapper 与 Service 改动。
- 输出：纯 MyBatis 的 Customer 模块、更新后的开发文档和经过验证的 API 行为。

- [ ] **步骤 1：编写重复注册行为测试**

```java
@Test
void duplicateMobileStillUsesTheExistingBusinessError() {
    service.register("13800000001", "password1", "Alice");

    assertThatThrownBy(() -> service.register("13800000001", "password2", "Bob"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("mobile already registered");
}
```

使用真实 XML Mapper 和 mocked `StringRedisTemplate` 构造 Service；该测试仅执行注册流程。

- [ ] **步骤 2：清理 JPA/Hibernate 遗留引用**

执行：

```bash
rg -n 'jakarta\.persistence|JpaRepository|spring\.jpa|hibernate' customer
```

预期：源码和配置中没有匹配项。更新 `docs/development-manual.md`，说明 Customer 使用 MyBatis XML Mapper、Flyway 管理 DDL、MySQL 自增主键通过 `useGeneratedKeys` 回填。

- [ ] **步骤 3：运行完整测试与构建**

执行：

```bash
mvn -pl customer -am test
mvn -pl customer -am package -DskipTests
```

预期：两条命令均通过；Testcontainers 可用时 Mapper 集成测试会在 MySQL 容器中运行。

- [ ] **步骤 4：运行 API 冒烟测试**

按开发文档配置 MySQL 和 Redis 后启动服务，执行：

```bash
curl -i -X POST http://localhost:8081/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"13800000009","password":"password1","name":"MyBatis User"}'
```

预期：HTTP `201 Created`，响应中有非空 `id`，且不含密码字段。
