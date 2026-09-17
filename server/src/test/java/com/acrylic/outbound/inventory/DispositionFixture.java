package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DispositionFixture {
    final ContinuationFixture c;final String action;JsonNode evidence;
    DispositionFixture(ContinuationFixture c,String action) { this.c=c;this.action=action; }
    ObjectNode evidenceBody() throws Exception {
        var body=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(c.r.body)).put("expectedVersion",c.r.read().get("rowVersion").asText());
        var scan=c.r.body.at("/scans/0");body.putArray("dispositions").add(SubmissionJson.object().put("captureId",scan.get("captureId").asText())
            .put("expectedDispatchCycle",scan.get("expectedDispatchCycle").asText()).put("action",action).put("physicalEvidence","主管现场核验：原包已卸至指定库位 D-12，双人复核编号 E-13"));
        return body;
    }
    MockHttpServletRequestBuilder approveEvidence(ObjectNode body,String key) {
        return post("/api/v1/recovery-packages/"+c.r.body.get("recoveryPackageId").asText()+"/disposition-evidence")
            .header("Idempotency-Key",key).contentType("application/json").content(body.toString());
    }
    void approveEvidence() throws Exception { evidence=c.r.f.web.data(approveEvidence(evidenceBody(),UUID.randomUUID().toString())); }
    ObjectNode requestBody() {
        var body=c.requestBody();((ObjectNode)body.at("/dispositions/0")).put("action",action).put("approvedEvidenceId",evidence.at("/evidence/0/approvedEvidenceId").asText());return body;
    }
    void activate() throws Exception {
        approveEvidence();c.ticket=c.r.f.web.data(c.request(requestBody(),UUID.randomUUID().toString()));c.approve();c.activate();
    }
    ObjectNode second() {
        var body=c.second().put("expectedCount","1").put("expectedWeightGrams","301000").put("shortShipmentReason","主管已批准原包卸货，本车按有效装载短装出库");
        ((ObjectNode)body.get("captureSummary")).put("saved","1");body.set("dispositions",c.activation.get("dispositions"));return body;
    }
    JsonNode review(ObjectNode body) throws Exception {
        var command=SubmissionJson.object().put("decision","APPROVE").put("reason","主管核验短装").put("payloadSha256",SubmissionJson.sha(body));
        return c.r.f.web.data(post("/api/v1/submissions/"+body.get("submissionId").asText()+"/review").contentType("application/json").content(command.toString()));
    }
}
