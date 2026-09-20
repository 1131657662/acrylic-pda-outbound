package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ServerState;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceIdentity.*;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SourceEvidenceService {
    private final SourceEvidenceAccess access;private final SourceEvidenceQueries queries;private final SourceEvidenceArtifacts artifacts;
    private final SourceEvidenceProof proof;private final SourceEvidenceFacts facts;private final ServerState state;
    public SourceEvidenceService(SourceEvidenceAccess access,SourceEvidenceQueries queries,SourceEvidenceArtifacts artifacts,
        SourceEvidenceProof proof,SourceEvidenceFacts facts,ServerState state) {
        this.access=access;this.queries=queries;this.artifacts=artifacts;this.proof=proof;this.facts=facts;this.state=state;
    }
    public JsonNode read(Selection selection) {
        var context=access.read(selection);var h=context.head();var stored=artifacts.inspect(h);
        var terminal=proof.read(h,stored.payload());var result=object().put("viewVersion",1);
        result.set("provenance",SourceEvidenceView.provenance(context));result.set("control",SourceEvidenceView.control(context));
        result.set("mapping",queries.mapping(h));result.set("receivedPayload",stored.received());result.set("declaredUpload",stored.upload());
        result.set("terminalProof",terminal);result.set("physical",facts.physical(h));result.set("audit",facts.audit(h));
        result.set("currentAssessment",SourceEvidenceView.assessment(context,stored,terminal,state.current()));return result;
    }
    public byte[] payload(Selection selection) { return artifacts.payload(access.read(selection).head()); }
    public byte[] chunk(Selection selection,int index) { access.read(selection);return artifacts.chunk(selection.submissionId(),index); }
}
