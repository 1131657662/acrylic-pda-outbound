package com.acrylic.outbound.recovery;

import com.acrylic.outbound.audit.PdaAuditFacts;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Rechecks continuous retained original events; this result grants no stock, replay or new work permission. */
public final class RecoverySourceAudit {
    private final RecoveryAuditArchiveProof archives;

    public RecoverySourceAudit(RecoveryReceiptKeys keys) { archives = new RecoveryAuditArchiveProof(keys); }

    public List<JsonNode> verify(byte[] originalPackage, Context context) {
        require(originalPackage != null && context != null, "RECOVERY_AUDIT_REQUIRED");
        try { return checked(SubmissionJson.parse(originalPackage), context); }
        catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_AUDIT_INVALID", invalid); }
    }

    private List<JsonNode> checked(JsonNode body, Context context) {
        var expected = context.source(); var coverage = SubmissionJson.array(body, "auditCoverage");
        references(body, context);
        require(coverage.size() == 1 && uuid(coverage.get(0), "originDeviceId").equals(expected.deviceId())
            && revision(coverage.get(0), "throughSeq") == expected.auditThroughSeq(), "RECOVERY_AUDIT_COVERAGE");
        require(uuid(body, "freezeDeviceId").equals(expected.deviceId()) && revision(body, "workRound") == expected.workRound()
            && text(body, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch()), "RECOVERY_AUDIT_SOURCE");
        var all = new ArrayList<JsonNode>(); long through = 0;
        for (var segment : context.prefix()) {
            require(revision(segment.proof(), "afterSeq") == through, "RECOVERY_AUDIT_PREFIX_GAP");
            all.addAll(toList(archives.read(segment, expected))); through = revision(segment.proof(), "throughSeq");
        }
        require(through == revision(coverage.get(0), "afterSeq"), "RECOVERY_AUDIT_PREFIX_GAP");
        var tail = SubmissionJson.array(body, "auditEvents"); require(!tail.isEmpty(), "RECOVERY_AUDIT_TAIL_REQUIRED");
        all.addAll(toList(tail)); require(all.size() == expected.auditThroughSeq(), "RECOVERY_AUDIT_COVERAGE");
        var ids = new HashMap<UUID, JsonNode>(); long next = 0;
        for (var event : all) {
            PdaAuditFacts.validate(event); require(revision(event, "localSeq") == ++next, "RECOVERY_AUDIT_SEQUENCE");
            require(ids.put(uuid(event, "id"), event) == null, "RECOVERY_AUDIT_ID_CONFLICT"); origin(event, context);
        }
        current(body, ids, expected); terminal(body, tail.get(tail.size() - 1), expected);
        return all.stream().<JsonNode>map(JsonNode::deepCopy).toList();
    }

    private void references(JsonNode body, Context context) {
        var originals = body.has("auditArchives") ? SubmissionJson.array(body, "auditArchives") : SubmissionJson.object().putArray("empty");
        require(originals.size() == context.prefix().size(), "RECOVERY_AUDIT_ARCHIVE_REFERENCE");
        for (int index = 0; index < originals.size(); index++)
            require(SubmissionJson.canonical(originals.get(index)).equals(SubmissionJson.canonical(context.prefix().get(index).proof())),
                "RECOVERY_AUDIT_ARCHIVE_REFERENCE");
    }

    private void origin(JsonNode event, Context context) {
        boolean temporary = event.has("source");
        UUID qualification = uuid(event, temporary ? "authorizationId" : "assignmentId");
        var source = context.origins().get(qualification);
        require(source != null && source.sourceKind().equals(temporary ? "TEMP" : "FORMAL")
            && source.deviceId().equals(context.source().deviceId()) && source.warehouseId().equals(context.source().warehouseId())
            && uuid(event, "originDeviceId").equals(source.deviceId()) && uuid(event, "operatorId").equals(source.operatorId()),
            "RECOVERY_AUDIT_ORIGIN");
        if (temporary) require(uuid(event.path("source"), "creatorDeviceId").equals(source.creatorDeviceId())
            && uuid(event.path("source"), "clientOrderId").equals(source.clientOrderId()), "RECOVERY_AUDIT_ORIGIN");
    }

    private void current(JsonNode body, Map<UUID, JsonNode> all, RecoverySourceIdentity source) {
        var seen = new HashSet<UUID>();
        for (var event : SubmissionJson.array(body, "events")) {
            UUID id = uuid(event, "id"); require(seen.add(id) && all.containsKey(id)
                && SubmissionJson.sha(event).equals(SubmissionJson.sha(all.get(id))), "RECOVERY_AUDIT_CURRENT_CONFLICT");
        }
        for (var event : all.values()) {
            String qualification = event.has("source") ? "authorizationId" : "assignmentId";
            if (uuid(event, qualification).equals(source.qualificationId()))
                require(seen.contains(uuid(event, "id")), "RECOVERY_AUDIT_CURRENT_MISSING");
        }
    }

    private void terminal(JsonNode body, JsonNode event, RecoverySourceIdentity source) {
        boolean recovery = body.has("recoveryPackageId");
        require(text(event, "type").equals(recovery ? "RECALL_STOPPED" : "FROZEN") && event.path("captureId").isNull()
            && text(event, "detail").equals(text(body, recovery ? "recoveryPackageId" : "submissionId"))
            && uuid(event, event.has("source") ? "authorizationId" : "assignmentId").equals(source.qualificationId()),
            "RECOVERY_AUDIT_TERMINAL");
    }

    private List<JsonNode> toList(JsonNode values) {
        var result = new ArrayList<JsonNode>(); values.forEach(result::add); return result;
    }

    public record Context(RecoverySourceIdentity source, Map<UUID, RecoverySourceIdentity> origins,
            List<RecoveryAuditArchiveProof.Segment> prefix) {
        public Context {
            require(source != null && origins != null && prefix != null && prefix.size() <= 1024, "RECOVERY_AUDIT_CONTEXT");
            origins = Map.copyOf(origins); prefix = List.copyOf(prefix);
            require(source.equals(origins.get(source.qualificationId())), "RECOVERY_AUDIT_CONTEXT");
            origins.forEach((id, value) -> require(id.equals(value.qualificationId()), "RECOVERY_AUDIT_CONTEXT"));
        }
    }
}
