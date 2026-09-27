SET SESSION lock_wait_timeout=10;

-- R07-1（原机可用正式单交接）的最小共同模型。本迁移不写入任何业务行：案件只在显式命令后出现。
-- 边界：仅 FORMAL 来源（TEMP 适配属 R07-2）；故障换机属 R07-5，stop_state 保留 FAULT_FENCED 取值但本批不可达。
-- 设计依据 docs/parallel/R07/HANDOVER_DESIGN.md §3 状态模型与 §4 原机可用流程。

CREATE TABLE handover_case (
    id BINARY(16) PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    submission_id BINARY(16) NOT NULL,
    canonical_order_id BINARY(16) NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    from_work_round INT NOT NULL,
    to_work_round INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    stop_state VARCHAR(24) NOT NULL,
    integrity_state VARCHAR(24) NOT NULL,
    original_result_state VARCHAR(24) NOT NULL,
    physical_state VARCHAR(24) NOT NULL,
    -- 1 = 未结案。MySQL 唯一索引忽略 NULL，因此同一来源同时只能有一个开案，结案后置 NULL。
    open_flag TINYINT NULL,
    input_high_watermark BIGINT NULL,
    target_device_id BINARY(16) NULL,
    target_user_id BINARY(16) NULL,
    prepared_ticket_sha256 BINARY(32) NULL,
    prepared_at DATETIME(6) NULL,
    activation_receipt MEDIUMBLOB NULL,
    activation_sha256 BINARY(32) NULL,
    activated_at DATETIME(6) NULL,
    requested_by BINARY(16) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_by BINARY(16) NULL,
    approved_at DATETIME(6) NULL,
    closed_reason VARCHAR(200) NULL,
    row_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_handover_scope_open (scope_id, open_flag),
    KEY ix_handover_warehouse_status (warehouse_id, status),
    CONSTRAINT fk_handover_case_scope FOREIGN KEY (scope_id) REFERENCES submission_scope(id),
    CONSTRAINT fk_handover_case_order FOREIGN KEY (canonical_order_id) REFERENCES outbound_order(id),
    CHECK (source_kind IN ('FORMAL')),
    CHECK (to_work_round = from_work_round + 1),
    CHECK (open_flag IS NULL OR open_flag = 1),
    CHECK (row_version > 0),
    CHECK (status IN ('REQUESTED','EVIDENCE_PENDING','REVIEW_READY','APPROVED','TARGET_PREPARED','ACTIVATED',
        'RECONCILIATION_REQUIRED','REJECTED','WITHDRAWN','CLOSED_INCOMPLETE')),
    CHECK (stop_state IN ('REQUESTED','DEVICE_ACKED','FAULT_FENCED')),
    CHECK (integrity_state IN ('COMPLETE','MISSING_RECORDS','MISSING_AUDIT','UNKNOWN_HIGH_WATERMARK')),
    CHECK (original_result_state IN ('NONE_VERIFIED','NONTERMINAL','ABORTED','REJECTED','SUCCEEDED','UNKNOWN')),
    CHECK (physical_state IN ('UNCHECKED','MATCHED','CONFLICT','QUARANTINED')),
    CHECK (physical_state <> 'MATCHED' OR input_high_watermark IS NOT NULL),
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    -- 激活只允许在"已停止 + 证据完整 + 实物一致 + 原结果明确且非 SUCCEEDED"时成立；SUCCEEDED 必须走更正。
    CHECK (status <> 'ACTIVATED' OR (stop_state = 'DEVICE_ACKED' AND integrity_state = 'COMPLETE'
        AND physical_state = 'MATCHED' AND original_result_state IN ('NONE_VERIFIED','ABORTED','REJECTED')
        AND activation_sha256 IS NOT NULL AND input_high_watermark IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE handover_evidence (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    device_id BINARY(16) NOT NULL,
    payload_sha256 BINARY(32) NOT NULL,
    high_watermark BIGINT NULL,
    receipt_bytes MEDIUMBLOB NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_handover_evidence_kind (case_id, kind),
    CONSTRAINT fk_handover_evidence_case FOREIGN KEY (case_id) REFERENCES handover_case(id),
    CHECK (kind IN ('STOP_PROOF','RECOVERY_PACKAGE')),
    CHECK (high_watermark IS NULL OR high_watermark >= 0)
) ENGINE=InnoDB;

CREATE TABLE handover_checklist (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL,
    item_no INT NOT NULL,
    bale_id BINARY(16) NOT NULL,
    delivery_code VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    dispatch_cycle BIGINT NOT NULL,
    verified_by BINARY(16) NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_handover_checklist_item (case_id, item_no),
    UNIQUE KEY uk_handover_checklist_bale (case_id, bale_id),
    CONSTRAINT fk_handover_checklist_case FOREIGN KEY (case_id) REFERENCES handover_case(id),
    CHECK (state IN ('RETAINED','REMOVED')),
    CHECK (item_no >= 0),
    CHECK (dispatch_cycle > 0)
) ENGINE=InnoDB;

-- 原键幂等：同键同文返回原字节，同键异文由服务端拒绝；回执不可改写或删除。
CREATE TABLE handover_command_receipt (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL,
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
    UNIQUE KEY uk_handover_receipt_key (original_key),
    KEY ix_handover_receipt_case (case_id, recorded_at),
    CONSTRAINT fk_handover_receipt_case FOREIGN KEY (case_id) REFERENCES handover_case(id),
    CHECK (command_type IN ('REQUEST','APPROVE','PREPARE','ACTIVATE')),
    CHECK (status IN ('RECORDED')),
    CHECK (JSON_VALID(expected_json))
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER handover_evidence_no_update BEFORE UPDATE ON handover_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover evidence is append-only'; END$$
CREATE TRIGGER handover_evidence_no_delete BEFORE DELETE ON handover_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover evidence is retained'; END$$
CREATE TRIGGER handover_checklist_no_update BEFORE UPDATE ON handover_checklist FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover checklist is fixed once written'; END$$
CREATE TRIGGER handover_checklist_no_delete BEFORE DELETE ON handover_checklist FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover checklist is retained'; END$$
CREATE TRIGGER handover_receipt_no_update BEFORE UPDATE ON handover_command_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover receipt is immutable'; END$$
CREATE TRIGGER handover_receipt_no_delete BEFORE DELETE ON handover_command_receipt FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover receipt is retained'; END$$
CREATE TRIGGER handover_case_no_delete BEFORE DELETE ON handover_case FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover case is retained'; END$$
-- 激活与结案终态不可回退：已激活/已拒绝/已撤回/未完整关闭的案件不得再变回活动状态。
CREATE TRIGGER handover_case_no_reopen BEFORE UPDATE ON handover_case FOR EACH ROW
BEGIN
    IF OLD.status = 'ACTIVATED' AND NEW.status <> 'ACTIVATED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Activated handover case cannot be reopened';
    END IF;
    IF OLD.status IN ('REJECTED','WITHDRAWN','CLOSED_INCOMPLETE')
       AND NEW.status NOT IN ('REJECTED','WITHDRAWN','CLOSED_INCOMPLETE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Closed handover case cannot be reopened';
    END IF;
    IF OLD.activation_receipt IS NOT NULL AND NEW.activation_receipt IS NOT NULL
       AND NOT (OLD.activation_receipt <=> NEW.activation_receipt) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Activation receipt cannot be rewritten';
    END IF;
END$$
DELIMITER ;
