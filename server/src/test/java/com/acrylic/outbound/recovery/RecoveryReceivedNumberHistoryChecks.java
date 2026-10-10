package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Reads the retained original global counter stream on a freshly restored and supervisor-fenced target. */
public final class RecoveryReceivedNumberHistoryChecks {
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
        // Independently retained pins from received-checkpoint-consumer-02, not recomputed from whichever file is supplied.
        var begin=new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-snapshot.json"),
            "1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b");
        var end=new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-source-final.json"),
            "3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c");
        var request=new RecoveryReleaseNumberHistory.Request(context.request(),keys,begin,end);
        var proof=RecoveryReleaseNumberHistory.verify(authority,inventory,request);
        assertThat(proof.facts().path("allocatedInWindow").asText()).isEqualTo("1");
        assertThat(proof.releaseAllowed()).isFalse(); assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        refusals(authority,inventory,request);
        RecoveryReceivedNumberDriftChecks.verify(app,context,inventory,request);
        assertThat(context.snapshots().capture()).isEqualTo(before);
        Files.write(attempt.resolve("release-number-history.json"),RecoveryTemporaryEvidenceMaterial.encode(proof.facts()));
        Files.writeString(attempt.resolve("release-number-history.sha256"),proof.sha256());
    }

    private static void refusals(RecoveryClosedAuthority authority,RecoveryReleaseInventory inventory,RecoveryReleaseNumberHistory.Request request) {
        var changed=(ObjectNode)inventory.facts();
        ((ArrayNode)changed.path("tables").path("order_number_counter").path("rows").get(0)).set(1,
            com.fasterxml.jackson.databind.node.TextNode.valueOf("Mg=="));
        var wrong=new RecoveryReleaseInventory(changed,inventory.gaps());
        assertThatThrownBy(()->RecoveryReleaseNumberHistory.verify(authority,wrong,request)).hasMessage("RECOVERY_NUMBER_HISTORY_TARGET_MISMATCH");
        var badPin=new RecoveryReleaseNumberHistory.Snapshot(request.begin().file(),"f".repeat(64));
        var bad=new RecoveryReleaseNumberHistory.Request(request.archive(),request.keys(),badPin,request.end());
        assertThatThrownBy(()->RecoveryReleaseNumberHistory.verify(authority,inventory,bad)).hasMessage("RECOVERY_NUMBER_SNAPSHOT_PIN_MISMATCH");
        var reversed=new RecoveryReleaseNumberHistory.Request(request.archive(),request.keys(),request.end(),request.begin());
        assertThatThrownBy(()->RecoveryReleaseNumberHistory.verify(authority,inventory,reversed)).hasMessage("RECOVERY_NUMBER_TARGET_ORDERS_CHANGED");
    }
}
