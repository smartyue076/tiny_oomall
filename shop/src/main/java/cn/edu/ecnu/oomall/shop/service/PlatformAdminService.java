package cn.edu.ecnu.oomall.shop.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.shop.domain.PlatformAdmin;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.repository.PlatformAdminRepository;
import cn.edu.ecnu.oomall.shop.repository.ShopRepository;

@Service
public class PlatformAdminService {
    private final PlatformAdminRepository platformAdmins;
    private final ShopRepository shops;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public PlatformAdminService(PlatformAdminRepository platformAdmins, ShopRepository shops,
                                PasswordEncoder encoder, StringRedisTemplate redis,
                                @Value("${customer.session-ttl:PT24H}") Duration ttl) {
        this.platformAdmins = platformAdmins;
        this.shops = shops;
        this.encoder = encoder;
        this.redis = redis;
        this.ttl = ttl;
    }

    public PlatformAdmin register(String mobile, String password, String name) {
        mobile = mobile.trim();
        if (platformAdmins.existsByMobile(mobile)) {
            throw new BusinessException("MOBILE_EXISTS", HttpStatus.CONFLICT, "mobile already registered");
        }
        System.out.println("Registering platform administrator with mobile: " + mobile + ", name: " + name);
        String encodedPassword = encoder.encode(password);
        String trimmedName = name.trim();
        PlatformAdmin platformAdmin = new PlatformAdmin(mobile, encodedPassword, trimmedName);
        platformAdmins.insert(platformAdmin);
        return platformAdmin;
    }

    public Session login(String mobile, String password) {
        String trimmedMobile = mobile.trim();
        Optional<PlatformAdmin> optionalPlatformAdmin = platformAdmins.findByMobile(trimmedMobile);
        if (optionalPlatformAdmin.isEmpty()) {
            throw invalidCredential();
        }
        PlatformAdmin platformAdmin = optionalPlatformAdmin.get();
        if (!"NORMAL".equals(platformAdmin.getStatus())) {
            throw new BusinessException("PLATFORM_ADMIN_DISABLED", HttpStatus.FORBIDDEN, "platform administrator disabled");
        }
        if (!encoder.matches(password, platformAdmin.getPassword())) {
            throw invalidCredential();
        }
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString().replace("-", "");
        try {
            String sessionKey = key(id);
            String sessionValue = platformAdmin.getId() + ":" + Role.PLATFORM_ADMIN.name();
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
        redis.delete(sessionKey);
    }

    public Page<Shop> getShopReviewQueue(ShopStatus status, String name, int page, int pageSize) {
        String normalizedName = null;
        if (name != null) {
            normalizedName = name.trim();
        }
        if (normalizedName != null && normalizedName.isEmpty()) {
            normalizedName = null;
        }

        long total = shops.countReviewQueue(status, normalizedName);
        long offset = (long) page * pageSize;
        List<Shop> shopsInPage = shops.findReviewQueue(status, normalizedName, pageSize, offset);
        return new PageImpl<>(shopsInPage, PageRequest.of(page, pageSize), total);
    }

    public Shop getManagementDetail(Long shopId) {
        Optional<Shop> optionalShop = shops.findById(shopId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }
        Shop shop = optionalShop.get();
        return shop;
    }

    public Shop setShopApplicationReview(Long shopId,Long platformAdminId,String conclusion,String reason) {
        Optional<Shop> optionalShop = shops.findById(shopId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }
        Shop shop = optionalShop.get();
        if (shop.getStatus() == ShopStatus.ONLINE) {
            throw new BusinessException("REVIEWED_SHOP", HttpStatus.BAD_REQUEST, "shop is online");
        }

        // Update the shop status based on the conclusion
        if ("APPROVED".equals(conclusion)) {
            shop.setStatus(ShopStatus.OFFLINE);
        } else if ("REJECTED".equals(conclusion)) {
            shop.setStatus(ShopStatus.REJECTED);
        } else {
            throw new BusinessException("INVALID_CONCLUSION", HttpStatus.BAD_REQUEST, "conclusion must be APPROVED or REJECTED");
        }

        shop.setAuditorId(platformAdminId);
        shop.setRejectReason(reason);
        shop.setUpdatedAt(Instant.now());


        int affectedRows = shops.updateReview(shop);
        if (affectedRows != 1) {
            throw new BusinessException(
                    "REVIEWED_SHOP",
                    HttpStatus.BAD_REQUEST,
                    "shop is not pending review");
        }

        return shop;
    }

    public Shop setShopOffline(Long shopId) {
        Optional<Shop> optionalShop = shops.findById(shopId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }

        Shop shop = optionalShop.get();
        if (shop.getStatus() != ShopStatus.ONLINE) {
            throw new BusinessException("SHOP_NOT_ONLINE", HttpStatus.BAD_REQUEST, "shop is not online");
        }

        Instant updatedAt = Instant.now();
        int affectedRows = shops.updateStatus(
                shop.getId(), ShopStatus.ONLINE, ShopStatus.OFFLINE, updatedAt);
        if (affectedRows != 1) {
            throw new BusinessException("SHOP_NOT_ONLINE", HttpStatus.BAD_REQUEST, "shop is not online");
        }

        shop.setStatus(ShopStatus.OFFLINE);
        shop.setUpdatedAt(updatedAt);
        return shop;
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
            Long platformAdminId = Long.valueOf(parts[0]);
            Role role = Role.valueOf(parts[1]);
            UserToken token = new UserToken(platformAdminId, role);
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
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
