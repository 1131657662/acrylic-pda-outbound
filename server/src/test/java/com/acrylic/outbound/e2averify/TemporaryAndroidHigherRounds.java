package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.temporary.command.PhysicalDispositionVerification;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic device identity plus actual Android-created E2A/STOP; physical RETAIN remains an explicit fixture. */
final class TemporaryAndroidHigherRounds {
    private TemporaryAndroidHigherRounds() { }
    static void device(ContinuationReceiptHttpFixture http,ObjectNode input) throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));var keys=generator.generateKeyPair();
        String publicKey=Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        http.database.jdbc.update("UPDATE device SET public_key=?,key_sha256=? WHERE id=UUID_TO_BIN(?)",
            publicKey,DeviceCrypto.sha(publicKey),http.database.seed.get("deviceId"));
        input.put("devicePublicKey",publicKey).put("devicePrivateKey",Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()));
        // This object is written only to the provisioned private/ directory, never to a tracked path or log.
    }

    static byte[] advance(ContinuationReceiptHttpFixture http) throws Exception {
        var db=http.database;Path attempt=db.profile.attempt;
        TemporaryAndroidContinuationHarness.await(attempt.resolve("android-stopped.json"));
        var stop=parse(Files.readString(attempt.resolve("android-stopped.json")));UUID id=uuid(stop,"submissionId");
        assertThat(db.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,id.toString())).isEqualTo("ABORTED");
        var hash=db.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",String.class,id.toString());
        var body=parse(http.app.getBean(SubmissionFiles.class).read(hash));UUID set=uuid(stop,"sourceEvidenceSetId");
        db.tx.executeWithoutResult(status->{
            var proof=new PhysicalDispositionVerification(db.jdbc).readForContinuation(db.id("warehouseId"),db.id("mappedScopeId"),
                id,number(body,"workRound"),set,uuid(stop,"verificationId"),text(stop,"setSha256"),1,1);
            db.apply(proof,Collections.nCopies(proof.activeBales().size(),"RETAIN"));
        });
        var commands=new ContinuationReceiptHttpCommands(http);long next=number(body,"captureHighWatermark")+1;
        var issued=parse(http.accepted(commands.request(null,id,set,next),"android-higher-ticket"));
        byte[] activated=http.accepted(commands.request(uuid(issued.path("businessDecision"),"ticketId"),id,set,next),"android-higher-activation");
        Files.write(attempt.resolve("android-next-activation.json"),activated,StandardOpenOption.CREATE_NEW);
        return activated;
    }
}
