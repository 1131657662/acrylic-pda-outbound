package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;

public record DraftEvidenceItemRequest(UUID evidence,UUID key,long ordinal,String kind,UUID record,long sequence,
    String status,int length,String sha,byte[] raw) {
    public DraftEvidenceItemRequest {
        DraftEvidenceWire.require(evidence!=null && key!=null && record!=null && kind!=null && status!=null && raw!=null
            && ordinal>=1 && ordinal<=250000 && sequence>0 && sequence<=9007199254740991L && status.matches("[A-Z_]{1,40}")
            && Set.of("CAPTURE","SCAN","AUDIT","ARCHIVE").contains(kind) && length>0 && length<=33554432 && raw.length==length
            && sha!=null && sha.matches("[0-9a-f]{64}"));
        raw=raw.clone();DraftEvidenceWire.require(DraftEvidenceWire.sha(raw).equals(sha));
    }
    @Override public byte[] raw() { return raw.clone(); }
    public byte[] descriptor() {
        return DraftEvidenceWire.bytes(SubmissionJson.object().put("evidenceId",evidence.toString()).put("ordinal",ordinal).put("kind",kind)
            .put("recordId",record.toString()).put("deviceSeq",sequence).put("recordStatus",status).put("byteCount",length).put("bytesSha256",sha));
    }
}
