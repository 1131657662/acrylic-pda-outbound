SET SESSION lock_wait_timeout=10;

-- Opt-in fault attachments. Existing cases, receipts and archive bytes are unchanged.
-- MySQL UTC DATETIME and binary UUIDs follow this repository's storage convention.
ALTER TABLE handover_case ADD UNIQUE KEY uq_handover_case_warehouse(id,warehouse_id);

CREATE TABLE handover_attachment (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    upload_key BINARY(16) NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes INT NOT NULL,
    content_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_handover_attachment_upload(case_id,warehouse_id,uploaded_by,upload_key),
    UNIQUE KEY uq_handover_attachment_owner(id,case_id,warehouse_id,uploaded_by),
    FOREIGN KEY(case_id,warehouse_id) REFERENCES handover_case(id,warehouse_id),
    FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
    CHECK(size_bytes BETWEEN 1 AND 10485760),
    CHECK(size_bytes=OCTET_LENGTH(content_bytes)),
    CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB;

-- The reservation already exists when the operation executes; the signed receipt follows in the same transaction.
CREATE TABLE handover_attachment_use (
    request_id BINARY(16) NOT NULL,
    ordinal TINYINT NOT NULL,
    attachment_id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    PRIMARY KEY(request_id,ordinal),
    UNIQUE KEY uq_handover_attachment_use(request_id,attachment_id),
    FOREIGN KEY(request_id) REFERENCES api_idempotency_request(id),
    FOREIGN KEY(attachment_id,case_id,warehouse_id,uploaded_by)
        REFERENCES handover_attachment(id,case_id,warehouse_id,uploaded_by),
    CHECK(ordinal BETWEEN 1 AND 5)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER handover_attachment_hash_insert BEFORE INSERT ON handover_attachment FOR EACH ROW
BEGIN
    IF BINARY NEW.content_sha256 <> BINARY LOWER(SHA2(NEW.content_bytes,256)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='HANDOVER_ATTACHMENT_HASH_MISMATCH';
    END IF;
END$$
CREATE TRIGGER handover_attachment_no_update BEFORE UPDATE ON handover_attachment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover attachment is immutable'; END$$
CREATE TRIGGER handover_attachment_no_delete BEFORE DELETE ON handover_attachment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover attachment is retained'; END$$
CREATE TRIGGER handover_attachment_use_insert BEFORE INSERT ON handover_attachment_use FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM api_idempotency_request q WHERE q.id=NEW.request_id
        AND q.principal_id=NEW.uploaded_by AND q.route_scope='r07.handover.v1') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover attachment requires original command reservation';
    END IF;
END$$
CREATE TRIGGER handover_attachment_use_no_update BEFORE UPDATE ON handover_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover attachment use is immutable'; END$$
CREATE TRIGGER handover_attachment_use_no_delete BEFORE DELETE ON handover_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover attachment use is retained'; END$$
DELIMITER ;

-- No destructive downgrade or evidence cleanup is introduced.
