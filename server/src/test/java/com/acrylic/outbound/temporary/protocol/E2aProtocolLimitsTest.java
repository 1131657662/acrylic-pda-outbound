package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2aProtocolLimitsTest {
    @Test void firstPageCannotClaimAnEarlierPage() {
        var page=page();page.put("prevPageHash","a".repeat(64));

        assertThatThrownBy(() -> E2aProtocol.pageHash(page))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_PREV_LINK");
    }

    @Test void laterPageRequiresPredecessorHash() {
        var page=page();page.put("pageCount",2);page.put("pageNo",2);

        assertThatThrownBy(() -> E2aProtocol.pageHash(page))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_PREV_LINK");
    }

    @Test void pageItemCountMustMatchActualReferences() {
        var page=page();page.put("itemCount",1);

        assertThatThrownBy(() -> E2aProtocol.pageHash(page))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_ITEM_COUNT");
    }

    @Test void pageCountCannotExceedTechnicalLimit() {
        var page=page();page.put("pageCount",4097);page.put("isFinal",false);

        assertThatThrownBy(() -> E2aProtocol.pageHash(page))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("PAGE_COUNT_LIMIT");
    }

    @Test void pageCoreCannotExceedStorageLimit() {
        var page=page();page.put("snapshotId","x".repeat(262145));

        assertThatThrownBy(() -> E2aProtocol.pageHash(page))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("SIZE_INVALID");
    }

    @Test void manifestPageCountCannotExceedTechnicalLimit() {
        var manifest=manifest();manifest.put("pageCount",4097);manifest.put("endPageNo",4097);

        assertThatThrownBy(() -> E2aProtocol.manifestHashes(manifest))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("MANIFEST_PAGE_LIMIT");
    }

    @Test void manifestRawByteTotalCannotExceedSetLimit() {
        var manifest=manifest();manifest.put("byteCount",134217729);

        assertThatThrownBy(() -> E2aProtocol.manifestHashes(manifest))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("MANIFEST_BYTES_LIMIT");
    }

    @Test void manifestCoreCannotExceedStorageLimit() {
        var manifest=manifest();manifest.put("snapshotId","x".repeat(262145));

        assertThatThrownBy(() -> E2aProtocol.manifestHashes(manifest))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("SIZE_INVALID");
    }

    private ObjectNode page() {
        var page=SubmissionJson.object().put("manifestId","m").put("manifestVersion",1).put("snapshotId","s")
            .put("pageNo",1).put("pageCount",1).put("isFinal",true).put("itemCount",0).put("byteCount",0)
            .putNull("prevPageHash");
        page.putArray("items");return page;
    }

    private ObjectNode manifest() {
        var manifest=SubmissionJson.object().put("schemaVersion",1).put("manifestId","m").put("manifestVersion",1)
            .put("snapshotId","s").put("source","TEMP").put("sourceId","source").put("submissionId","submission")
            .put("workRound",1).put("pageCount",1).put("entryCount",0).put("byteCount",0).put("endPageNo",1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo",1).put("pageHash","a".repeat(64)));
        return manifest;
    }
}
