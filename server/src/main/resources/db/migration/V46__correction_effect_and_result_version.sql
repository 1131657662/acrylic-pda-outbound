-- R07-3 步骤 4-5 的存储补充（V46，**纯增量**）
--
-- 背景（设计 vs 实况冲突，已在设计文档 §20 记录）：
-- 1) 设计 §2 假设 `order_result_version` 有 `original_result_version` / `original_submission_id` / `source_correction_id`，
--    且 `order_effective_scan` 存在。实况：该表只有 `submission_id NOT NULL UNIQUE`（V9），且没有 order_effective_scan。
--    后果：更正产生的 v>1 版本**无法**指向新 submission（更正不是 submission），effect 又通过
--    FOREIGN KEY(order_id,result_version) 强制要求版本行先存在 ⇒ 步骤 4 与步骤 5 在结构上不可拆。
-- 2) 因此本迁移按设计原意做**加法**：给既有表补设计点名的两列并放开 submission_id 的 NOT NULL
--    （UNIQUE 允许多个 NULL，故 v>1 行合法），新增 order_effective_scan。
--    不改任何既有列的类型/取值语义，不动 R05/R06/R08 的写入与读取路径（v1 行照旧带 submission_id）。
--
-- 回滚：DROP TABLE order_effective_scan; ALTER TABLE order_result_version DROP FOREIGN KEY ...; DROP COLUMN ...;
--       MODIFY submission_id BINARY(16) NOT NULL（需先无 NULL 行）。

-- 1) 版本表补设计点名的列；v1 行回填 original_submission_id
ALTER TABLE order_result_version
    MODIFY submission_id BINARY(16) NULL,
    ADD original_submission_id BINARY(16) NULL,
    ADD source_correction_id BINARY(16) NULL,
    ADD CONSTRAINT fk_result_original_submission FOREIGN KEY (original_submission_id) REFERENCES submission(id),
    ADD CONSTRAINT fk_result_source_correction FOREIGN KEY (source_correction_id) REFERENCES correction_document(id);

UPDATE order_result_version SET original_submission_id=submission_id WHERE result_version=1;

-- 2b) 更正的"结果版本渠道"：V15 的 ck_result_channel 要求版本必属 PDA 提交或 PC 提交之一；更正版本两者皆无
--     （它由 correction_document 驱动），故放开为三分支。此处只**放宽**既有约束，不收紧任何原先合法的行。
ALTER TABLE order_result_version
    DROP CHECK ck_result_channel,
    ADD CONSTRAINT ck_result_channel CHECK (
        (submission_id IS NOT NULL AND pc_submission_id IS NULL) OR
        (submission_id IS NULL AND pc_submission_id IS NOT NULL) OR
        (submission_id IS NULL AND pc_submission_id IS NULL AND source_correction_id IS NOT NULL));

-- 3) 逐包有效快照：每个版本覆盖全部原 dispatch（含被归零的行），不改原 sequence、不新造扫描
CREATE TABLE order_effective_scan (
    order_id BINARY(16) NOT NULL,
    result_version BIGINT NOT NULL,
    dispatch_id BINARY(16) NOT NULL,
    original_scan_ref BINARY(16) NULL,
    original_sequence BIGINT NOT NULL,
    is_effective BOOLEAN NOT NULL,
    effective_weight_g BIGINT NOT NULL,
    latest_correction_line_id BINARY(16) NULL,
    PRIMARY KEY (order_id,result_version,dispatch_id),
    CONSTRAINT fk_effective_scan_version FOREIGN KEY (order_id,result_version)
        REFERENCES order_result_version(order_id,result_version),
    CONSTRAINT fk_effective_scan_dispatch FOREIGN KEY (dispatch_id) REFERENCES dispatch_record(id),
    CONSTRAINT fk_effective_scan_line FOREIGN KEY (latest_correction_line_id) REFERENCES correction_line(id),
    CHECK (original_sequence > 0),
    CHECK (effective_weight_g >= 0)
) ENGINE=InnoDB;
