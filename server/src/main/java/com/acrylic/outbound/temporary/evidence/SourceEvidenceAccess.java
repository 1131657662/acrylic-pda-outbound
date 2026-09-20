package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionStore;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceIdentity.*;

@Component
public class SourceEvidenceAccess {
    private final Access access;private final SubmissionStore submissions;private final SourceEvidenceQueries queries;
    public SourceEvidenceAccess(Access access,SubmissionStore submissions,SourceEvidenceQueries queries) {
        this.access=access;this.submissions=submissions;this.queries=queries;
    }
    Context read(Selection selection) {
        var identity=selection.identity();var actor=access.require("temporary_order:confirm",identity.warehouseId());
        long round=identity.round();var h=submissions.find(selection.submissionId());
        if(h==null || !h.warehouse().equals(identity.warehouseId()) || h.control().temporary()==null)throw missing();
        var original=h.control().temporary();
        if(!original.creator().equals(identity.creatorDeviceId()) || !original.client().equals(identity.clientOrderId())
            || h.control().round()!=round)throw missing();
        var metadata=queries.metadata(h);
        return new Context(h,metadata,Long.toString(actor.authVersion()));
    }
    static BusinessException missing() { return new BusinessException(404,"SOURCE_EVIDENCE_NOT_FOUND","原来源证据不存在或无权访问；此结果不能证明现场无作业"); }
    static BusinessException corrupt() { return new BusinessException(409,"SOURCE_EVIDENCE_INTEGRITY_FAILURE","原来源证据不一致，请保留资料并核对"); }
    record Context(SubmissionStore.Head head,com.fasterxml.jackson.databind.JsonNode metadata,String readerPermissionVersion) { }
}
