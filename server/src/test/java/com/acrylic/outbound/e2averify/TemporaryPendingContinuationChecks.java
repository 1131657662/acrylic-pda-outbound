package com.acrylic.outbound.e2averify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Incomplete next-round transport must retain owners, refuse unsupported stop and recover its original bytes. */
final class TemporaryPendingContinuationChecks {
    private final TemporaryRoundConfirmationHttpFixture f;
    TemporaryPendingContinuationChecks(TemporaryRoundConfirmationHttpFixture f) { this.f=f; }
    void resume() throws Exception {
        var h=new TemporaryRoundChunkHttp(f);var stock=f.http.database.stock();
        h.send("/upload",h.manifestBytes(),"linked-pending-start");
        var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
        var request=object().put("submissionId",text(f.body,"submissionId")).put("reason","incomplete source cannot imply complete STOP")
            .put("sourceEvidenceSetId",UUID.randomUUID().toString()).put("verificationId",UUID.randomUUID().toString()).put("setSha256","a".repeat(64));
        var refused=h.send(h.base+"/stop",canonical(request).getBytes(StandardCharsets.UTF_8),
            new TemporaryRoundChunkHttp.Request(f.http.token,"1","linked-pending-stop",409));
        assertThat(parse(refused.body()).path("code").asText()).isEqualTo("TEMP_CONTINUATION_STOP_STATE_CONFLICT");
        h.send(h.base+"/finalize",new byte[0],new TemporaryRoundChunkHttp.Request(f.http.token,"1","linked-pending-incomplete",400));
        assertThat(state.snapshot()).isEqualTo(before);assertThat(f.http.database.stock()).isEqualTo(stock);
        assertThat(parse(f.http.readContinuation(text(f.body,"submissionId"),f.http.token,"linked-pending-read").body())
            .path("data").path("status").asText()).isEqualTo("RECEIVING");
        h.send("/upload",h.manifestBytes(),"linked-pending-original-key");
        for(int i=0;i<h.count();i++)h.send("/chunks/"+i,h.chunk(i),"linked-pending-part-"+i);
        var accepted=parse(h.send("/finalize",new byte[0],"linked-pending-finalize").body()).path("data");
        assertThat(accepted.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(parse(h.send("/finalize",new byte[0],"linked-pending-finalize-replay").body()).path("data")).isEqualTo(accepted);
    }
}
