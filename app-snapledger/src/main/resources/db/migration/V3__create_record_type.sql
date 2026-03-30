-- V3__create_record_type.sql

CREATE TABLE sl_record_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type_code VARCHAR(20) NOT NULL UNIQUE,
    type_name VARCHAR(50),
    default_main_category VARCHAR(50),
    icon VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化记录类型
INSERT INTO sl_record_type (type_code, type_name) VALUES
('income', '收入'),
('expense', '支出'),
('transfer', '转账'),
('receivable', '应收账款'),
('payable', '应付账款'),
('adjustment', '余额调整');
