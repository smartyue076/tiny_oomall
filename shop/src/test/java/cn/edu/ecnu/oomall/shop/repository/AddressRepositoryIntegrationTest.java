// package cn.edu.ecnu.oomall.customer.repository;

// import static org.assertj.core.api.Assertions.assertThat;

// import cn.edu.ecnu.oomall.customer.CustomerApplication;
// import cn.edu.ecnu.oomall.customer.domain.Customer;
// import cn.edu.ecnu.oomall.customer.domain.CustomerAddress;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.test.context.DynamicPropertyRegistry;
// import org.springframework.test.context.DynamicPropertySource;
// import org.testcontainers.containers.MySQLContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;

// @SpringBootTest(classes = CustomerApplication.class)
// @Testcontainers
// class AddressRepositoryIntegrationTest {
//     @Container
//     static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
//             .withDatabaseName("customer")
//             .withUsername("tiny_oomall")
//             .withPassword("tiny_oomall");

//     @DynamicPropertySource
//     static void databaseProperties(DynamicPropertyRegistry registry) {
//         registry.add("spring.datasource.url", mysql::getJdbcUrl);
//         registry.add("spring.datasource.username", mysql::getUsername);
//         registry.add("spring.datasource.password", mysql::getPassword);
//     }

//     @Autowired
//     private AddressRepository addresses;

//     @Autowired
//     private CustomerRepository customers;

//     @Test
//     void findByCustomerIdReturnsNewestAddressFirst() {
//         Customer customer = createCustomer("13800000011");
//         insert(customer.getId(), "first", false);
//         insert(customer.getId(), "second", true);

//         assertThat(addresses.countByCustomerId(customer.getId())).isEqualTo(2);
//         assertThat(addresses.findByCustomerId(customer.getId(), 1, 0))
//                 .extracting(CustomerAddress::getAddress)
//                 .containsExactly("second");
//     }

//     @Test
//     void clearDefaultOnlyChangesAddressesOfTheGivenCustomer() {
//         Customer mineCustomer = createCustomer("13800000012");
//         Customer otherCustomer = createCustomer("13800000013");
//         CustomerAddress mine = insert(mineCustomer.getId(), "mine", true);
//         CustomerAddress other = insert(otherCustomer.getId(), "other", true);

//         addresses.clearDefault(mineCustomer.getId());

//         assertThat(addresses.findByIdAndCustomerId(mine.getId(), mineCustomer.getId()).orElseThrow().isBeDefault()).isFalse();
//         assertThat(addresses.findByIdAndCustomerId(other.getId(), otherCustomer.getId()).orElseThrow().isBeDefault()).isTrue();
//     }

//     private Customer createCustomer(String mobile) {
//         Customer customer = new Customer(mobile, "hash", "Alice");
//         customers.insert(customer);
//         return customer;
//     }

//     private CustomerAddress insert(Long customerId, String address, boolean beDefault) {
//         CustomerAddress customerAddress = new CustomerAddress(
//                 customerId, "Alice", "13800000000", address, beDefault);
//         addresses.insert(customerAddress);
//         return customerAddress;
//     }
// }
