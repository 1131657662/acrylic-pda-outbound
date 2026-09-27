-- R07-3 更正命令内核的存储与权限码（步骤 2 第一片）
--
-- 背景一（回执表缺失）：V43 只建了更正领域表（correction_document/line/effect/ledger、cycle_reopening、
-- inventory_reconciliation、order_current_result），**没有回执表**；而 §6 要求命令面沿用 R06/R07-1 已实证的
-- 「原键幂等 + 不可变签名回执 + 单一审计事件」骨架。故本迁移按 V42 的 handover_command_receipt 形态建
-- correction_command_receipt：original_key 唯一 → 重试回放原字节；一行回执配一条审计事件。
--
-- 背景二（R07-1 教训，必须同批解决）：R07-1 的命令层引用了 order:handover 作为审批门，但当时没有任何迁移把
-- 该权限码注册进 permission 表（V44 事后补），导致 role_permission 静默插 0 行、审批门在生产同样不可满足。
-- 本迁移因此在建立更正命令面的同时登记其权限码，避免重演。
--
-- 权限码为设计 §5 的草案（标注「拟」）：outbound:correct 用于建单/读取/撤销确认，correction:approve 用于
-- 主管审批门。二者先绑 WAREHOUSE_ADMIN（与 order:resume/recall/dispose/handover 等审批类权限一致），
-- 能力开放（开关 from false to true）前需业主确认岗位绑定。

CREATE TABLE correction_command_receipt (
    id BINARY(16) PRIMARY KEY,
    document_id BINARY(16) NULL,
    order_id BINARY(16) NOT NULL,
    original_key BINARY(16) NOT NULL,
    command_type VARCHAR(24) NOT NULL,
    command_sha256 BINARY(32) NOT NULL,
    canonical_command_bytes MEDIUMBLOB NOT NULL,
    response_bytes MEDIUMBLOB NOT NULL,
    response_sha256 BINARY(32) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    expected_json MEDIUMTEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    event_id BINARY(16) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_correction_receipt_key (original_key),
    KEY ix_correction_receipt_document (document_id, recorded_at),
    KEY ix_correction_receipt_order (order_id, recorded_at),
    CONSTRAINT fk_correction_receipt_document FOREIGN KEY (document_id) REFERENCES correction_document(id),
    CONSTRAINT fk_correction_receipt_order FOREIGN KEY (order_id) REFERENCES outbound_order(id),
    CHECK (command_type IN ('CREATE','APPROVE','WITHDRAW')),
    CHECK (status IN ('RECORDED')),
    CHECK (JSON_VALID(expected_json))
) ENGINE=InnoDB;

INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-00000000ff11'),'outbound:correct','更正单建立/读取（R07-3，草案）');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-00000000ff12'),'correction:approve','更正主管审批（R07-3，草案）');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('outbound:correct','correction:approve');
