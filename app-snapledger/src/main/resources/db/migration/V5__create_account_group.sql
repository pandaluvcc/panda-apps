-- V5__create_account_group.sql
-- 账户分组字典表

CREATE TABLE sl_account_group (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL UNIQUE COMMENT '分组名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    is_system  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置分组（内置分组不可删除）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 内置默认分组（INSERT IGNORE 保证幂等）
INSERT IGNORE INTO sl_account_group (name, sort_order, is_system) VALUES
('第三方支付', 10, 1),
('现金',       20, 1),
('银行',       30, 1),
('信用卡',     40, 1),
('保单',       50, 1),
('证券户',     60, 1),
('加密货币',   70, 1),
('其他',       80, 1);
