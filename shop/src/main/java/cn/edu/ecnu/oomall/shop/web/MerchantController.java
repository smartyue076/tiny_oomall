package cn.edu.ecnu.oomall.shop.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cn.edu.ecnu.oomall.core.api.ApiResponse;
import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserContext;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.shop.domain.Merchant;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.service.MerchantService;
import cn.edu.ecnu.oomall.shop.web.MerchantController.MerchantView;
import cn.edu.ecnu.oomall.shop.web.MerchantController.SessionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class MerchantController {
    private final MerchantService service;

    public MerchantController(MerchantService service) {
        this.service = service;
    }

    @PostMapping("/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MerchantView> register(@Valid @RequestBody RegisterRequest request) {
        Merchant merchant = service.register(request.mobile(), request.password(), request.name());
        MerchantView view = MerchantView.of(merchant);
        return ApiResponse.ok(view);
    }

    @PostMapping("/auth/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest request) {
        MerchantService.Session session = service.login(request.mobile(), request.password());
        SessionView view = new SessionView(session.id(), session.expiresAt());
        return ApiResponse.ok(view);
    }

    @DeleteMapping("/auth/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("X-Session-Id") String sessionId) {
        service.logout(sessionId);
    }

    @GetMapping("/merchant/shop/detail")
    ApiResponse<ShopManagementView> getShopDetail() {
        UserToken token = requireMerchant();
        Long merchantId = token.userId();
        Shop shop = service.getShopDetail(merchantId);
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }


    @PutMapping("/merchant/shop/online")
    ApiResponse<ShopManagementView> setShopOnline() {
        UserToken token = requireMerchant();
        Long merchantId = token.userId();
        Shop shop = service.setShopOnline(merchantId);
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }

    @PutMapping("/merchant/shop/offline")
    ApiResponse<ShopManagementView> setShopOffline() {
        UserToken token = requireMerchant();
        Long merchantId = token.userId();
        Shop shop = service.setShopOffline(merchantId);
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }

    @PutMapping("/merchant/shop/detail")
    ApiResponse<ShopManagementView> updateShopDetail(@RequestBody UpdateContactRequest request) {
        UserToken token = requireMerchant();
        Long merchantId = token.userId();
        Shop shop = service.updateShopDetail(merchantId, request.name(),request.contact(), request.mobile());
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }


    private UserToken requireMerchant() {
        UserToken token = UserContext.require();
        if (token.role() != Role.MERCHANT) {
            throw new IllegalStateException("unauthenticated");
        }
        return token;
    }

    public record RegisterRequest(@Pattern(regexp = "^1[3-9]\\d{9}$") String mobile,
                                  @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 64) String name) { }

    public record LoginRequest(@NotBlank String mobile, @NotBlank String password) { }

    public record UpdateContactRequest(@Size(max = 64)String name,
                                       @Size(max = 64) String contact,
                                       @Pattern(regexp = "^1[3-9]\\d{9}$") String mobile) { }

    public record MerchantView(Long id, String mobile, String name) {
        static MerchantView of(Merchant merchant) {
            Long id = merchant.getId();
            String mobile = merchant.getMobile();
            String name = merchant.getName();
            return new MerchantView(id, mobile, name);
        }
    }

    public record SessionView(String id, Instant expiresAt) { }

    public record ShopManagementView(Long id, Long merchantId, String name, String contact, String mobile, String status, String rejectReason, Long auditorId, Instant auditedAt, Instant createdAt, Instant updatedAt) {
        static ShopManagementView of(Shop shop) {
            Long id = shop.getId();
            Long merchantId = shop.getMerchantId();
            String name = shop.getName();
            String contact = shop.getContact();
            String mobile = shop.getMobile();
            String status = shop.getStatus().name();
            String rejectReason = shop.getRejectReason();
            Long auditorId = shop.getAuditorId();
            Instant auditedAt = shop.getAuditTime();
            Instant createdAt = shop.getApplyTime();
            Instant updatedAt = shop.getUpdatedAt();
            return new ShopManagementView(id, merchantId, name, contact, mobile, status,
                    rejectReason, auditorId, auditedAt, createdAt, updatedAt);
        }
    }
}
