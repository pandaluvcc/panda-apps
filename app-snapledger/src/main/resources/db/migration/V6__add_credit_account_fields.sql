-- 添加信用账户扩展字段
ALTER TABLE sl_account
  ADD COLUMN credit_due_date      DATE            NULL                COMMENT '到期还款日'      AFTER bill_cycle_end,
  ADD COLUMN credit_limit         DECIMAL(12,2)   NULL                COMMENT '信用额度'        AFTER credit_due_date,
  ADD COLUMN credit_limit_sharing VARCHAR(100)    NULL                COMMENT '额度共享'        AFTER credit_limit,
  ADD COLUMN master_account_name  VARCHAR(100)    NULL                COMMENT '主账户名称'      AFTER credit_limit_sharing,
  ADD COLUMN auto_debit_account   VARCHAR(100)    NULL                COMMENT '自动扣缴账户'    AFTER master_account_name,
  ADD COLUMN bill_discount        TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '账单折抵'        AFTER auto_debit_account,
  ADD COLUMN interest_free_recommend TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '免息期推荐'      AFTER bill_discount,
  ADD COLUMN cashback_info        VARCHAR(200)    NULL                COMMENT '返利回馈描述'    AFTER interest_free_recommend;
