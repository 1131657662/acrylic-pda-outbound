package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.sha256;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;
import static org.assertj.core.api.Assertions.*;

/** Raw and confidential views must remove the same signed row without weakening the raw decoder. */
class RecoveryRecipientSessionRowsTest {
    @ParameterizedTest @CsvSource({"pda_session,access_hash","device_challenge,nonce"})
    void removesOnlySignedRowInConfidentialInventory(String name,String secret) {
        var raw=table(secret); var inventory=raw.deepCopy().put("rowCount","2");
        for(var row:inventory.withArray("rows")) ((com.fasterxml.jackson.databind.node.ArrayNode)row).set(1,
            SubmissionJson.object().put("value","sha256:"+sha256(bytes(row.get(1).asText()))).get("value"));
        var retained=inventory.path("rows").get(1).deepCopy();
        RecoveryRecipientSessionRows.remove(inventory,raw,name,decode(raw).getFirst());
        assertThat(inventory.path("rows").size()).isEqualTo(1); assertThat(inventory.path("rows").get(0)).isEqualTo(retained);
        assertThat(inventory.path("rowCount").asText()).isEqualTo("1");
        assertThatThrownBy(()->decode(table(secret).put("rowCount","2").set("rows",inventory.path("rows"))))
            .hasMessage("RECOVERY_SCOPE_ENCODING");
    }
    @Test void rejectsUnmaskedSecretInInventory() {
        var raw=table("access_hash"); var inventory=raw.deepCopy().put("rowCount","2");
        assertThatThrownBy(()->RecoveryRecipientSessionRows.remove(inventory,raw,"pda_session",decode(raw).getFirst()))
            .hasMessage("RECOVERY_SESSION_EFFECT_PROJECTION");
    }
    @Test void rejectsChangedRawSecretDespiteMatchingInventory() {
        var raw=table("nonce"); var expected=decode(raw).getFirst(); var changed=raw.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode)changed.path("rows").get(0)).set(1,SubmissionJson.object().put("value",encoded("changed")).get("value"));
        assertThatThrownBy(()->RecoveryRecipientSessionRows.remove(raw.deepCopy(),changed,"device_challenge",expected))
            .isInstanceOf(RecoveryControlException.class);
    }
    @Test void removesOneRawRowWithoutMasking() {
        var raw=table("access_hash"); var copy=raw.deepCopy();
        RecoveryRecipientSessionRows.remove(copy,raw,"pda_session",decode(raw).getFirst());
        assertThat(copy.path("rows").size()).isEqualTo(1); assertThat(copy.path("rows").get(0)).isEqualTo(raw.path("rows").get(1));
    }
    @Test void verifiesEntireDigestBeforeRemovingSignedAudit() {
        var raw=table("details"); var actual=raw.deepCopy(); actual.remove("rows");
        actual.put("rowCount","2").put("rowsSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(raw.path("rows"))));
        var bad=actual.deepCopy().put("rowsSha256","a".repeat(64));
        assertThatThrownBy(()->RecoveryRecipientSessionRows.remove(bad,raw,"operation_event",decode(raw).getFirst()))
            .hasMessage("RECOVERY_SESSION_RAW_DIGEST");
        RecoveryRecipientSessionRows.remove(actual,raw,"operation_event",decode(raw).getFirst());
        assertThat(actual.path("rowCount").asText()).isEqualTo("1");
        assertThat(actual.path("rowsSha256").asText()).isEqualTo(sha256(RecoveryTemporaryEvidenceMaterial.encode(encode(raw.path("columns"),List.of(decode(raw).get(1))))));
    }
    private static ObjectNode table(String column) {
        var table=SubmissionJson.object(); table.putArray("columns").add("id").add(column);
        var rows=table.putArray("rows"); rows.addArray().add(encoded("signed")).add(encoded("secret"));
        rows.addArray().add(encoded("original")).add(encoded("untouched")); return table;
    }
}
