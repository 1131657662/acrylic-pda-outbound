package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class RelayIndependentReviewPageTest {
    @Test void pagesKeepOneCompleteContextAndAllCapabilitiesClosed() {
        var full=full();var first=read(full,"candidates","",1);
        var second=read(full,"candidates",text(first,"nextCursor"),1);
        assertThat(text(first,"total")).isEqualTo("2");assertThat(first.path("items").get(0).path("id").asText()).isEqualTo("first");
        assertThat(second.path("items").get(0).path("id").asText()).isEqualTo("hidden");
        assertThat(first.path("contextSha256")).isEqualTo(second.path("contextSha256"));
        for(String field:new String[]{"associationAuthorized","stockAvailable","businessProcessed","releaseAllowed","localDeletionAllowed"})
            assertThat(second.path(field).asBoolean()).isFalse();
    }
    @Test void changingHiddenCandidateRejectsOldCursor() {
        var full=full();String cursor=text(read(full,"candidates","",1),"nextCursor");
        ((ObjectNode)full.path("candidates").path("items").get(1)).put("hasAssignment",true);
        assertThatThrownBy(()->read(full,"candidates",cursor,1)).hasMessageContaining("已变化");
    }
    @Test void changingInventoryRejectsCandidateCursor() {
        var full=full();String cursor=text(read(full,"candidates","",1),"nextCursor");
        full.withArray("inventory").add(object().put("commitmentState","DISPATCHED"));
        assertThatThrownBy(()->read(full,"candidates",cursor,1)).hasMessageContaining("已变化");
    }
    @Test void changingPeerOriginalRejectsInventoryCursor() {
        var full=full();full.withArray("inventory").add(object()).add(object());
        String cursor=text(read(full,"inventory","",1),"nextCursor");full.withArray("peers").add(object().put("taskId","new"));
        assertThatThrownBy(()->read(full,"inventory",cursor,1)).hasMessageContaining("已变化");
    }
    @Test void cursorCannotBeReusedForAnotherSection() {
        var full=full();String cursor=text(read(full,"candidates","",1),"nextCursor");
        assertThatThrownBy(()->read(full,"inventory",cursor,1)).hasMessageContaining("已变化");
    }
    @Test void malformedCursorAndUnboundedPageAreRejected() {
        assertThatThrownBy(()->read(full(),"candidates","%%%%",1)).hasMessageContaining("位置无效");
        assertThatThrownBy(()->read(full(),"candidates","",201)).hasMessageContaining("1至200");
        assertThatThrownBy(()->read(full(),"arbitrary","",1)).hasMessageContaining("类别无效");
    }
    @Test void unverifiedOrIncompleteOriginalCannotBeReconciled() {
        var review=object().put("sourceTrust","TRUSTED").put("authorizationState","VERIFIED").put("completeBoundaryVerified",true);
        RelayIndependentReconciliation.eligible(review);review.put("completeBoundaryVerified",false);
        assertThatThrownBy(()->RelayIndependentReconciliation.eligible(review)).hasMessageContaining("完整结束");
        review.put("authorizationState","UNVERIFIED");
        assertThatThrownBy(()->RelayIndependentReconciliation.eligible(review)).hasMessageContaining("历史授权");
    }
    private ObjectNode full() {
        var full=object();full.putObject("original").put("taskId","fixed");full.putObject("review");full.putObject("summary");
        full.putObject("candidates").putArray("items").add(object().put("id","first")).add(object().put("id","hidden"));
        full.putArray("inventory");full.putArray("peers");return full;
    }
    private ObjectNode read(ObjectNode full,String section,String cursor,int limit) {
        return RelayIndependentReviewPage.read(full,new RelayIndependentReviewPage.Query(section,cursor,limit));
    }
}
