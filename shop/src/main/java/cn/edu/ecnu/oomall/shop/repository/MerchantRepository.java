package cn.edu.ecnu.oomall.shop.repository;

import java.util.Optional;

import cn.edu.ecnu.oomall.shop.domain.Merchant;


public interface MerchantRepository {
    Optional<Merchant> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    int insert(Merchant merchant);
}
