SET SESSION lock_wait_timeout = 10;

CREATE TABLE pairing_key_guard (
    key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB;

CREATE TABLE device_pairing (
    id BINARY(16) PRIMARY KEY,
    public_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model VARCHAR(100) NOT NULL,
    short_code CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    recovery_epoch BIGINT NOT NULL,
    approval_json JSON NULL,
    approval_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    approved_by BINARY(16) NULL,
    approved_auth_version BIGINT NULL,
    device_id BINARY(16) NULL,
    live_code CHAR(8) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
        (CASE WHEN status IN ('WAITING','PENDING') THEN short_code ELSE NULL END) STORED,
    live_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
        (CASE WHEN status IN ('WAITING','PENDING') THEN key_sha256 ELSE NULL END) STORED,
    UNIQUE KEY uq_pairing_live_code (live_code),
    UNIQUE KEY uq_pairing_live_key (live_key),
    UNIQUE KEY uq_pairing_device (device_id),
    KEY ix_pairing_expiry (status, expires_at),
    KEY ix_pairing_approver (approved_by),
    CONSTRAINT fk_pairing_approver FOREIGN KEY (approved_by) REFERENCES app_user(id),
    CONSTRAINT fk_pairing_device FOREIGN KEY (device_id) REFERENCES device(id),
    CONSTRAINT ck_pairing_status CHECK (status IN ('WAITING','PENDING','COMPLETED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_pairing_completion CHECK ((status='COMPLETED') = (device_id IS NOT NULL)),
    CONSTRAINT ck_pairing_approval CHECK (status NOT IN ('PENDING','COMPLETED') OR
        (approval_json IS NOT NULL AND approval_sha256 IS NOT NULL AND approved_by IS NOT NULL AND approved_auth_version IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE pairing_challenge (
    id BINARY(16) PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    purpose VARCHAR(10) NOT NULL,
    binding_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nonce VARCHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recovery_epoch BIGINT NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    used_at TIMESTAMP(3) NULL,
    KEY ix_pairing_challenge_expiry (expires_at),
    CONSTRAINT ck_pairing_purpose CHECK (purpose IN ('CREATE','STATUS','CONFIRM','CANCEL'))
) ENGINE=InnoDB;
