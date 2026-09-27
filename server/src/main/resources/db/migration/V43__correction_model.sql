-- R07-3 已出库更正模型（步骤 1：Schema）
--
-- 【语义变更声明】本迁移把 order_result_version 的两条 CHECK 由 >0 放宽为 >=0。
-- 这不是"简单放宽"，而是语义变更：原来要求每个结果版本必须有实际内容；现在允许"空结果"，
-- 以支持【全单退货后的净结果 0 包/0g】合法（见 docs/parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md §4/§10）。
-- 既有依赖已核对：写入方 PcAccounting / SubmissionAccounting 在 v1 恒写入 >0；读取方
-- （ExportFacts / ExportRangeReader / CommandBusinessIntegrity / TemporaryDuplicateQueries /
--  TemporaryLinkTarget / OrderTemporaryCandidateQueries）只按 result_version=1 过滤或仅判存在，
-- 无一依赖该 CHECK 的严格性，故放宽不改变 v1 行为。
--
-- 【为何不动 inventory_ledger】它是"原政策事实"表：dispatch_id UNIQUE（一个 dispatch 一条原始扣减）
-- 与 CHECK(quantity_delta=-1) / CHECK(weight_delta_g<0) 是 R05 已验证的账务不变量。更正账务需要
-- 多条流水（原扣 + 重量差额 + 完整反向，且反向为正值），因此改用并列的 correction_ledger，
-- 而不是破坏已验证面（设计 §10 路线 A）。
--
-- 既有可用对象：order_effective_line(order_id,result_version,delivery_code,actual_count,weight_g) 直接复用。

-- 1) 放宽 order_result_version 的两条 CHECK（约束名由 MySQL 自动生成，按 CHECK 子句动态定位）
SET @count_check = (SELECT cc.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
      AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'order_result_version'
      AND cc.CHECK_CLAUSE LIKE '%actual_count%' LIMIT 1);
SET @stmt = IF(@count_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE order_result_version DROP CHECK `', @count_check, '`'));
PREPARE drop_count FROM @stmt; EXECUTE drop_count; DEALLOCATE PREPARE drop_count;

SET @weight_check = (SELECT cc.CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
      AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'order_result_version'
      AND cc.CHECK_CLAUSE LIKE '%weight_g%' LIMIT 1);
SET @stmt = IF(@weight_check IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE order_result_version DROP CHECK `', @weight_check, '`'));
PREPARE drop_weight FROM @stmt; EXECUTE drop_weight; DEALLOCATE PREPARE drop_weight;

ALTER TABLE order_result_version
    ADD CONSTRAINT ck_result_version_count CHECK(actual_count >= 0),
    ADD CONSTRAINT ck_result_version_weight CHECK(weight_g >= 0);

-- 2) 当前有效结果指针（新增表而非改 outbound_order，避免触碰既有表）
CREATE TABLE order_current_result (
    order_id BINARY(16) PRIMARY KEY,
    result_version BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    FOREIGN KEY(order_id,result_version) REFERENCES order_result_version(order_id,result_version),
    CHECK(result_version >= 1)
) ENGINE=InnoDB;

-- 3) 更正文档：提交后正文固定（document_sha256 绑定），每单一份凭据原子生效
CREATE TABLE correction_document (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
    type VARCHAR(32) NOT NULL, expected_result_version BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
    document_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL, source_recovery_epoch BIGINT NOT NULL, row_version BIGINT NOT NULL DEFAULT 1,
    requested_by BINARY(16) NOT NULL, requested_at DATETIME(3) NOT NULL,
    approved_by BINARY(16) NULL, approved_at DATETIME(3) NULL,
    effective_result_version BIGINT NULL,
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
    UNIQUE uq_correction_document(order_id,document_sha256),
    CHECK(type IN ('PHYSICAL_RETURN','DISPATCH_CANCELLATION','WEIGHT_CORRECTION','HEADER_CORRECTION')),
    CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','EFFECTIVE','REJECTED','WITHDRAWN'))
) ENGINE=InnoDB;
CREATE INDEX ix_correction_document_order ON correction_document(order_id,status);

-- 4) 更正明细行：目标值由服务端相对当前版本重算；HEADER 行无 dispatch
CREATE TABLE correction_line (
    id BINARY(16) PRIMARY KEY, document_id BINARY(16) NOT NULL, line_no INT NOT NULL,
    dispatch_id BINARY(16) NULL, bale_id BINARY(16) NULL, delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL,
    before_count BIGINT NOT NULL, before_weight_g BIGINT NOT NULL,
    target_count BIGINT NOT NULL, target_weight_g BIGINT NOT NULL,
    evidence_id BINARY(16) NULL, evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    business_occurred_at DATETIME(3) NOT NULL,
    FOREIGN KEY(document_id) REFERENCES correction_document(id),
    FOREIGN KEY(dispatch_id) REFERENCES dispatch_record(id),
    UNIQUE uq_correction_line_no(document_id,line_no),
    UNIQUE uq_correction_line_dispatch(document_id,dispatch_id),
    CHECK(target_count >= 0), CHECK(target_weight_g >= 0)
) ENGINE=InnoDB;

-- 5) 结果差额：每明细行恰好一条（汇总只求 effects，不 JOIN 账务放大）
CREATE TABLE correction_effect (
    id BINARY(16) PRIMARY KEY, line_id BINARY(16) NOT NULL UNIQUE, order_id BINARY(16) NOT NULL,
    result_version BIGINT NOT NULL, dispatch_id BINARY(16) NULL, action VARCHAR(24) NOT NULL,
    count_delta BIGINT NOT NULL, weight_delta_g BIGINT NOT NULL,
    cycle_before INT NULL, cycle_after INT NULL,
    business_occurred_at DATETIME(3) NOT NULL, effective_at DATETIME(3) NOT NULL,
    FOREIGN KEY(line_id) REFERENCES correction_line(id), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
    FOREIGN KEY(order_id,result_version) REFERENCES order_result_version(order_id,result_version),
    CHECK(action IN ('RETURN','CANCEL_DISPATCH','WEIGHT','HEADER'))
) ENGINE=InnoDB;

-- 6) 更正账务（并列于 inventory_ledger，避免改动 R05 已验证不变量）
CREATE TABLE correction_ledger (
    id BINARY(16) PRIMARY KEY, dispatch_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    correction_line_id BINARY(16) NULL, entry_kind VARCHAR(24) NOT NULL,
    quantity_delta INT NOT NULL, weight_delta_g BIGINT NOT NULL,
    reverses_entry_id BINARY(16) NULL UNIQUE, business_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    created_at DATETIME(3) NOT NULL,
    FOREIGN KEY(dispatch_id) REFERENCES dispatch_record(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    FOREIGN KEY(correction_line_id) REFERENCES correction_line(id),
    FOREIGN KEY(reverses_entry_id) REFERENCES correction_ledger(id),
    CHECK(entry_kind IN ('REVERSAL','WEIGHT_DIFF','MANUAL_DEDUCT'))
) ENGINE=InnoDB;
CREATE INDEX ix_correction_ledger_dispatch ON correction_ledger(dispatch_id,created_at);

-- 7) 周期重开：原 dispatch 最多一次；可用性独立表达（重开 != 可用）
CREATE TABLE cycle_reopening (
    id BINARY(16) PRIMARY KEY, original_dispatch_id BINARY(16) NOT NULL UNIQUE,
    correction_line_id BINARY(16) NOT NULL, cycle_before INT NOT NULL, cycle_after INT NOT NULL,
    evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_version BIGINT NOT NULL, issued_recovery_epoch BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
    FOREIGN KEY(original_dispatch_id) REFERENCES dispatch_record(id),
    FOREIGN KEY(correction_line_id) REFERENCES correction_line(id),
    CHECK(cycle_after = cycle_before + 1)
) ENGINE=InnoDB;

-- 8) 库存差异案件（处理结论不等于该包可用）
CREATE TABLE inventory_reconciliation (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, kind VARCHAR(32) NOT NULL,
    bale_id BINARY(16) NULL, dispatch_id BINARY(16) NULL, cycle INT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN', expected_result_version BIGINT NULL,
    expected_ledger_version BIGINT NULL, expected_stock_state_version BIGINT NULL,
    conclusion VARCHAR(500) NULL, source_correction_id BINARY(16) NULL,
    command_receipt_id BINARY(16) NULL, row_version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(status IN ('OPEN','EVIDENCE_PENDING','REVIEW_READY','RESOLVED','BLOCKED'))
) ENGINE=InnoDB;
CREATE INDEX ix_reconciliation_status ON inventory_reconciliation(warehouse_id,status,id);
