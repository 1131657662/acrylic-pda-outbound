package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Current consumer of restored pre-exclusion signed receipts; never creates replacement historical proof. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="P04_LEGACY_RECEIPTS",matches=".+")
class TemporaryExclusionLegacyReceiptIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CommandStore store;
    @Autowired CommandReceiptIntegrity integrity;

    @Test void restoredLegacyLinkOriginalsRemainReadableWithoutSelectionOrNewWrites() throws Exception {
        var root=Path.of(System.getenv("P04_LEGACY_RECEIPTS")).toAbsolutePath().normalize();
        assertThat(root.toString().replace('\\','/')).startsWith("D:/acrylic-pda-outbound/.local/goal-20261012/");
        var run=System.getenv("P04_LEGACY_RUN");assertThat(run).matches("139[5-9]");
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("acrylic_e2a_cross_20260925_"+run);
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19331);
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) id FROM temporary_command_receipt r
            JOIN api_idempotency_request q ON q.id=r.api_request_id
            WHERE q.route_scope=? ORDER BY r.recorded_at,r.id
            """,com.acrylic.outbound.temporary.protocol.TemporaryProtocol.ROUTE_SCOPE);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
        var before=facts();
        for(var row:rows)verify(UUID.fromString(row.get("id").toString()));
        assertThat(facts()).isEqualTo(before);
        Files.writeString(root.resolve("legacy-reader-result.json"),SubmissionJson.canonical(
            SubmissionJson.object().put("receipts",rows.size()).put("originalsUnchanged",true)
                .put("currentIntegrityConsumer",true).put("syntheticHistoricalWarehouse",true)
                .put("localDeletionAllowed",false)));
    }

    private void verify(UUID id) {
        var record=store.byReceipt(id);var bytes=record.bytes("original_response_bytes").clone();
        var unsigned=integrity.verify(record);
        assertThat(unsigned.path("commandType").asText()).isEqualTo("LINK");
        assertThat(unsigned.path("businessDecision").has("selection")).isFalse();
        assertThat(store.byReceipt(id).bytes("original_response_bytes")).containsExactly(bytes);
        assertThat(store.audits(id)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_decision_event WHERE receipt_id=?",
            Integer.class,Ids.bytes(id))).isEqualTo(1);
    }

    private Object facts() {
        return jdbc.queryForList("""
            SELECT HEX(id) id,HEX(canonical_command_bytes) command,HEX(original_response_bytes) response,
             command_sha256,original_response_sha256,unsigned_receipt_sha256,signed_receipt
            FROM temporary_command_receipt ORDER BY id
            """);
    }
}
