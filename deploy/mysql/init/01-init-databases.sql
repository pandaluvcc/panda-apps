-- 初始化数据库脚本
-- 字符集: utf8mb4

-- 创建 gridtrading_db 数据库
CREATE DATABASE IF NOT EXISTS gridtrading_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 创建 snapledger_db 数据库
CREATE DATABASE IF NOT EXISTS snapledger_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 授权 (如果需要单独的应用用户，可在此添加)
-- GRANT ALL PRIVILEGES ON gridtrading_db.* TO 'app_user'@'%';
-- GRANT ALL PRIVILEGES ON snapledger_db.* TO 'app_user'@'%';
-- FLUSH PRIVILEGES;
