package cn.edu.ecnu.oomall.customer.repository;

import cn.edu.ecnu.oomall.customer.domain.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

public interface AddressRepository {
    Optional<CustomerAddress> findByIdAndCustomerId(@Param("id") Long id,
                                                     @Param("customerId") Long customerId);
    long countByCustomerId(@Param("customerId") Long customerId);
    int clearDefault(@Param("customerId") Long customerId);
    int insert(CustomerAddress address);
    int update(CustomerAddress address);
    int deleteByIdAndCustomerId(@Param("id") Long id, @Param("customerId") Long customerId);
    List<CustomerAddress> findByCustomerId(@Param("customerId") Long customerId,
                                           @Param("limit") int limit, @Param("offset") long offset);
    long countAllByCustomerId(@Param("customerId") Long customerId);
}
