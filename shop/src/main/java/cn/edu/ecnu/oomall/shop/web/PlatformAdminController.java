package cn.edu.ecnu.oomall.shop.web;

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
import cn.edu.ecnu.oomall.shop.domain.PlatformAdmin;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.service.PlatformAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class PlatformAdminController {
    private final PlatformAdminService service;

    public PlatformAdminController(PlatformAdminService service) {
        this.service = service;
    }

    @PostMapping("/platform-admins")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PlatformAdminView> register(@Valid @RequestBody RegisterRequest request) {
        PlatformAdmin platformAdmin = service.register(
                request.mobile(), request.password(), request.name());
        PlatformAdminView view = PlatformAdminView.of(platformAdmin);
        return ApiResponse.ok(view);
    }

    @PostMapping("/auth/platform-admin-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest request) {
        PlatformAdminService.Session session = service.login(request.mobile(), request.password());
        SessionView view = new SessionView(session.id(), session.expiresAt());
        return ApiResponse.ok(view);
    }

    @DeleteMapping("/auth/platform-admin-sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("X-Session-Id") String sessionId) {
        service.logout(sessionId);
    }

    @GetMapping("/shop-review-queue")
    ApiResponse<Page<ShopManagementView>> list(@RequestParam(required = false) ShopStatus status,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        requireplatformAdmin();
        Page<Shop> shopPage = service.getShopReviewQueue(status, name, page, pageSize);
        List<ShopManagementView> views = new ArrayList<>();
        for (Shop shop : shopPage.getContent()) {
            ShopManagementView view = ShopManagementView.of(shop);
            views.add(view);
        }
        Page<ShopManagementView> viewPage = new PageImpl<>(
                views, shopPage.getPageable(), shopPage.getTotalElements());
        return ApiResponse.ok(viewPage);
    }

    @GetMapping("/shops/{shopId}/management-detail")
    ApiResponse<ShopManagementView> getManagementDetail(@PathVariable Long shopId) {
        requireplatformAdmin();
        Shop shop = service.getManagementDetail(shopId);
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }

    @PutMapping("/shop-applications/{shopId}/review")
    ApiResponse<ShopManagementView> setShopApplicationReview(@PathVariable Long shopId,@Valid @RequestBody ShopApplicationReviewRequest request) {
        UserToken token = requireplatformAdmin();
        Long platformAdminId = token.userId();
        Shop reviewedShop = service.setShopApplicationReview(shopId,platformAdminId,request.conclusion(), request.reason());
        ShopManagementView view = ShopManagementView.of(reviewedShop);
        return ApiResponse.ok(view);
    }

    @PutMapping("/shops/{shopId}/offline")
    ApiResponse<ShopManagementView> setShopOffline(@PathVariable Long shopId) {
        requireplatformAdmin();
        Shop shop = service.setShopOffline(shopId);
        ShopManagementView view = ShopManagementView.of(shop);
        return ApiResponse.ok(view);
    }

    private UserToken requireplatformAdmin() {
        UserToken token = UserContext.require();
        if (token.role() != Role.PLATFORM_ADMIN) {
            throw new IllegalStateException("unauthenticated");
        }
        return token;
    }

    public record RegisterRequest(@Pattern(regexp = "^1[3-9]\\d{9}$") String mobile,
                                  @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 64) String name) { }

    public record LoginRequest(@NotBlank String mobile, @NotBlank String password) { }

    public record ShopApplicationReviewRequest(@NotBlank String conclusion, @Size(max = 255) String reason) { }

    public record PlatformAdminView(Long id, String mobile, String name) {
        static PlatformAdminView of(PlatformAdmin platformAdmin) {
            Long id = platformAdmin.getId();
            String mobile = platformAdmin.getMobile();
            String name = platformAdmin.getName();
            return new PlatformAdminView(id, mobile, name);
        }
    }

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



    public record SessionView(String id, Instant expiresAt) { }
}
