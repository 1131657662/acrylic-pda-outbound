package com.acrylic.outbound.foundationmigration;

import com.acrylic.outbound.submissions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit acceptance preflight; SQL migration cannot validate private file bytes or signatures. */
public final class LegacyEvidencePreflight {
    private final JdbcTemplate jdbc;
    private final SubmissionArchiveProofs proofs;
    public LegacyEvidencePreflight(JdbcTemplate jdbc, SubmissionArchiveProofs proofs) {
        this.jdbc = jdbc; this.proofs = proofs;
    }

    public void verify() {
        files("SELECT file_key,payload_sha256,byte_count FROM submission_payload", "");
        files("SELECT file_key,sha256,byte_count FROM submission_chunk", "");
        files("SELECT CONCAT(payload_sha256,'.json'),payload_sha256,byte_count FROM recovery_package", "submissions/");
        files("SELECT file_key,file_sha256,NULL FROM inventory_import", "");
        verifyReceipts();
        archive("submission_archive", "confirmation_text", "acrylic-pda-archive");
        archive("pda_audit_archive", "proof_text", "acrylic-pda-audit-archive");
    }

    private void verifyReceipts() {
        for (String raw : jdbc.queryForList("SELECT receipt_text FROM submission WHERE status IN ('SUCCEEDED','ABORTED')", String.class))
            proofs.verify(SubmissionJson.parse(raw), "signedReceipt", "acrylic-pda-receipt", "receipt");
    }

    private void archive(String table, String column, String audience) {
        for (String raw : jdbc.queryForList("SELECT " + LegacyRows.name(column) + " FROM " + LegacyRows.name(table), String.class)) {
            var value = SubmissionJson.parse(raw);
            proofs.verify(value, "signedArchive", audience, "archive");
            checkedFile("submissions/" + value.path("payloadSha256").asText() + ".json", value.path("payloadSha256").asText(), null);
            if (value.has("receiptSha256"))
                checkedFile("submissions/" + value.path("receiptSha256").asText() + ".json", value.path("receiptSha256").asText(), null);
        }
    }

    private void files(String sql, String prefix) {
        jdbc.query(sql, rs -> {
            String key = rs.getString(1);
            if (prefix.equals("submissions/") && !key.endsWith(".json")) key += ".json";
            checkedFile(prefix + key, rs.getString(2), rs.getObject(3) == null ? null : rs.getLong(3));
        });
    }

    private void checkedFile(String key, String expected, Long length) {
        try {
            Path root = FoundationEnvironment.storage().toAbsolutePath().normalize();
            Path path = root.resolve(key).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path)) throw new IllegalStateException("Unexpected private file key");
            byte[] bytes = Files.readAllBytes(path);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected) || (length != null && bytes.length != length))
                throw new IllegalStateException("Legacy file bytes differ: " + key);
        } catch (Exception error) { throw new IllegalStateException("Legacy file preflight failed: " + key, error); }
    }
}
