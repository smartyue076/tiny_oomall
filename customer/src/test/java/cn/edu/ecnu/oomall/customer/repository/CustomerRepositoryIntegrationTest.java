package cn.edu.ecnu.oomall.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cn.edu.ecnu.oomall.customer.CustomerApplication;
import cn.edu.ecnu.oomall.customer.domain.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CustomerApplication.class)
@Testcontainers
class CustomerRepositoryIntegrationTest {
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
    private CustomerRepository customers;

    @Test
    void insertAssignsGeneratedIdAndFindByMobileReadsPersistedCustomer() {
        Customer written = new Customer("13800000000", "hash", "Alice");

        customers.insert(written);

        Customer read = customers.findByMobile("13800000000").orElseThrow();
        assertThat(written.getId()).isPositive();
        assertThat(read.getId()).isEqualTo(written.getId());
        assertThat(read.getPassword()).isEqualTo("hash");
        assertThat(customers.existsByMobile("13800000000")).isTrue();
    }
}
