package cn.edu.ecnu.oomall.shop.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cn.edu.ecnu.oomall.core.api.ApiResponse;
import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserContext;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.service.ShopService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class ShopController {
    private final ShopService service;

    public ShopController(ShopService service) {
        this.service = service;
    }

    @GetMapping("/shop-status-options")
    ApiResponse<List<ShopStatusOption>> shopStatusOptions() {
        ShopStatus[] statuses = ShopStatus.values();
        List<ShopStatusOption> options = new ArrayList<>();
        for (ShopStatus status : statuses) {
            String code = status.name();
            String name = status.getDisplayName();
            ShopStatusOption option = new ShopStatusOption(code, name);
            options.add(option);
        }
        return ApiResponse.ok(options);
    }

    @GetMapping("/shops")
    ApiResponse<Page<ShopView>> getOnlineShops(@RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        Page<Shop> shopPage = service.getOnlineShopsQueue(page, pageSize);
        List<ShopView> views = new ArrayList<>();
        for (Shop shop : shopPage.getContent()) {
            ShopView view = ShopView.of(shop);
            views.add(view);
        }
        Page<ShopView> viewPage = new PageImpl<>(
                views, shopPage.getPageable(), shopPage.getTotalElements());
        return ApiResponse.ok(viewPage);
    }

    @PostMapping("/shop-applications")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ShopView> createShopApplication(
            @Valid @RequestBody CreateShopApplicationRequest request) {
        UserToken token = requireMerchant();
        Long merchantId = token.userId();
        Shop shop = service.createShopApplication(
                merchantId, request.name(), request.contact(), request.mobile());
        ShopView view = ShopView.of(shop);
        return ApiResponse.ok(view);
    }

    private UserToken requireMerchant() {
        UserToken token = UserContext.require();
        if (token.role() != Role.MERCHANT) {
            throw new IllegalStateException("unauthenticated");
        }
        return token;
    }

    public record CreateShopApplicationRequest(@NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 64) String contact,
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String mobile) {
    }

    public record ShopStatusOption(String code, String name) {
    }

    public record ShopView(Long id, Long merchantId, String name, String contact,
            String mobile, ShopStatus status, Instant createdAt) {
        static ShopView of(Shop shop) {
            Long id = shop.getId();
            Long merchantId = shop.getMerchantId();
            String name = shop.getName();
            String contact = shop.getContact();
            String mobile = shop.getMobile();
            ShopStatus status = shop.getStatus();
            Instant createdAt = shop.getApplyTime();
            return new ShopView(id, merchantId, name, contact, mobile, status, createdAt);
        }
    }
}
