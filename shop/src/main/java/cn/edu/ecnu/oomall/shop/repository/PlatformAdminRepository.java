package cn.edu.ecnu.oomall.shop.repository;

import java.util.Optional;

import cn.edu.ecnu.oomall.shop.domain.PlatformAdmin;


public interface PlatformAdminRepository {
    Optional<PlatformAdmin> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    int insert(PlatformAdmin platformAdmin);
}
