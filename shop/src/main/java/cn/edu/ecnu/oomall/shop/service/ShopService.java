package cn.edu.ecnu.oomall.shop.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.repository.ShopRepository;

@Service
public class ShopService {
    private final ShopRepository shops;
    private final PasswordEncoder encoder;
    private final StringRedisTemplate redis;

    public ShopService(ShopRepository shops, PasswordEncoder encoder, StringRedisTemplate redis) {
        this.shops = shops;
        this.encoder = encoder;
        this.redis = redis;
    }

    public Shop createShopApplication(Long merchantId, String name, String contact, String mobile) {
        // 商店名不能重复
        if (shops.existsByName(name)) {
            throw new BusinessException("NAME_EXISTS", HttpStatus.CONFLICT, "name already registered");
        }
        // 一个商户只能有一个商店
        if (shops.existsByMerchantId(merchantId)) {
            throw new BusinessException("MERCHANT_EXISTS", HttpStatus.CONFLICT, "You already has a shop");
        }

        System.out.println("Creating shop application with name: " + name + ", contact: " + contact + ", mobile: " + mobile);
        mobile = mobile.trim();
        Shop shop = new Shop(merchantId, name, contact, mobile);
        shop.setApplyTime(Instant.now());

        // id是如何获取的？
        // MyBatis 会在插入后自动设置 id 字段，因为我们在 ShopRepository.xml 中使用了 useGeneratedKeys="true" keyProperty="id"。
        shops.insert(shop);
        return shop;
    }


    public Page<Shop> getOnlineShopsQueue(int page, int pageSize) {
        long total = shops.countOnlineShopsQueue();
        long offset = (long) page * pageSize;
        List<Shop> shopsInPage = shops.findOnlineShopsQueue(pageSize, offset);
        return new PageImpl<>(shopsInPage, PageRequest.of(page, pageSize), total);
    }
}
