package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual retained original history; target operations authority and supervisors remain synthetic. */
public final class RecoveryReceivedHistoryCoverageChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var context=RecoveryReceivedArchiveSetup.createWithHistoryWindow(app.getBean(JdbcTemplate.class),attempt,original);
        var authority=RecoveryClosedTestSupply.of(context.source()).authority();
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var binding=new HandoverRecoveredTemporaryBinding(context.consumer(),context.request(),context.nativeFacts());
        var foreign=new RecoveryReceivedTemporaryArchiveConsumer(new DelegatingDataSource(context.jdbc().getDataSource()),authority,keys);
        var inventory=HandoverRecoveredReviewChecks.verify(app,attempt,new HandoverRecoveredTemporaryChecks.Bindings(binding,
            new HandoverRecoveredTemporaryBinding(foreign,context.request(),context.nativeFacts())));
        var before=context.snapshots().capture();
        var proof=RecoveryReleaseHistoryCoverage.verify(authority,inventory,context.request(),keys);
        assertThat(proof.facts().path("historicalAuthorityEstablished").asBoolean()).isTrue();
        assertThat(proof.facts().path("possibleLossFrom")).isEqualTo(proof.facts().path("historyFrom"));
        assertThat(proof.facts().path("possibleLossThrough")).isEqualTo(proof.facts().path("historyThrough"));
        assertThat(proof.releaseAllowed()).isFalse(); assertThat(proof.ordinaryWritesAllowed()).isFalse();
        assertThat(proof.localDeletionAllowed()).isFalse();
        refusals(context,authority,inventory,keys);
        assertThat(context.snapshots().capture()).isEqualTo(before);
        Files.write(attempt.resolve("release-history-coverage.json"),RecoveryTemporaryEvidenceMaterial.encode(proof.facts()));
        Files.writeString(attempt.resolve("release-history-coverage.sha256"),proof.sha256());
    }

    private static void refusals(RecoveryReceivedArchiveSetup.Context context,RecoveryClosedAuthority authority,
            RecoveryReleaseInventory inventory,RecoveryReceiptKeys keys) {
        var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)inventory.facts();
        ((com.fasterxml.jackson.databind.node.ObjectNode)wrong.path("control")).put("epochId",UUID.randomUUID().toString());
        var wrongControl=new RecoveryReleaseInventory(wrong,inventory.gaps());
        assertThatThrownBy(()->RecoveryReleaseHistoryCoverage.verify(authority,wrongControl,context.request(),keys))
            .hasMessage("RECOVERY_RELEASE_HISTORY_CONTROL");
        wrong=(com.fasterxml.jackson.databind.node.ObjectNode)inventory.facts(); wrong.put("sourceRowsSha256","f".repeat(64));
        var mismatched=new RecoveryReleaseInventory(wrong,inventory.gaps());
        assertThatThrownBy(()->RecoveryReleaseHistoryCoverage.verify(authority,mismatched,context.request(),keys))
            .hasMessage("RECOVERY_RELEASE_HISTORY_SOURCE");
    }
}
