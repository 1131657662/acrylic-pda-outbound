package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service @Transactional(readOnly=true)
public class TemporaryReviewService {
    private final Access access;private final SubmissionStore submissions;private final SubmissionFiles files;
    private final TemporaryStore authorizations;private final TemporaryReviewQueries queries;
    private final TemporaryReviewEligibility eligibility;private final TemporaryReviewStock stock;private final ServerState state;
    public TemporaryReviewService(Access access,SubmissionStore submissions,SubmissionFiles files,TemporaryStore authorizations,
        TemporaryReviewQueries queries,TemporaryReviewEligibility eligibility,TemporaryReviewStock stock,ServerState state) {
        this.access=access;this.submissions=submissions;this.files=files;this.authorizations=authorizations;
        this.queries=queries;this.eligibility=eligibility;this.stock=stock;this.state=state;
    }
    public Page<TemporaryReviewQueries.Item> list(UUID warehouse,String cursor,int limit) {
        access.require("temporary_order:confirm",warehouse);return queries.current(warehouse,cursor,limit);
    }
    public JsonNode detail(UUID id,UUID warehouse) {
        var actor=access.require("temporary_order:confirm",warehouse);var evidence=read(id,warehouse);var h=evidence.head();
        var metadata=queries.metadata(id);var original=authorizations.get(h.control().temporary().authorization());var server=state.current();
        var result=header(evidence,metadata);
        result.put("reviewerPermissionVersion",Long.toString(actor.authVersion())).put("checkedAt",Instant.now().toString());
        result.put("serverRecoveryEpoch",server.epoch()).put("serviceMode",server.mode());
        result.set("authorization",object().put("authorizationId",original.id().toString()).put("issuedAt",original.issuedAt().toString())
            .put("expiresAt",original.expiresAt().toString()).put("userVersion",original.userVersion()).put("deviceVersion",original.deviceVersion())
            .put("recoveryEpoch",original.recoveryEpoch()));
        result.set("products",TemporaryReviewEvidence.products(evidence.body()));
        result.set("receivedIssues",TemporaryReviewEvidence.originalIssues(h.issues()));
        var issues=eligibility.current(original,server);
        TemporaryReviewEligibility.add(issues,"SUBMISSION_NOT_CURRENT",!metadata.current());
        TemporaryReviewEligibility.add(issues,"SUBMISSION_NOT_PENDING",!h.status().equals("REVIEW_REQUIRED"));
        boolean shortShipment=false;for(var p:result.path("products")) shortShipment|=p.path("shortCount").asLong()>0;
        TemporaryReviewEligibility.add(issues,"SHORT_SHIPMENT_APPROVAL_REQUIRED",shortShipment);
        stock.issues(evidence.body(),id).forEach((code,count)->issues.add(object().put("code",code).put("count",count.toString())));
        result.set("currentIssues",issues);return result;
    }
    public Page<JsonNode> scans(UUID id,UUID warehouse,String cursor,int limit) {
        access.require("temporary_order:confirm",warehouse);Page.checkedLimit(limit);var evidence=read(id,warehouse);
        var page=TemporaryReviewEvidence.scans(evidence.body(),id,evidence.hash(),cursor,limit);
        var conflicts=stock.conflicts(page.items(),id);var rows=new ArrayList<JsonNode>();
        for(var scan:page.items()) {
            var row=(com.fasterxml.jackson.databind.node.ObjectNode)scan.deepCopy();
            row.put("currentIssue",conflicts.get(scan.path("captureId").asText()));rows.add(row);
        }
        return new Page<>(rows,page.nextCursor(),page.hasMore());
    }
    private Evidence read(UUID id,UUID warehouse) {
        var head=submissions.find(id);
        if(head==null || !head.warehouse().equals(warehouse) || head.control().temporary()==null || head.order()==null) throw missing();
        String hash=submissions.payloadHash(id);if(hash==null) throw missing();
        return new Evidence(head,hash,parse(files.read(hash)));
    }
    private com.fasterxml.jackson.databind.node.ObjectNode header(Evidence e,TemporaryReviewQueries.Metadata m) {
        var h=e.head();var c=h.control();var body=e.body();var plan=body.path("temporaryOrder");
        var result=object().put("submissionId",c.id().toString()).put("orderId",h.order().toString()).put("orderNo",m.orderNo())
            .put("status",h.status()).put("current",m.current()).put("currentSubmissionId",m.currentSubmissionId())
            .put("payloadSha256",e.hash()).put("workRound",Long.toString(c.round())).put("sourceRecoveryEpoch",c.epoch())
            .put("registeredAt",m.registeredAt().toString()).put("receivedAt",m.receivedAt().toString())
            .put("operatorId",h.actor().toString()).put("operatorName",m.operatorName()).put("deviceName",m.deviceName())
            .put("writeConfirmationAvailable",false).put("releaseAllowed",false);
        result.set("source",body.get("source"));
        for(String field:List.of("temporaryNo","planSha256","orderRevision","inventorySnapshotId","policyVersion","autoDeductInventory",
            "expectedCount","expectedWeightGrams","closedAt","shortShipmentReason")) result.set(field,body.get(field));
        for(String field:List.of("vehicleNo","containerNo","reason")) result.set(field,plan.get(field));
        return result;
    }
    private static BusinessException missing() { return new BusinessException(404,"TEMPORARY_REVIEW_NOT_FOUND","临时审阅记录不存在或无权访问"); }
    private record Evidence(SubmissionStore.Head head,String hash,JsonNode body) { }
}
