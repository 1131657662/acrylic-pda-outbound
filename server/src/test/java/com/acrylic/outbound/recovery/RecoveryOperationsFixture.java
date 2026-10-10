package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit synthetic independent operations signer; no production key or authority. */
final class RecoveryOperationsFixture {
    final RecoveryClosedAuthority closed;
    final KeyPair key;
    final Path directory,identityFile;
    final ObjectNode identity;

    RecoveryOperationsFixture(RecoveryClosedAuthority closed) throws Exception {
        this.closed=closed;
        directory=Files.createDirectory(closed.location().root().getParent().resolve("operations-"+UUID.randomUUID()));
        identityFile=directory.resolve("identity.json");
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); key=generator.generateKeyPair();
        identity=SubmissionJson.object().put("formatVersion",1).put("authorityKind","RECOVERY_OPERATIONS_APPROVAL")
            .put("installationId",closed.control().installationId().toString()).put("operatorId",UUID.randomUUID().toString())
            .put("publicKeyDer",Base64.getEncoder().encodeToString(key.getPublic().getEncoded()));
        identity.putArray("warehouseIds").add(closed.sourceIdentity().warehouseId().toString());
        publish();
    }
    static RecoveryOperationsFixture create() throws Exception {
        return new RecoveryOperationsFixture(RecoveryClosedTestSupply.of(RecoverySourceFixture.temporary()).authority());
    }
    void publish() throws Exception { Files.write(identityFile,RecoveryControlJson.encode(identity)); }
    RecoveryOperationsAuthority authority() { return new RecoveryOperationsAuthority(closed,identityFile,hash(identity)); }
    byte[] sign(JsonNode approval) throws Exception {
        var signer=Signature.getInstance("SHA256withRSA"); signer.initSign(key.getPrivate()); signer.update(RecoveryControlJson.encode(approval));
        var envelope=SubmissionJson.object().put("formatVersion",1).put("algorithm","RS256")
            .put("keySha256",sha256(key.getPublic().getEncoded()))
            .put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        envelope.set("approval",approval.deepCopy()); return RecoveryControlJson.encode(envelope);
    }
    RecoveryOperationsApproval.Request request(JsonNode approval) throws Exception {
        byte[] bytes=sign(approval); Path file=directory.resolve(UUID.randomUUID()+".json"); Files.write(file,bytes);
        return new RecoveryOperationsApproval.Request(authority(),file,sha256(bytes));
    }
}
