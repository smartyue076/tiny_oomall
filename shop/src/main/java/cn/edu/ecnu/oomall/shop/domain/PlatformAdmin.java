package cn.edu.ecnu.oomall.shop.domain;

import java.time.Instant;

/** 商户账户事实；密码字段只保存 BCrypt 哈希，绝不直接返回给接口层。 */
public class PlatformAdmin {
    private Long id;

    private String mobile;

    private String password;

    private String name;

    private String status = "NORMAL";

    private Instant createdAt = Instant.now();

    public PlatformAdmin() {
    }

    public PlatformAdmin(String mobile, String password, String name) {
        this.mobile = mobile;
        this.password = password;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
