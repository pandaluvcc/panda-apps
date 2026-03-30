-- V1__add_account_fields.sql

ALTER TABLE sl_account ADD COLUMN icon VARCHAR(50);
ALTER TABLE sl_account ADD COLUMN account_group VARCHAR(50);
ALTER TABLE sl_account ADD COLUMN main_currency VARCHAR(10) DEFAULT 'CNY';
ALTER TABLE sl_account ADD COLUMN initial_balance DECIMAL(12, 2) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN bill_cycle_start DATE;
ALTER TABLE sl_account ADD COLUMN bill_cycle_end DATE;
ALTER TABLE sl_account ADD COLUMN is_credit_account TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN is_master_account TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN cashback DECIMAL(12, 2) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN auto_rollover TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN foreign_transaction_fee TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN include_in_total TINYINT(1) DEFAULT 1;
ALTER TABLE sl_account ADD COLUMN is_archived TINYINT(1) DEFAULT 0;
ALTER TABLE sl_account ADD COLUMN show_on_widget TINYINT(1) DEFAULT 1;
ALTER TABLE sl_account ADD COLUMN remark VARCHAR(500);
ALTER TABLE sl_account ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sl_account ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 更新 name 字段长度
ALTER TABLE sl_account MODIFY COLUMN name VARCHAR(100) NOT NULL UNIQUE;
