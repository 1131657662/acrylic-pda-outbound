-- R07-3 C 组：误批撤销的「申请人确认」数据模型（P-R07-08：审批人发起 → 申请人确认）
--
-- 设计缺口（此前记录为待决策 D3）：P-R07-08 只写了两方角色，**没有**确认机制的数据模型——既无
-- withdrawal_* 列、也无待确认状态。本迁移补上模型（纯增量 + 只放宽状态 CHECK），使「发起」与「确认」
-- 成为两个可分别审计的时刻：
--   EFFECTIVE --审批人发起--> WITHDRAW_PENDING --申请人确认--> WITHDRAWN（此时才逐笔回退账务并出新的结果版本）
-- 未定义处（不自行发明）：确认是否有期限、申请人是否可拒绝导致撤销失效——均无政策，故本迁移只提供模型。
--
-- 回滚：ALTER TABLE correction_document DROP FOREIGN KEY 两条；DROP COLUMN 四列；状态 CHECK 还原为五种。

ALTER TABLE correction_document
    ADD withdrawal_requested_by BINARY(16) NULL,
    ADD withdrawal_requested_at DATETIME(3) NULL,
    ADD withdrawal_confirmed_by BINARY(16) NULL,
    ADD withdrawal_confirmed_at DATETIME(3) NULL,
    ADD CONSTRAINT fk_correction_withdrawal_requester FOREIGN KEY (withdrawal_requested_by) REFERENCES app_user(id),
    ADD CONSTRAINT fk_correction_withdrawal_confirmer FOREIGN KEY (withdrawal_confirmed_by) REFERENCES app_user(id);

-- 状态 CHECK 只放宽（新增待确认态），不收紧任何原先合法的取值；约束名由 MySQL 自动生成，按定义内容自定位。
SET @status_check = (SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME AND cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='correction_document'
      AND cc.CHECK_CLAUSE LIKE '%status%' LIMIT 1);
SET @drop_sql = CONCAT('ALTER TABLE correction_document DROP CHECK `', @status_check, '`');
PREPARE drop_status FROM @drop_sql;
EXECUTE drop_status;
DEALLOCATE PREPARE drop_status;
ALTER TABLE correction_document
    ADD CONSTRAINT ck_correction_document_status
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','EFFECTIVE','REJECTED','WITHDRAWN','WITHDRAW_PENDING'));

-- 回执的命令类型清单必须与已实现的命令面同步：V47 放宽到五种，本步新增 WITHDRAW_CONFIRM，故一并纳入（只放宽）。
-- 维护规则：今后每新增一个命令类型，都要同时检查这张表的 CHECK 与 KINDS 列表。
SET @receipt_check = (SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME AND cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='correction_command_receipt'
      AND cc.CHECK_CLAUSE LIKE '%command_type%' LIMIT 1);
SET @drop_receipt = CONCAT('ALTER TABLE correction_command_receipt DROP CHECK `', @receipt_check, '`');
PREPARE drop_receipt FROM @drop_receipt;
EXECUTE drop_receipt;
DEALLOCATE PREPARE drop_receipt;
ALTER TABLE correction_command_receipt
    ADD CONSTRAINT ck_correction_receipt_command_type
        CHECK (command_type IN ('CREATE','APPROVE','WITHDRAW','WITHDRAW_CONFIRM','RECONCILE_OPEN','RECONCILE_ADVANCE'));

