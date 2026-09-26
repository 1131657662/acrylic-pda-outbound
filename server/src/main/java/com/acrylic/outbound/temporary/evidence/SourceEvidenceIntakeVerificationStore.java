package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeVerificationStore {
    private static final String INSERT = """
        INSERT INTO source_evidence_verification
        (id,set_id,scope_id,submission_id,work_round,verification_version,algorithm_version,set_sha256,
         result,known_facts_sha256,findings_bytes,verified_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeVerificationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID insert(SourceEvidenceIntakePageObjects.Binding binding, UUID setId, String setHash,
        long verificationVersion, long algorithmVersion, String result, byte[] knownFacts,
        byte[] findings, Instant verifiedAt) {
        if (binding == null || setId == null || setHash == null || knownFacts == null || findings == null
            || verifiedAt == null || verificationVersion < 1 || algorithmVersion < 1)
            throw new IllegalArgumentException("VERIFY_INSERT_INPUT");
        var id = Ids.next();
        try {
            if (jdbc.update(INSERT, Ids.bytes(id), Ids.bytes(setId), Ids.bytes(binding.scopeId()),
                Ids.bytes(binding.submissionId()), binding.workRound(), verificationVersion, algorithmVersion, setHash,
                result, ImportFiles.sha256(knownFacts), findings, Timestamp.from(verifiedAt)) != 1)
                throw new IllegalStateException("VERIFY_INSERT_COUNT");
            return id;
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "E2A_VERIFY_VERSION_CONFLICT", "验证版本已存在且不可覆盖");
        }
    }
}
