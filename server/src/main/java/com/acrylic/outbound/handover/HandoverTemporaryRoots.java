package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.evidence.*;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates the original device's complete E2A roots without impersonating it or writing another verification. */
@Component
final class HandoverTemporaryRoots {
    private final JdbcTemplate jdbc;
    private final SourceEvidenceIntakeReceipts opens;
    private final SourceEvidenceIntakeDeviceKeys keys;
    private final SourceEvidenceIntakeVerifyPreflight preflight;
    private final SourceEvidenceIntakeAuditPrefix audits;

    HandoverTemporaryRoots(JdbcTemplate jdbc, SourceEvidenceIntakeReceipts opens,
            SourceEvidenceIntakeDeviceKeys keys, SourceEvidenceIntakeVerifyPreflight preflight, SourceEvidenceIntakeAuditPrefix audits) {
        this.jdbc = jdbc; this.opens = opens; this.keys = keys; this.preflight = preflight;
        this.audits = audits;
    }

    Verified require(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        return require(HandoverTemporaryArchive.fromDevice(command), origin);
    }

    Verified require(HandoverTemporaryArchive command, HandoverStopStore.Origin origin) {
        var row = stored(command, origin);
        byte[] bytes = (byte[]) row.get("canonical_request_bytes");
        var body = parse(bytes); UUID openKey = uuid(body, "openOriginalKey");
        final SourceEvidenceIntakeStop.Expected expected;
        final SourceEvidenceIntakeStop.Parsed stop;
        try {
            var open = opens.openBound(bound(origin, openKey));
            expected = new SourceEvidenceIntakeStop.Expected(openKey, open, origin.warehouse(), origin.operator(),
                origin.device(), Long.parseLong(origin.epoch()), keys.trusted(origin.warehouse(), origin.device()));
            stop = SourceEvidenceIntakeStop.parse(bytes, expected);
        }
        catch (RuntimeException invalid) { throw changed(); }
        if (!command.payloadHash().equals(stop.setHash())
                || !Arrays.equals(stop.manifestBytes(), (byte[]) row.get("manifest_bytes"))) throw changed();
        var binding = new SourceEvidenceIntakePageObjects.Binding(origin.warehouse(), origin.scope(), origin.submission(),
            origin.round(), origin.device(), origin.operator(), openKey);
        var stopBinding = new SourceEvidenceIntakeStopObjects.Binding(origin.warehouse(), origin.scope(), origin.submission(),
            origin.round(), origin.device(), origin.operator(), openKey);
        preflight.requireStoredRoots(bytes, expected, bound(origin, UUID.fromString((String) row.get("original_key"))),
            stopBinding, binding);
        return new Verified(binding, parse(stop.manifestBytes()));
    }

    private Map<String, Object> stored(HandoverTemporaryArchive command, HandoverStopStore.Origin origin) {
        var rows = jdbc.queryForList("""
            SELECT r.canonical_request_bytes,BIN_TO_UUID(r.original_key) original_key,e.manifest_bytes
            FROM source_evidence_set e JOIN source_evidence_intake_receipt r ON r.object_id=e.stop_object_id
              AND r.scope_id=e.scope_id AND r.submission_id=e.submission_id AND r.work_round=e.work_round
              AND r.warehouse_id=e.warehouse_id AND r.operation_kind='STOP' AND r.route_scope=?
            WHERE e.id=? AND e.scope_id=? AND e.submission_id=? AND e.work_round=? AND e.warehouse_id=?
              AND r.principal_id=? AND r.original_operator_id=? AND r.creator_device_id=?
              AND r.temporary_authorization_id=? AND r.source_recovery_epoch=?
            """, E2aProtocol.ROUTE_SCOPE, Ids.bytes(command.evidenceSet()), Ids.bytes(origin.scope()),
            Ids.bytes(origin.submission()), origin.round(), Ids.bytes(origin.warehouse()), Ids.bytes(origin.operator()),
            Ids.bytes(origin.operator()), Ids.bytes(origin.device()), Ids.bytes(origin.temporary().authorization()), origin.epoch());
        if (rows.size() != 1) throw changed();
        return rows.getFirst();
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(HandoverStopStore.Origin origin, UUID key) {
        return new SourceEvidenceIntakeReceipts.BoundKey(origin.operator(), key, origin.warehouse(), origin.submission(),
            origin.round(), origin.device(), origin.temporary().authorization());
    }

    void events(JsonNode body, HandoverStopCommand command, HandoverStopStore.Origin origin) {
        events(body, command.auditHigh(), origin);
    }

    void events(JsonNode body, long auditHigh, HandoverStopStore.Origin origin) {
        for (var event : array(body, "events")) {
            var device = uuid(event, "originDeviceId");
            if (device.equals(origin.device()) && number(event, "localSeq") >= auditHigh) throw changed();
            audits.requireArchiveEvent(event, device, origin.warehouse(), Long.parseLong(origin.epoch()));
        }
    }

    static BusinessException changed() { return new BusinessException(409, "HANDOVER_EVIDENCE_CHANGED", "原临时来源停止证据不可完整核验"); }
    record Verified(SourceEvidenceIntakePageObjects.Binding binding, JsonNode manifest) { }
}
