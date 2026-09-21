package cn.edu.ecnu.oomall.shop.domain;

/** 商店状态 */
public enum ShopStatus {
    NEW("待审核"),
    OFFLINE("已下线"),
    ONLINE("营业中"),
    REJECTED("已拒绝");

    private final String displayName;

    ShopStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
