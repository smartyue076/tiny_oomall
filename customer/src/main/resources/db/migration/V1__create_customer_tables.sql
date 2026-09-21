CREATE TABLE customer_customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mobile VARCHAR(32) NOT NULL,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_mobile (mobile)
);

CREATE TABLE customer_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    address VARCHAR(500) NOT NULL,
    consignee VARCHAR(128) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    be_default BOOLEAN NOT NULL DEFAULT FALSE,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_address_customer (customer_id)
);
