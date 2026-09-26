package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/** Exports only synthetic public contract material, never session tokens or signing private keys. */
final class E2aCrossSourceExport {
    private E2aCrossSourceExport() { }

    static void write(E2aCrossSourceWire wire, Path attempt) throws Exception {
        var f = wire.fixture;
        var export = SubmissionJson.object().put("fixturePurpose", "SYNTHETIC_ISOLATED_MYSQL_REAL_HTTP")
            .put("warehouseId", f.seed.get("warehouseId")).put("submissionId", f.submission.toString())
            .put("authorizationId", f.seed.get("authorizationId")).put("clientOrderId", f.client.toString())
            .put("originalOperatorId", f.seed.get("operatorId")).put("freezeDeviceId", f.seed.get("deviceId"))
            .put("sourceRecoveryEpoch", 1).put("manifestId", wire.manifest).put("snapshotId", wire.snapshot)
            .put("openOriginalKey", wire.openKey.toString()).put("stopOriginalKey", wire.stopKey.toString())
            .put("manifestBytesHash", wire.hashes.manifestBytesHash()).put("setHash", wire.hashes.setHash());
        export.set("manifestCore", wire.manifestCore);
        String publicKey = Base64.getEncoder().encodeToString(Files.readAllBytes(attempt.resolve("private/server-public.der")));
        export.putObject("signingKey").put("algorithm", "RS256").put("publicKey", publicKey)
            .put("keyId", "offline-" + ImportFiles.sha256(publicKey.getBytes(StandardCharsets.UTF_8)).substring(0, 32));
        var entries = export.putArray("entries");
        for (var row : f.jdbc.queryForList("""
            SELECT operation_kind,canonical_request_bytes,original_response_bytes FROM source_evidence_intake_receipt
            WHERE submission_id=? ORDER BY FIELD(operation_kind,'OPEN','PAGE','STOP','VERIFY')
            """, Ids.bytes(f.submission))) {
            entries.addObject().put("operation", (String)row.get("operation_kind"))
                .put("requestBase64", Base64.getEncoder().encodeToString((byte[])row.get("canonical_request_bytes")))
                .put("responseBase64", Base64.getEncoder().encodeToString((byte[])row.get("original_response_bytes")));
        }
        if (entries.size() != 4) throw new IllegalStateException("Expected four distinct real HTTP receipt kinds");
        Files.write(attempt.resolve("intake-http-vector.json"), E2aCrossSourceFixture.bytes(export), StandardOpenOption.CREATE_NEW);
    }
}
