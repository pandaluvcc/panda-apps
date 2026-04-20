ALTER TABLE sl_record ADD COLUMN parent_record_id BIGINT NULL;
CREATE INDEX idx_record_parent ON sl_record(parent_record_id);
