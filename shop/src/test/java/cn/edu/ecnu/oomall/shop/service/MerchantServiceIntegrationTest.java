package cn.edu.ecnu.oomall.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import cn.edu.ecnu.oomall.core.exception.BusinessException;
import cn.edu.ecnu.oomall.shop.ShopApplication;
import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;
import cn.edu.ecnu.oomall.shop.repository.ShopRepository;

@SpringBootTest(classes = ShopApplication.class)
@Testcontainers
class MerchantServiceIntegrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("shop")
            .withUsername("tiny_oomall")
            .withPassword("tiny_oomall");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MerchantService service;

    @Autowired
    private ShopRepository shops;

    @Autowired
    private PlatformAdminService platformAdminService;

    @Test
    void duplicateMobileStillUsesTheExistingBusinessError() {
        service.register("13800000001", "password1", "Alice");

        assertThatThrownBy(() -> service.register("13800000001", "password2", "Bob"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mobile already registered");
    }

    @Test
    void reviewQueueMatchesShopNameExactly() {
        Shop shop = new Shop(1L, "Coffee Shop", "Alice", "13800000000");
        shop.setApplyTime(Instant.now());
        shops.insert(shop);

        assertThat(shops.findReviewQueue(null, "Coffee", 10, 0)).isEmpty();
        assertThat(shops.findReviewQueue(null, "Coffee Shop", 10, 0)).hasSize(1);
    }

    @Test
    void managementDetailReturnsShopAndRejectsUnknownShop() {
        Shop written = new Shop(2L, "Book Store", "Bob", "13900000000");
        written.setApplyTime(Instant.now());
        shops.insert(written);

        Shop read = platformAdminService.getManagementDetail(written.getId());

        assertThat(read.getId()).isEqualTo(written.getId());
        assertThat(read.getName()).isEqualTo("Book Store");
        assertThatThrownBy(() -> platformAdminService.getManagementDetail(999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.code()).isEqualTo("SHOP_NOT_FOUND");
                });
    }

    @Test
    void merchantCanPutAnOfflineShopOnline() {
        Shop written = new Shop(3L, "Online Shop", "Carol", "13700000000");
        written.setApplyTime(Instant.now());
        shops.insert(written);
        shops.updateStatus(written.getId(), ShopStatus.NEW, ShopStatus.OFFLINE, Instant.now());

        Shop onlineShop = service.setShopOnline(written.getMerchantId());

        assertThat(onlineShop.getStatus()).isEqualTo(ShopStatus.ONLINE);
        Shop storedShop = shops.findById(written.getId()).orElseThrow();
        assertThat(storedShop.getStatus()).isEqualTo(ShopStatus.ONLINE);
    }

    @Test
    void merchantCanPutAnOnlineShopOffline() {
        Shop written = new Shop(4L, "Offline Shop", "David", "13600000000");
        written.setApplyTime(Instant.now());
        shops.insert(written);
        shops.updateStatus(written.getId(), ShopStatus.NEW, ShopStatus.OFFLINE, Instant.now());
        service.setShopOnline(written.getMerchantId());

        Shop offlineShop = service.setShopOffline(written.getMerchantId());

        assertThat(offlineShop.getStatus()).isEqualTo(ShopStatus.OFFLINE);
        Shop storedShop = shops.findById(written.getId()).orElseThrow();
        assertThat(storedShop.getStatus()).isEqualTo(ShopStatus.OFFLINE);
    }

    @Test
    void platformAdminCanPutAnOnlineShopOffline() {
        Shop written = new Shop(5L, "Admin Offline Shop", "Eve", "13500000000");
        written.setApplyTime(Instant.now());
        shops.insert(written);
        shops.updateStatus(written.getId(), ShopStatus.NEW, ShopStatus.OFFLINE, Instant.now());
        shops.updateStatus(written.getId(), ShopStatus.OFFLINE, ShopStatus.ONLINE, Instant.now());

        Shop offlineShop = platformAdminService.setShopOffline(written.getId());

        assertThat(offlineShop.getStatus()).isEqualTo(ShopStatus.OFFLINE);
        Shop storedShop = shops.findById(written.getId()).orElseThrow();
        assertThat(storedShop.getStatus()).isEqualTo(ShopStatus.OFFLINE);
    }
}
