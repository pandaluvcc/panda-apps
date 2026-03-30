-- V2__add_record_reconciliation.sql

ALTER TABLE sl_record ADD COLUMN reconciliation_status VARCHAR(20) DEFAULT 'UNRECONCILED';
ALTER TABLE sl_record ADD COLUMN postponed_to_cycle VARCHAR(10);
