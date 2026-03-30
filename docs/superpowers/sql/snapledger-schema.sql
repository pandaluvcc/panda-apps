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

-- 记录类型表
CREATE TABLE IF NOT EXISTS sl_record_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(20) NOT NULL UNIQUE,
    type_name VARCHAR(50),
    default_main_category VARCHAR(50),
    icon VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 主类别表（关联记录类型）
CREATE TABLE IF NOT EXISTS sl_main_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_type VARCHAR(20),
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(50),
    icon VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_record_type (record_type)
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
    icon VARCHAR(50),
    balance DECIMAL(12, 2) DEFAULT 0,
    remark VARCHAR(500),
    name VARCHAR(100) NOT NULL UNIQUE,
    main_currency VARCHAR(10) DEFAULT 'CNY',
    account_group VARCHAR(50),
    initial_balance DECIMAL(12, 2) DEFAULT 0,
    bill_cycle_start DATE,
    bill_cycle_end DATE,
    is_credit_account TINYINT(1) DEFAULT 0,
    is_master_account TINYINT(1) DEFAULT 0,
    cashback DECIMAL(12, 2) DEFAULT 0,
    auto_rollover TINYINT(1) DEFAULT 0,
    foreign_transaction_fee TINYINT(1) DEFAULT 0,
    include_in_total TINYINT(1) DEFAULT 1,
    is_archived TINYINT(1) DEFAULT 0,
    show_on_widget TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_account_group (account_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 项目表
CREATE TABLE IF NOT EXISTS sl_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    icon VARCHAR(50),
    name VARCHAR(100) NOT NULL UNIQUE,
    main_currency VARCHAR(10) DEFAULT 'CNY',
    cycle_type VARCHAR(20),
    settlement_cycle INT DEFAULT 1,
    cycle_unit VARCHAR(10),
    start_date DATE,
    auto_rollup_budget TINYINT(1) DEFAULT 0,
    show_on_home TINYINT(1) DEFAULT 0,
    include_in_stats TINYINT(1) DEFAULT 0,
    remark VARCHAR(500),
    budget_amount DECIMAL(12, 2) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预算表（保留，后续清理）
CREATE TABLE IF NOT EXISTS sl_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    year INT NOT NULL,
    month INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_year_month (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
