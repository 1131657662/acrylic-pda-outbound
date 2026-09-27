-- R07-3 A3（步骤 7）的两个缺口，均按 R07-1 的教训（权限码必须随能力一并登记）与回执可空性处理：
-- 1) `inventory:reconcile` 在 §5 里标注为"拟"，此前**从未登记**；若等到接线后再补，就会出现 R07-1/V44 那种
--    "审批门权限码不存在"的陷阱。此处随本步一并登记并绑 WAREHOUSE_ADMIN。
-- 2) `correction_command_receipt.order_id` 原为 NOT NULL（V45，照 R06 回执形态）；差异案件命令没有订单，
--    故放宽为可空。只放宽，不收紧，既有回执行不受影响。

INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000047'),'inventory:reconcile','处理库存差异案件');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
WHERE r.code='WAREHOUSE_ADMIN' AND p.code='inventory:reconcile';

ALTER TABLE correction_command_receipt MODIFY order_id BINARY(16) NULL;

-- 3) V45 把回执的 command_type 限死在三种文档命令上；步骤 7 的差异案件命令走同一命令面（同一开关、同一回执骨架），
--    故把该 CHECK **放宽**到五种已实现命令。只放宽，不收紧。
--    约束名由 MySQL 自动生成（chk_1/chk_2...），此处按定义内容自定位，避免写死一个猜测的名字。
SET @check_name = (SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME AND cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='correction_command_receipt'
      AND cc.CHECK_CLAUSE LIKE '%command_type%' LIMIT 1);
SET @drop_sql = CONCAT('ALTER TABLE correction_command_receipt DROP CHECK `', @check_name, '`');
PREPARE drop_check FROM @drop_sql;
EXECUTE drop_check;
DEALLOCATE PREPARE drop_check;
ALTER TABLE correction_command_receipt
    ADD CONSTRAINT ck_correction_receipt_command_type
        CHECK (command_type IN ('CREATE','APPROVE','WITHDRAW','RECONCILE_OPEN','RECONCILE_ADVANCE'));
