package cn.edu.ecnu.oomall.shop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

import cn.edu.ecnu.oomall.shop.domain.Shop;
import cn.edu.ecnu.oomall.shop.domain.ShopStatus;


public interface ShopRepository {
    Optional<Shop> findById(Long id);
    Optional<Shop> findByName(String name);
    Optional<Shop> findByMerchantId(Long merchantId);
    boolean existsByName(String name);
    boolean existsByMerchantId(Long merchantId);
    int insert(Shop shop);
    List<Shop> findReviewQueue(@Param("status") ShopStatus status, @Param("name") String name,
                               @Param("limit") int limit, @Param("offset") long offset);
    long countReviewQueue(@Param("status") ShopStatus status, @Param("name") String name);
    long countOnlineShopsQueue();
    List<Shop> findOnlineShopsQueue(@Param("limit") int limit, @Param("offset") long offset);
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") ShopStatus expectedStatus,
                     @Param("status") ShopStatus status,
                     @Param("updatedAt") Instant updatedAt);
    int updateReview(Shop shop);
    int updateDetail(Shop shop);
}
