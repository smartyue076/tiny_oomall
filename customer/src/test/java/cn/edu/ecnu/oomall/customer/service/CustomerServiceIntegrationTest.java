package cn.edu.ecnu.oomall.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.customer.CustomerApplication;
import cn.edu.ecnu.oomall.customer.domain.Customer;
import cn.edu.ecnu.oomall.customer.domain.CustomerAddress;
import cn.edu.ecnu.oomall.customer.repository.AddressRepository;
import cn.edu.ecnu.oomall.customer.repository.CustomerRepository;

@SpringBootTest(classes = CustomerApplication.class)
@Testcontainers
class CustomerServiceIntegrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("customer")
            .withUsername("tiny_oomall")
            .withPassword("tiny_oomall");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private CustomerService service;

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private CustomerRepository customers;


    @Test
    void duplicateMobileStillUsesTheExistingBusinessError() {
        service.register("13800000001", "password1", "Alice");

        assertThatThrownBy(() -> service.register("13800000001", "password2", "Bob"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mobile already registered");
    }

    @Test
    void deleteAddressRejectsAnAddressOwnedByAnotherCustomer() {
        Customer requestingCustomer = createCustomer("13800000002");
        Customer addressOwner = createCustomer("13800000003");
        CustomerAddress address = new CustomerAddress(
                addressOwner.getId(), "Alice", "13800000000", "Road 1", false);
        addresses.insert(address);

        assertThatThrownBy(() -> service.deleteAddress(requestingCustomer.getId(), address.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("address unavailable");
    }

    @Test
    void createDefaultAddressRollsBackWhenInsertFails() {
        Customer customer = createCustomer("13800000004");
        CustomerAddress oldAddress = new CustomerAddress(
                customer.getId(), "Alice", "13800000000", "Old Road", true);
        addresses.insert(oldAddress);

        assertThatThrownBy(() -> service.createAddress(
                customer.getId(), "Bob", "13900000000", "X".repeat(501), true))
                .isInstanceOf(RuntimeException.class);

        CustomerAddress afterRollback = addresses.findByIdAndCustomerId(oldAddress.getId(), customer.getId())
                .orElseThrow();
        assertThat(afterRollback.isBeDefault()).isTrue();
        assertThat(addresses.countByCustomerId(customer.getId())).isEqualTo(1);
    }

    private Customer createCustomer(String mobile) {
        Customer customer = new Customer(mobile, "hash", "Alice");
        customers.insert(customer);
        return customer;
    }
}
