-- ============================================
-- Database Initialization Script
-- Version: 1.0.0
-- Created: 2026-03-17
-- Description: Creates databases for Panda Apps
-- ============================================

-- Set default character set
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================
-- Create gridtrading_db database
-- ============================================
CREATE DATABASE IF NOT EXISTS gridtrading_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- ============================================
-- Create snapledger_db database
-- ============================================
CREATE DATABASE IF NOT EXISTS snapledger_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- ============================================
-- Grant privileges to user
-- Note: Replace 'your_username' and 'your_password' with actual credentials
-- ============================================

-- Grant privileges for gridtrading_db
GRANT ALL PRIVILEGES ON gridtrading_db.* TO 'gridtrading_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON gridtrading_db.* TO 'gridtrading_user'@'%' IDENTIFIED BY 'your_password';

-- Grant privileges for snapledger_db
GRANT ALL PRIVILEGES ON snapledger_db.* TO 'snapledger_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON snapledger_db.* TO 'snapledger_user'@'%' IDENTIFIED BY 'your_password';

-- Apply privilege changes
FLUSH PRIVILEGES;

-- ============================================
-- Verification
-- ============================================
SELECT
    SCHEMA_NAME as 'Database',
    DEFAULT_CHARACTER_SET_NAME as 'Charset',
    DEFAULT_COLLATION_NAME as 'Collation'
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('gridtrading_db', 'snapledger_db');
