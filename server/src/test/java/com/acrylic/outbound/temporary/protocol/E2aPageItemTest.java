package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2aPageItemTest {
    private static final String RECORD="11111111-1111-4111-8111-111111111111";
    private static final String OBJECT="22222222-2222-4222-8222-222222222222";

    @Test void acceptsOneBoundRawItemAndExactPageTotal() {
        var core=page();core.put("itemCount",1).put("byteCount",4);
        core.withArray("items").add(item());

        var parsed=E2aPageItem.parseAll(core.path("items"),4);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().originalRecordId()).isEqualTo(UUID.fromString(RECORD));
        assertThat(parsed.getFirst().byteCount()).isEqualTo(4);
        assertThat(E2aProtocol.pageHash(core)).matches("[0-9a-f]{64}");
    }

    @Test void rejectsWrongTotalAndDuplicateRecord() {
        var core=page();core.put("itemCount",2).put("byteCount",8);
        core.withArray("items").add(item()).add(item().put("originalObjectId",UUID.randomUUID().toString()));

        assertThatThrownBy(() -> E2aProtocol.pageHash(core)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PAGE_DUPLICATE_RECORD");
        core.withArray("items").remove(1);core.put("itemCount",1);core.put("byteCount",3);
        assertThatThrownBy(() -> E2aProtocol.pageHash(core)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PAGE_BYTES_MISMATCH");
    }

    @Test void rejectsDuplicateObjectWithDifferentRecord() {
        var core=page();core.put("itemCount",2).put("byteCount",8);
        core.withArray("items").add(item()).add(item().put("originalRecordId",UUID.randomUUID().toString()));

        assertThatThrownBy(() -> E2aProtocol.pageHash(core)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PAGE_DUPLICATE_OBJECT");
    }

    @Test void rejectsExtraClaimAndNoncanonicalObjectIdentity() {
        var extraField=item().put("releaseAllowed",true);
        assertThatThrownBy(() -> E2aPageItem.parse(extraField)).hasMessage("PAGE_ITEM_FIELDS");

        var noncanonical=item().put("originalObjectId","AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");
        assertThatThrownBy(() -> E2aPageItem.parse(noncanonical)).hasMessage("PAGE_ITEM_UUID");
    }

    @Test void scanRequiresCompleteBaleCycleAndCaptureWhileOtherKindsForbidThem() {
        var scan=item().put("itemKind","SCAN");
        assertThatThrownBy(() -> E2aPageItem.parse(scan)).hasMessage("PAGE_SCAN_FIELDS");

        scan.put("baleId",UUID.randomUUID().toString()).put("originalCycle",2)
            .put("captureId",UUID.randomUUID().toString());
        assertThat(E2aPageItem.parse(scan).originalCycle()).isEqualTo(2);
        scan.put("itemKind","AUDIT");
        assertThatThrownBy(() -> E2aPageItem.parse(scan)).hasMessage("PAGE_NONSCAN_FIELDS");
    }

    @Test void rejectsOverLimitRawTotalAndUntrustedMalformedClaims() {
        var core=page();core.put("itemCount",5).put("byteCount",167772160);
        for(int index=0;index<5;index++)core.withArray("items").add(item()
            .put("originalRecordId",UUID.randomUUID().toString())
            .put("originalObjectId",UUID.randomUUID().toString()).put("byteCount",33554432));
        assertThatThrownBy(() -> E2aProtocol.pageHash(core)).hasMessage("PAGE_BYTES_LIMIT");

        assertThatThrownBy(() -> E2aPageItem.parse(item().put("recordStatus","\n"))).hasMessage("PAGE_ITEM_TEXT");
        assertThatThrownBy(() -> E2aPageItem.parse(item().put("itemBytesSha256","A".repeat(64))))
            .hasMessage("PAGE_ITEM_HASH");
        assertThatThrownBy(() -> E2aPageItem.parse(item().put("originalDeviceSeq",1.5)))
            .hasMessage("PAGE_ITEM_NUMBER");
    }

    private ObjectNode item() {
        return SubmissionJson.object().put("itemKind","CAPTURE").put("originalRecordId",RECORD)
            .put("objectKind","CAPTURE_RECORDS").put("originalObjectId",OBJECT)
            .put("itemBytesSha256","a".repeat(64)).put("byteCount",4).put("originalDeviceSeq",1)
            .put("recordSha256","b".repeat(64)).put("recordStatus","CAPTURED")
            .putNull("baleId").putNull("originalCycle").putNull("captureId");
    }

    private ObjectNode page() {
        var core=SubmissionJson.object().put("manifestId","manifest").put("manifestVersion",1)
            .put("snapshotId","snapshot").put("pageNo",1).put("pageCount",1)
            .put("isFinal",true).put("itemCount",0).put("byteCount",0).putNull("prevPageHash");
        core.putArray("items");return core;
    }
}
