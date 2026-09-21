package cn.edu.ecnu.oomall.customer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.customer.domain.Customer;
import cn.edu.ecnu.oomall.customer.domain.CustomerAddress;
import cn.edu.ecnu.oomall.customer.repository.AddressRepository;
import cn.edu.ecnu.oomall.customer.repository.CustomerRepository;

/** 顾客、会话及收货地址用例的应用服务。 */
@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final Duration ttl;
    public CustomerService(CustomerRepository customers, AddressRepository addresses,
                           PasswordEncoder encoder, StringRedisTemplate redis,
                           @Value("${customer.session-ttl:PT24H}") Duration ttl) {
        this.customers = customers;
        this.addresses = addresses;
        this.encoder = encoder;
        this.redis = redis;
        this.ttl = ttl;
    }

    public Customer register(String mobile, String password, String name) {
        mobile = mobile.trim();
        if (customers.existsByMobile(mobile)) {
            throw new BusinessException("MOBILE_EXISTS", HttpStatus.CONFLICT, "mobile already registered");
        }
        System.out.println("Registering customer with mobile: " + mobile + ", name: " + name);
        String encodedPassword = encoder.encode(password);
        String trimmedName = name.trim();
        Customer customer = new Customer(mobile, encodedPassword, trimmedName);
        customers.insert(customer);
        return customer;
    }

    /** 校验凭据后仅将随机 Session ID 交给客户端，身份数据保存在 Redis。 */
    public Session login(String mobile, String password) {
        String trimmedMobile = mobile.trim();
        Optional<Customer> optionalCustomer = customers.findByMobile(trimmedMobile);
        if (optionalCustomer.isEmpty()) {
            throw invalidCredential();
        }
        Customer customer = optionalCustomer.get();
        if (!"NORMAL".equals(customer.getStatus())) {
            throw new BusinessException("CUSTOMER_DISABLED", HttpStatus.FORBIDDEN, "customer disabled");
        }
        if (!encoder.matches(password, customer.getPassword())) {
            throw invalidCredential();
        }
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString().replace("-", "");
        try {
            String sessionKey = key(id);
            String sessionValue = customer.getId() + ":" + Role.CUSTOMER.name();
            ValueOperations<String, String> values = redis.opsForValue();
            values.set(sessionKey, sessionValue, ttl);
        } catch (Exception exception) {
            throw unavailable();
        }
        Instant expiresAt = Instant.now().plus(ttl);
        Session session = new Session(id, expiresAt);
        return session;
    }

    public void logout(String id) {
        String sessionKey = key(id);
        redis.delete(sessionKey); // 删除不存在的 Key 同样成功，保证退出幂等。
    }

    public UserToken authenticate(String id) {
        try {
            String sessionKey = key(id);
            ValueOperations<String, String> values = redis.opsForValue();
            String value = values.get(sessionKey);
            if (value == null) {
                throw new IllegalStateException("unauthenticated");
            }
            String[] parts = value.split(":");
            Long customerId = Long.valueOf(parts[0]);
            Role role = Role.valueOf(parts[1]);
            UserToken token = new UserToken(customerId, role);
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /** 设置新默认地址与清除旧默认地址必须处于同一个数据库事务。 */
    @Transactional
    public CustomerAddress createAddress(Long customerId, String consignee, String mobile,
                                         String address, boolean beDefault) {
        if (addresses.countByCustomerId(customerId) >= 20) {
            throw new BusinessException("ADDRESS_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "address limit exceeded");
        }
        if (beDefault) {
            addresses.clearDefault(customerId);
        }
        CustomerAddress customerAddress = new CustomerAddress(customerId, consignee, mobile, address, beDefault);
        addresses.insert(customerAddress);
        return customerAddress;
    }

    @Transactional
    public CustomerAddress updateAddress(Long customerId, Long id, String consignee, String mobile,
                                         String address, boolean beDefault) {
        CustomerAddress customerAddress = owned(customerId, id);
        if (beDefault) {
            addresses.clearDefault(customerId);
        }
        customerAddress.update(consignee, mobile, address, beDefault);
        addresses.update(customerAddress);
        return customerAddress;
    }

    @Transactional
    public void deleteAddress(Long customerId, Long id) {
        owned(customerId, id);
        addresses.deleteByIdAndCustomerId(id, customerId);
    }

    public Page<CustomerAddress> addresses(Long customerId, int page, int pageSize) {
        long total = addresses.countAllByCustomerId(customerId);
        long offset = (long) page * pageSize;
        List<CustomerAddress> addressesInPage = addresses.findByCustomerId(customerId, pageSize, offset);
        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by("id").descending());
        Page<CustomerAddress> addressPage = new PageImpl<>(addressesInPage, pageRequest, total);
        return addressPage;
    }

    /** 使用联合条件查询，不向调用方泄露其他用户地址是否存在。 */
    public CustomerAddress owned(Long customerId, Long id) {
        Optional<CustomerAddress> optionalAddress = addresses.findByIdAndCustomerId(id, customerId);
        if (optionalAddress.isEmpty()) {
            throw new BusinessException(
                    "RESOURCE_FORBIDDEN", HttpStatus.FORBIDDEN, "address unavailable");
        }
        CustomerAddress customerAddress = optionalAddress.get();
        return customerAddress;
    }

    private String key(String id) {
        return "session:" + id;
    }

    private BusinessException invalidCredential() {
        return new BusinessException(
                "AUTH_INVALID_CREDENTIAL", HttpStatus.UNAUTHORIZED, "invalid credential");
    }

    private BusinessException unavailable() {
        return new BusinessException(
                "DEPENDENCY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "session service unavailable");
    }

    public record Session(String id, Instant expiresAt) { }
}
