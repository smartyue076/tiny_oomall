package cn.edu.ecnu.oomall.shop.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.edu.ecnu.oomall.core.auth.Role;
import cn.edu.ecnu.oomall.core.auth.UserToken;
import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.shop.domain.Merchant;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.repository.MerchantRepository;
import cn.edu.ecnu.oomall.shop.repository.ShopRepository;

@Service
public class MerchantService {
    private final MerchantRepository merchants;
    private final ShopRepository shops;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;
    private final Duration ttl;

    public MerchantService(MerchantRepository merchants, ShopRepository shops, PasswordEncoder encoder,
            StringRedisTemplate redis,
            @Value("${customer.session-ttl:PT24H}") Duration ttl) {
        this.merchants = merchants;
        this.shops = shops;
        this.encoder = encoder;
        this.redis = redis;
        this.ttl = ttl;
    }

    public Merchant register(String mobile, String password, String name) {
        mobile = mobile.trim();
        if (merchants.existsByMobile(mobile)) {
            throw new BusinessException("MOBILE_EXISTS", HttpStatus.CONFLICT, "mobile already registered");
        }
        System.out.println("Registering merchant with mobile: " + mobile + ", name: " + name);
        String encodedPassword = encoder.encode(password);
        String trimmedName = name.trim();
        Merchant merchant = new Merchant(mobile, encodedPassword, trimmedName);
        merchants.insert(merchant);
        return merchant;
    }

    public Session login(String mobile, String password) {
        String trimmedMobile = mobile.trim();
        Optional<Merchant> optionalMerchant = merchants.findByMobile(trimmedMobile);
        if (optionalMerchant.isEmpty()) {
            throw invalidCredential();
        }
        Merchant merchant = optionalMerchant.get();
        if (!"NORMAL".equals(merchant.getStatus())) {
            throw new BusinessException("MERCHANT_DISABLED", HttpStatus.FORBIDDEN, "merchant disabled");
        }
        if (!encoder.matches(password, merchant.getPassword())) {
            throw invalidCredential();
        }
        UUID uuid = UUID.randomUUID();
        String id = uuid.toString().replace("-", "");
        try {
            String sessionKey = key(id);
            String sessionValue = merchant.getId() + ":" + Role.MERCHANT.name();
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

    public Shop getShopDetail(Long merchantId) {
        Optional<Shop> optionalShop = shops.findByMerchantId(merchantId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }
        return optionalShop.get();
    }

    public Shop setShopOnline(Long merchantId) {
        Optional<Shop> optionalShop = shops.findByMerchantId(merchantId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }
        Shop shop = optionalShop.get();
        if (shop.getStatus() == ShopStatus.REJECTED) {
            throw new BusinessException("APPLICATION_REJECTED", HttpStatus.BAD_REQUEST,
                    "shop is not premitted to go online");
        } else if (shop.getStatus() == ShopStatus.ONLINE) {
            throw new BusinessException("SHOP_ALREADY_ONLINE", HttpStatus.BAD_REQUEST, "shop is already online");
        } else if (shop.getStatus() == ShopStatus.NEW) {
            throw new BusinessException("SHOP_NOT_APPROVED", HttpStatus.BAD_REQUEST, "shop is not approved yet");
        }
        if (shop.getStatus() != ShopStatus.OFFLINE) {
            throw new BusinessException("SHOP_NOT_OFFLINE", HttpStatus.BAD_REQUEST, "shop is not offline");
        }

        Instant updatedAt = Instant.now();
        int affectedRows = shops.updateStatus(
                shop.getId(), ShopStatus.OFFLINE, ShopStatus.ONLINE, updatedAt);
        if (affectedRows != 1) {
            throw new BusinessException("SHOP_NOT_OFFLINE", HttpStatus.BAD_REQUEST, "shop is not offline");
        }

        shop.setStatus(ShopStatus.ONLINE);
        shop.setUpdatedAt(updatedAt);
        return shop;
    }

    public Shop setShopOffline(Long merchantId) {
        Optional<Shop> optionalShop = shops.findByMerchantId(merchantId);
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

    public Shop updateShopDetail(Long merchantId, String name, String contact, String mobile) {
        Optional<Shop> optionalShop = shops.findByMerchantId(merchantId);
        if (optionalShop.isEmpty()) {
            throw new BusinessException("SHOP_NOT_FOUND", HttpStatus.NOT_FOUND, "shop not found");
        }

        Shop shop = optionalShop.get();
        if (name != null && !name.isEmpty())
            shop.setName(name);
        if (contact != null && !contact.isEmpty())
            shop.setContact(contact);
        if (mobile != null && !mobile.isEmpty())
            shop.setMobile(mobile);
        shop.setUpdatedAt(Instant.now());

        int affectedRows = shops.updateDetail(shop);
        if (affectedRows != 1) {
            throw new BusinessException("SHOP_UPDATE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "failed to update shop details");
        }

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
            Long merchantId = Long.valueOf(parts[0]);
            Role role = Role.valueOf(parts[1]);
            UserToken token = new UserToken(merchantId, role);
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

    public record Session(String id, Instant expiresAt) {
    }
}
