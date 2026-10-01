-- Opt-in correction attachment bytes. Existing correction facts are not changed or backfilled.
-- UTC DATETIME follows this MySQL schema. No cleanup/deletion path is enabled.
CREATE TABLE correction_attachment (
    id BINARY(16) PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    upload_key BINARY(16) NOT NULL,
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes INT NOT NULL,
    content_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE uq_correction_attachment_upload(uploaded_by,warehouse_id,upload_key),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
    CHECK(size_bytes BETWEEN 1 AND 10485760),
    CHECK(size_bytes=OCTET_LENGTH(content_bytes)),
    CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB;

CREATE TABLE correction_attachment_use (
    document_id BINARY(16) NOT NULL,
    line_no INT NOT NULL,
    attachment_id BINARY(16) NOT NULL,
    PRIMARY KEY(document_id,line_no,attachment_id),
    FOREIGN KEY(document_id,line_no) REFERENCES correction_line(document_id,line_no),
    FOREIGN KEY(attachment_id) REFERENCES correction_attachment(id),
    CHECK(line_no BETWEEN 1 AND 200)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER correction_attachment_hash_insert BEFORE INSERT ON correction_attachment FOR EACH ROW
BEGIN
    IF BINARY NEW.content_sha256 <> BINARY LOWER(SHA2(NEW.content_bytes,256)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CORRECTION_ATTACHMENT_HASH_MISMATCH';
    END IF;
END$$
CREATE TRIGGER correction_attachment_immutable_update BEFORE UPDATE ON correction_attachment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CORRECTION_ATTACHMENT_IMMUTABLE';
END$$
CREATE TRIGGER correction_attachment_immutable_delete BEFORE DELETE ON correction_attachment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CORRECTION_ATTACHMENT_IMMUTABLE';
END$$
CREATE TRIGGER correction_attachment_use_immutable_update BEFORE UPDATE ON correction_attachment_use FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CORRECTION_ATTACHMENT_USE_IMMUTABLE';
END$$
CREATE TRIGGER correction_attachment_use_immutable_delete BEFORE DELETE ON correction_attachment_use FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CORRECTION_ATTACHMENT_USE_IMMUTABLE';
END$$
DELIMITER ;

-- Reversal is a separately authorised retention/export operation, never DROP of nonempty evidence tables.
