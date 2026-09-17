package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class ContinuationStore {
    private final JdbcTemplate jdbc;private final SubmissionFiles files;
    public ContinuationStore(JdbcTemplate jdbc,SubmissionFiles files) { this.jdbc=jdbc;this.files=files; }
    public UUID forRecall(UUID recall) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM order_continuation WHERE request_id=?",String.class,Ids.bytes(recall));
        return ids.isEmpty()?null:UUID.fromString(ids.getFirst());
    }
    public Head get(UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(request_id),BIN_TO_UUID(scope_id),BIN_TO_UUID(order_id),BIN_TO_UUID(previous_submission_id),
            from_round,to_round,revision,BIN_TO_UUID(assigned_device_id),BIN_TO_UUID(recovery_package_id),LOWER(HEX(retained_manifest_sha256)),
            next_order_scan_seq,status,command_text,BIN_TO_UUID(new_assignment_id),receipt_text,activation_text FROM order_continuation WHERE id=?
            """,(rs,n)->new Head(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),
                UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),rs.getLong(6),rs.getLong(7),rs.getInt(8),
                UUID.fromString(rs.getString(9)),UUID.fromString(rs.getString(10)),rs.getString(11),rs.getLong(12),rs.getString(13),
                parse(rs.getString(14).getBytes(java.nio.charset.StandardCharsets.UTF_8)),rs.getString(15),rs.getString(16),rs.getString(17)),Ids.bytes(id));
        if(rows.isEmpty())throw new BusinessException(404,"CONTINUATION_NOT_FOUND","续作申请不存在或无权访问");return rows.getFirst();
    }
    public Head forAssignment(UUID assignment) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM order_continuation WHERE new_assignment_id=?",String.class,Ids.bytes(assignment));
        return ids.isEmpty()?null:get(UUID.fromString(ids.getFirst()));
    }
    public JsonNode body(Head h) {
        String hash=jdbc.queryForObject("SELECT payload_sha256 FROM recovery_package WHERE id=?",String.class,Ids.bytes(h.recovery()));
        return parse(files.read(hash));
    }
    public JsonNode view(Head h) {
        var result=object().put("continuationId",h.id().toString()).put("changeRequestId",h.id().toString()).put("type","REOPEN_WORK")
            .put("orderId",h.order().toString()).put("previousSubmissionId",h.previous().toString()).put("status",h.status())
            .put("workRound",Long.toString(h.toRound())).put("revision",h.revision()).put("requestedDeviceId",h.device().toString())
            .put("nextOrderScanSeq",Long.toString(h.nextSeq())).put("retainedManifestSha256",h.manifest()).put("recoveryPackageId",h.recovery().toString());
        if(h.receipt()!=null)result.set("approval",parse(h.receipt().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        result.set("dispositions",dispositions(h));
        return result;
    }
    public JsonNode dispositions(Head h) { return DispositionProofs.compose(body(h),h.command()); }
    public record Head(UUID id,UUID recall,UUID scope,UUID order,UUID previous,long fromRound,long toRound,int revision,
        UUID device,UUID recovery,String manifest,long nextSeq,String status,JsonNode command,String assignment,String receipt,String activation) {}
}
