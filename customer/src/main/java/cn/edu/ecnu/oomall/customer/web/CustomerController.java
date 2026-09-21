package cn.edu.ecnu.oomall.customer.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cn.edu.ecnu.oomall.core.api.ApiResponse;
import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserContext;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.customer.domain.Customer;
import cn.edu.ecnu.oomall.customer.domain.CustomerAddress;
import cn.edu.ecnu.oomall.customer.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 实验 1 的顾客、会话及地址 HTTP 接口。 */
@RestController   // 表示这是一个 Web 控制器。Spring Boot 会把其中方法的返回值自动转换为 JSON 返回给客户端。
@RequestMapping("/api")  // 是统一前缀，所以这个类里的接口都会以 /api 开头
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED) // 指定成功时 HTTP 状态码是 201 Created，用于表示资源创建成功
    ApiResponse<CustomerView> register(@Valid @RequestBody RegisterRequest request)
    // @RequestBody 表示 Postman 发送的 JSON 会被转换为 RegisterRequest 对象
    // @Valid 会执行 RegisterRequest 中的校验规则，比如手机号格式、密码长度、姓名不能为空。校验不通过时，GlobalExceptionHandler 会返回 400 Bad Request。
    /*
        Postman
        -> POST /api/customers
        -> CustomerController.register
        -> CustomerService.register
        -> CustomerRepository
        -> MySQL
        -> CustomerView
        -> JSON 响应
    */
    {
        Customer customer = service.register(request.mobile(), request.password(), request.name());
        CustomerView view = CustomerView.of(customer);
        return ApiResponse.ok(view);
    }

    @PostMapping("/auth/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest request) {
        CustomerService.Session session = service.login(request.mobile(), request.password());
        return ApiResponse.ok(new SessionView(session.id(), session.expiresAt()));
    }

    @DeleteMapping("/auth/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("X-Session-Id") String sessionId) {
        requireCustomer();
        service.logout(sessionId);
    }

    @GetMapping("/addresses")
    ApiResponse<Page<AddressView>> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        UserToken token = requireCustomer();
        Long customerId = token.userId();
        Page<CustomerAddress> addressPage = service.addresses(customerId, page, pageSize);
        List<AddressView> views = new ArrayList<>();
        for (CustomerAddress address : addressPage.getContent()) {
            AddressView view = AddressView.of(address);
            views.add(view);
        }
        Page<AddressView> viewPage = new PageImpl<>(
                views, addressPage.getPageable(), addressPage.getTotalElements());
        return ApiResponse.ok(viewPage);
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AddressView> create(@Valid @RequestBody AddressRequest request) {
        UserToken token = requireCustomer();
        Long customerId = token.userId();
        CustomerAddress address = service.createAddress(customerId, request.consignee(),
                request.mobile(), request.address(), request.beDefault());
        AddressView view = AddressView.of(address);
        return ApiResponse.ok(view);
    }

    @PutMapping("/addresses/{id}")
    ApiResponse<AddressView> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        UserToken token = requireCustomer();
        Long customerId = token.userId();
        CustomerAddress address = service.updateAddress(customerId, id, request.consignee(),
                request.mobile(), request.address(), request.beDefault());
        AddressView view = AddressView.of(address);
        return ApiResponse.ok(view);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        UserToken token = requireCustomer();
        Long customerId = token.userId();
        service.deleteAddress(customerId, id);
    }

    /** 临时内部调用边界；后续网关/服务认证会替换该头部约定。 */
    @GetMapping("/internal/customers/{customerId}/addresses/{id}")
    ApiResponse<AddressView> internal(@PathVariable Long customerId, @PathVariable Long id,
                                      @RequestHeader("X-Internal-Call") String internal) {
        if (!"true".equals(internal)) {
            throw new IllegalStateException("unauthenticated");
        }
        CustomerAddress address = service.owned(customerId, id);
        AddressView view = AddressView.of(address);
        return ApiResponse.ok(view);
    }

    private UserToken requireCustomer() {
        UserToken token = UserContext.require();
        if (token.role() != Role.CUSTOMER) {
            throw new IllegalStateException("unauthenticated");
        }
        return token;
    }

    public record RegisterRequest(@Pattern(regexp = "^1[3-9]\\d{9}$") String mobile,
                                  @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 64) String name) { }
    public record LoginRequest(@NotBlank String mobile, @NotBlank String password) { }
    public record AddressRequest(@NotBlank @Size(max = 128) String consignee,
                                 @Pattern(regexp = "^1[3-9]\\d{9}$") String mobile,
                                 @NotBlank @Size(max = 500) String address, boolean beDefault) { }
    public record CustomerView(Long id, String mobile, String name) {
        static CustomerView of(Customer customer) {
            Long id = customer.getId();
            String mobile = customer.getMobile();
            String name = customer.getName();
            return new CustomerView(id, mobile, name);
        }
    }
    public record SessionView(String id, Instant expiresAt) { }
    public record AddressView(Long id, String consignee, String mobile, String address, boolean beDefault) {
        static AddressView of(CustomerAddress address) {
            Long id = address.getId();
            String consignee = address.getConsignee();
            String mobile = address.getMobile();
            String value = address.getAddress();
            boolean beDefault = address.isBeDefault();
            return new AddressView(id, consignee, mobile, value, beDefault);
        }
    }
}
