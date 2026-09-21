CREATE TABLE shop_shop (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    contact VARCHAR(128) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    reject_reason VARCHAR(500) NULL,
    auditor_id BIGINT NULL,
    audit_time TIMESTAMP NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_merchant (merchant_id),
    UNIQUE KEY uk_shop_name (name),
    KEY idx_shop_public_list (status, id)
);

CREATE TABLE shop_merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mobile VARCHAR(32) NOT NULL,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_mobile (mobile)
);

CREATE TABLE shop_platform_admin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mobile VARCHAR(32) NOT NULL,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_admin_mobile (mobile)
);
