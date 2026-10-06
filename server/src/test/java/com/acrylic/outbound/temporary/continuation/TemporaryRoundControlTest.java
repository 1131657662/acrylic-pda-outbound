package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionControl;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class TemporaryRoundControlTest {
    @Test void preservesExplicitNewIdentityWithoutBorrowingFormalAssignment() {
        var body=body();var parsed=TemporaryRoundControl.read(body);
        assertThat(parsed.control().source()).isEqualTo(body.get("source"));
        assertThat(parsed.control().round()).isEqualTo(2);
        assertThat(parsed.control().assignment()).isNull();
        assertThat(parsed.control().continuation()).isNull();
        assertThat(parsed.predecessor().payloadSha256()).isEqualTo("a".repeat(64));
        assertThat(parsed.predecessor().submission()).isNotEqualTo(parsed.control().id());
    }
    @Test void ordinaryIntakeParserStillRejectsNextRound() {
        var body=body();
        assertThatThrownBy(()->SubmissionControl.read(uuid(body,"submissionId"),body)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @MethodSource("invalid")
    void rejectsIncompleteOrAmbiguousControl(Consumer<ObjectNode> mutate) {
        var body=body();mutate.accept(body);
        assertThatThrownBy(()->TemporaryRoundControl.read(body)).isInstanceOf(BusinessException.class);
    }
    static Stream<Consumer<ObjectNode>> invalid() {
        return Stream.of(b->b.put("schemaVersion",3),b->b.put("workRound","1"),b->b.put("workRound",2),
            b->b.put("sourceRecoveryEpoch","01"),b->b.put("assignmentId",UUID.randomUUID().toString()),
            b->b.put("continuationId",UUID.randomUUID().toString()),b->b.remove("continuationId"),
            b->b.put("freezeDeviceId",UUID.randomUUID().toString()),b->b.remove("temporaryContinuation"),
            b->reference(b).put("version","1"),b->reference(b).put("extra",true),
            b->reference(b).put("predecessorPayloadSha256","A".repeat(64)),
            b->reference(b).put("predecessorSubmissionId",b.path("submissionId").asText()),
            b->reference(b).put("ticketId","1-1-1-1-1"),b->((ObjectNode)b.get("source")).put("scopeId",UUID.randomUUID().toString()));
    }
    private static ObjectNode reference(ObjectNode body) { return (ObjectNode)body.get("temporaryContinuation"); }
    static ObjectNode body() {
        String device=UUID.randomUUID().toString();
        var body=object().put("schemaVersion",2).put("submissionId",UUID.randomUUID().toString())
            .put("authorizationId",UUID.randomUUID().toString()).put("freezeDeviceId",device)
            .put("workRound","2").put("sourceRecoveryEpoch","1").putNull("assignmentId").putNull("continuationId");
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",device).put("clientOrderId",UUID.randomUUID().toString());
        body.putObject("temporaryContinuation").put("version",1).put("ticketId",UUID.randomUUID().toString())
            .put("predecessorSubmissionId",UUID.randomUUID().toString()).put("predecessorPayloadSha256","a".repeat(64))
            .put("retainedManifestSha256","b".repeat(64));
        return body;
    }
}
