package cn.edu.ecnu.oomall.customer.domain;

import java.time.Instant;

/** 顾客私有收货地址；归属判断始终以 customerId 和地址 id 联合完成。 */
public class CustomerAddress {
    private Long id;
    private Long customerId;
    private String address;
    private String consignee;
    private String mobile;
    private boolean beDefault;
    private Instant createdAt = Instant.now();

    public CustomerAddress() {
    }

    public CustomerAddress(Long customerId, String consignee, String mobile, String address, boolean beDefault) {
        this.customerId = customerId;
        this.consignee = consignee;
        this.mobile = mobile;
        this.address = address;
        this.beDefault = beDefault;
    }

    public void update(String consignee, String mobile, String address, boolean beDefault) {
        this.consignee = consignee;
        this.mobile = mobile;
        this.address = address;
        this.beDefault = beDefault;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getConsignee() {
        return consignee;
    }

    public String getMobile() {
        return mobile;
    }

    public String getAddress() {
        return address;
    }

    public boolean isBeDefault() {
        return beDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setConsignee(String consignee) {
        this.consignee = consignee;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public void setBeDefault(boolean beDefault) {
        this.beDefault = beDefault;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
