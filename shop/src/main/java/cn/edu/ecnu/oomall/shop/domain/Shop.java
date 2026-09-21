package cn.edu.ecnu.oomall.shop.domain;

import java.time.Instant;

/** 顾客账户事实；密码字段只保存 BCrypt 哈希，绝不直接返回给接口层。 */
public class Shop {
    private Long id;
    private Long merchantId;
    private String name;
    private String contact;
    private String mobile;
    private ShopStatus status;
    private Instant applyTime;

    private String rejectReason;
    private Long auditorId;
    private Instant auditTime;
    private Instant updatedAt;

    public Shop(Long merchantId, String name, String contact, String mobile) {
        this.merchantId = merchantId;
        this.name = name;
        this.contact = contact;
        this.mobile = mobile;
        this.status = ShopStatus.NEW;
    }


    public Instant getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(Instant applyTime) {
        this.applyTime = applyTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public void setStatus(ShopStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public Instant getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(Instant auditTime) {
        this.auditTime = auditTime;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
