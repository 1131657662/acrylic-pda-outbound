package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakePageTest {
    private static final UUID SUBMISSION=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OPEN_KEY=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String OPEN="""
        {"operation":"OPEN","manifestId":"manifest","manifestVersion":2,"snapshotId":"snapshot",
         "source":"TEMP","sourceId":"source","submissionId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","workRound":3}
        """;

    @Test void validPageIsCanonicalAndBoundToOpen() {
        var body=page();

        var parsed=parse(body);

        assertThat(parsed.openOriginalKey()).isEqualTo(OPEN_KEY);
        assertThat(parsed.pageNo()).isEqualTo(1);
        assertThat(parsed.pageCount()).isEqualTo(1);
        assertThat(parsed.prevPageHash()).isNull();
        assertThat(parsed.canonicalBytes()).isEqualTo(E2aProtocol.canonical(bytes(body)));
        assertThat(parsed.pageCoreBytes()).isEqualTo(E2aProtocol.canonical(bytes(body.path("pageCore"))));
    }

    @Test void otherManifestCannotBeAttachedToOpen() {
        var body=page();((ObjectNode)body.path("pageCore")).put("manifestId","other");refreshHash(body);

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_OPEN_CONTEXT");
    }

    @Test void otherSnapshotCannotBeAttachedToOpen() {
        var body=page();((ObjectNode)body.path("pageCore")).put("snapshotId","other");refreshHash(body);

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_OPEN_CONTEXT");
    }

    @Test void otherVersionCannotBeAttachedToOpen() {
        var body=page();((ObjectNode)body.path("pageCore")).put("manifestVersion",3);refreshHash(body);

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_OPEN_CONTEXT");
    }

    @Test void wrongClaimedHashIsRejected() {
        var body=page();body.put("pageHash","0".repeat(64));

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_HASH_MISMATCH");
    }

    @Test void derivedFieldInsideCoreIsRejected() {
        var body=page();((ObjectNode)body.path("pageCore")).put("nextHash","0".repeat(64));

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unknownRequestFieldIsRejected() {
        var body=page();body.put("releaseAllowed",true);

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_REQUEST_FIELDS");
    }

    @Test void nonCanonicalOpenKeyIsRejected() {
        var body=page();body.put("openOriginalKey",OPEN_KEY.toString().toUpperCase());

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_OPEN_KEY");
    }

    @Test void overLimitPageIsRejected() {
        var body=page();((ObjectNode)body.path("pageCore")).put("snapshotId","x".repeat(262145));

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(RuntimeException.class);
    }

    @Test void anotherValidOpenKeyCannotReuseThisOpen() {
        var body=page();body.put("openOriginalKey",UUID.randomUUID().toString());

        assertThatThrownBy(() -> parse(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_OPEN_KEY_MISMATCH");
    }

    private SourceEvidenceIntakePage.Parsed parse(ObjectNode body) {
        return SourceEvidenceIntakePage.parse(bytes(body),OPEN_KEY,open());
    }

    private SourceEvidenceIntakeOpen.Parsed open() {
        return SourceEvidenceIntakeOpen.parse(OPEN.getBytes(StandardCharsets.UTF_8),SUBMISSION,3);
    }

    private ObjectNode page() {
        var core=SubmissionJson.object().put("manifestId","manifest").put("manifestVersion",2)
            .put("snapshotId","snapshot").put("pageNo",1).put("pageCount",1).put("isFinal",true)
            .put("itemCount",0).put("byteCount",0).putNull("prevPageHash");
        core.putArray("items");
        var body=SubmissionJson.object().put("operation","PAGE").put("openOriginalKey",OPEN_KEY.toString());
        body.set("pageCore",core);body.put("pageHash",E2aProtocol.pageHash(core));return body;
    }

    private void refreshHash(ObjectNode body) { body.put("pageHash",E2aProtocol.pageHash((ObjectNode)body.path("pageCore"))); }
    private byte[] bytes(com.fasterxml.jackson.databind.JsonNode node) { return node.toString().getBytes(StandardCharsets.UTF_8); }
}
