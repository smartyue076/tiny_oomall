package cn.edu.ecnu.oomall.customer.repository;

import cn.edu.ecnu.oomall.customer.domain.Customer;
import java.util.Optional;

public interface CustomerRepository {
    Optional<Customer> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    int insert(Customer customer);
}
