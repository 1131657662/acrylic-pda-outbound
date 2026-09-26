package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionControl;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.TemporarySubmissionEvidence;
import com.acrylic.outbound.temporary.TemporarySubmissionPlan;
import com.acrylic.outbound.temporary.TemporarySubmissionSnapshot;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads independent frozen evidence; never restores an aborted business payload or creates an order. */
@Service
public class SourceEvidenceIntakeLocalPayload {
    private final SourceEvidenceIntakeRawObjects objects;
    private final SourceEvidenceIntakeObjectFiles files;
    private final SubmissionStore submissions;
    private final SourceEvidenceProof proofs;
    private final TemporarySubmissionPlan plans;
    private final TemporarySubmissionEvidence evidence;
    private final TemporarySubmissionSnapshot snapshots;
    private final SourceEvidenceIntakeAuditPrefix audits;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeLocalPayload(SourceEvidenceIntakeRawObjects objects,
        SourceEvidenceIntakeObjectFiles files, SubmissionStore submissions, SourceEvidenceProof proofs,
        TemporarySubmissionPlan plans, TemporarySubmissionEvidence evidence,
        TemporarySubmissionSnapshot snapshots, SourceEvidenceIntakeAuditPrefix audits, JdbcTemplate jdbc) {
        this.objects = objects; this.files = files; this.submissions = submissions; this.proofs = proofs;
        this.plans = plans; this.evidence = evidence; this.snapshots = snapshots; this.audits = audits; this.jdbc = jdbc;
    }

    JsonNode require(SourceEvidenceIntakePageObjects.Binding binding, List<E2aPageItem> items, long auditThrough) {
        var head = submissions.required(binding.submissionId());
        requireHead(binding, head);
        var frozen = items.stream().filter(item -> "LOCAL_FROZEN_PAYLOAD".equals(item.objectKind())).toList();
        require(frozen.size() == 1, "E2A_LOCAL_PAYLOAD_MISSING_OR_DUPLICATE");
        var item = frozen.getFirst();
        require("STOP".equals(item.itemKind()) && "FROZEN".equals(item.recordStatus())
            && auditThrough > 0 && item.originalDeviceSeq() == auditThrough
            && binding.submissionId().equals(item.originalRecordId()), "E2A_LOCAL_PAYLOAD_BINDING_CONFLICT");
        var body = read(binding, item);
        require(SubmissionControl.read(SubmissionJson.uuid(body, "submissionId"), body).equals(head.control()),
            "E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT");
        submissions.requireUploadHash(binding.submissionId(), SubmissionJson.sha(body));
        var plan = plans.validate(head, body);
        evidence.validate(head, plan, body);
        snapshots.validate(plan, body);
        requireAudits(binding, head, body, auditThrough);
        requireUnclaimedCaptures(body);
        return body;
    }

    private void requireHead(SourceEvidenceIntakePageObjects.Binding binding, SubmissionStore.Head head) {
        var control = head.control();
        require("ABORTED".equals(head.status()) && head.order() == null && control.temporary() != null
            && binding.warehouseId().equals(head.warehouse()) && binding.originalOperatorId().equals(head.actor())
            && binding.creatorDeviceId().equals(control.device()) && binding.workRound() == control.round(),
            "E2A_LOCAL_PAYLOAD_SOURCE_CONFLICT");
        submissions.current(head);
        require("VERIFIED".equals(proofs.read(head, null).path("verification").textValue()),
            "E2A_ABORT_PROOF_UNVERIFIABLE");
    }

    private JsonNode read(SourceEvidenceIntakePageObjects.Binding binding, E2aPageItem item) {
        var key = new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(), binding.scopeId(),
            binding.submissionId(), binding.workRound(), binding.creatorDeviceId(), binding.originalOperatorId(),
            item.objectKind(), item.originalObjectId());
        var object = objects.findAfterSourceLock(key);
        require(object != null && item.itemBytesSha256().equals(object.stored().sha256())
            && item.byteCount() == object.stored().bytes(), "E2A_LOCAL_PAYLOAD_OBJECT_CONFLICT");
        try {
            var original = files.read(object.stored());
            var body = SubmissionJson.parse(original);
            require(item.recordSha256().equals(SubmissionJson.sha(body))
                && Arrays.equals(original, SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8)),
                "E2A_LOCAL_PAYLOAD_DIGEST_CONFLICT");
            return body;
        } catch (IOException invalid) { throw conflict("E2A_LOCAL_PAYLOAD_FILE_UNAVAILABLE"); }
    }

    private void requireAudits(SourceEvidenceIntakePageObjects.Binding binding, SubmissionStore.Head head,
        JsonNode body, long auditThrough) {
        long epoch = Long.parseLong(head.control().epoch());
        for (var event : SubmissionJson.array(body, "events")) {
            require(SubmissionJson.number(event, "localSeq") < auditThrough, "E2A_LOCAL_AUDIT_OUTSIDE_STOP");
            audits.requireArchiveEvent(event, binding.creatorDeviceId(), binding.warehouseId(), epoch);
        }
    }

    private void requireUnclaimedCaptures(JsonNode body) {
        for (var capture : SubmissionJson.array(body, "captures")) {
            var owners = jdbc.queryForList("""
                SELECT BIN_TO_UUID(submission_id) FROM submitted_capture
                WHERE id=? OR (origin_device_id=? AND device_capture_seq=?)
                """, String.class, Ids.bytes(SubmissionJson.uuid(capture, "id")),
                Ids.bytes(SubmissionJson.uuid(capture, "originDeviceId")), SubmissionJson.number(capture, "deviceCaptureSeq"));
            require(owners.isEmpty(), "E2A_LOCAL_CAPTURE_ALREADY_SUBMITTED");
        }
    }

    private static void require(boolean valid, String code) { if (!valid) throw conflict(code); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "独立冻结证据不可核验，保留原取消与实物状态"); }
}
