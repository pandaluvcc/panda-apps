-- SnapLedger 数据库表结构
-- 执行前请确保数据库已创建

-- 记账记录表
CREATE TABLE IF NOT EXISTS sl_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account VARCHAR(50),
    currency VARCHAR(10) DEFAULT 'CNY',
    record_type VARCHAR(20),
    main_category VARCHAR(50),
    sub_category VARCHAR(50),
    amount DECIMAL(12, 2) NOT NULL,
    fee DECIMAL(12, 2) DEFAULT 0,
    discount DECIMAL(12, 2) DEFAULT 0,
    name VARCHAR(100),
    merchant VARCHAR(100),
    date DATE NOT NULL,
    time TIME,
    project VARCHAR(50),
    description VARCHAR(500),
    tags VARCHAR(200),
    target VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_main_category (main_category),
    INDEX idx_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类表
CREATE TABLE IF NOT EXISTS sl_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    main_category VARCHAR(50) NOT NULL,
    sub_category VARCHAR(50),
    type VARCHAR(20) NOT NULL,
    icon VARCHAR(50),
    UNIQUE KEY uk_category (main_category, sub_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 账户表
CREATE TABLE IF NOT EXISTS sl_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20),
    balance DECIMAL(12, 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预算表
CREATE TABLE IF NOT EXISTS sl_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    year INT NOT NULL,
    month INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_year_month (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
