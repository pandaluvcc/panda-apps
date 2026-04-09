-- 账户分组默认字典数据
-- 每次启动自动执行，INSERT IGNORE 保证幂等（数据已存在则跳过）

INSERT IGNORE INTO sl_account_group (name, sort_order, is_system) VALUES
('第三方支付', 10, 1),
('现金',       20, 1),
('银行',       30, 1),
('信用卡',     40, 1),
('保单',       50, 1),
('证券户',     60, 1),
('加密货币',   70, 1),
('其他',       80, 1)
