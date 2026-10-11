package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Exact append-only signed effect, including unknown additions and transaction-tail changes. */
@Component
final class HandoverRecoveredConfirmationStore {
    private final JdbcTemplate jdbc;
    private final HandoverRecoveredConfirmationReceipt receipts;
    HandoverRecoveredConfirmationStore(JdbcTemplate jdbc,HandoverRecoveredConfirmationReceipt receipts) {
        this.jdbc=jdbc; this.receipts=receipts;
    }
    byte[] confirm(HandoverRecoveredConfirmationReceipt.Context context,String requestId) {
        HandoverCommand.corruptUnless(TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(jdbc.getDataSource()));
        var rows=read(); HandoverCommand.corruptUnless(rows.size()<=1);
        if(rows.isEmpty()) insert(context,requestId);
        byte[] response=verify(read(),context); var retained=read();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                HandoverCommand.corruptUnless(retained.equals(read())); verify(read(),context);
            }
        }); return response;
    }
    private void insert(HandoverRecoveredConfirmationReceipt.Context context,String requestId) {
        var actor=context.actor(); var command=context.command(); var body=command.body();
        var id=Ids.next(); var at=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",Timestamp.class).toInstant();
        byte[] response=receipts.create(context,id,at,requestId);
        HandoverCommand.corruptUnless(jdbc.update("""
            INSERT INTO recovery_handover_confirmation(case_id,id,command_id,qualification_id,session_id,actor_id,device_id,
                source_recovery_epoch,target_recovery_epoch,command_sha256,command_bytes,receipt_sha256,receipt_bytes,recorded_at,request_id)
            VALUES(?,?,?,?,?,?,?,?,?,UNHEX(?),?,UNHEX(?),?,?,?)
            """,Ids.bytes(uuid(body,"caseId")),Ids.bytes(id),Ids.bytes(uuid(body,"commandId")),Ids.bytes(uuid(body,"qualificationId")),
            Ids.bytes(actor.sessionId()),Ids.bytes(actor.userId()),Ids.bytes(actor.deviceId()),Long.parseLong(text(body,"sourceRecoveryEpoch")),
            Long.parseLong(text(body,"transportRecoveryEpoch")),command.sha256(),command.bytes(),HandoverCommand.sha(response),response,
            Timestamp.from(at),requestId)==1);
    }
    private byte[] verify(JsonNode rows,HandoverRecoveredConfirmationReceipt.Context context) {
        HandoverCommand.corruptUnless(rows.size()==1); var row=rows.get(0); var command=context.command(); var body=command.body();
        byte[] storedCommand=decoded(row,"command_bytes");
        if(!Arrays.equals(storedCommand,command.bytes())) throw HandoverCommand.conflict();
        byte[] response=decoded(row,"receipt_bytes"); var receipt=parse(response);
        var id=uuid(receipt,"receiptId"); Instant at=Instant.parse(text(receipt,"recordedAt")); String requestId=text(receipt,"requestId");
        receipts.verify(response,context,id,at,requestId);
        HandoverCommand.corruptUnless(!at.isAfter(jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",Timestamp.class).toInstant())
            && requestId.matches("[A-Za-z0-9._:-]{1,64}") && row.equals(expected(context,id,at,requestId,response)));
        return response.clone();
    }
    private ObjectNode expected(HandoverRecoveredConfirmationReceipt.Context context,UUID id,Instant at,String requestId,byte[] response) {
        var expected=object(); var command=context.command(); var body=command.body(); var actor=context.actor();
        binary(expected,"case_id",Ids.bytes(uuid(body,"caseId"))); binary(expected,"id",Ids.bytes(id));
        binary(expected,"command_id",Ids.bytes(uuid(body,"commandId"))); binary(expected,"qualification_id",Ids.bytes(uuid(body,"qualificationId")));
        binary(expected,"session_id",Ids.bytes(actor.sessionId())); binary(expected,"actor_id",Ids.bytes(actor.userId())); binary(expected,"device_id",Ids.bytes(actor.deviceId()));
        literal(expected,"source_recovery_epoch",text(body,"sourceRecoveryEpoch")); literal(expected,"target_recovery_epoch",text(body,"transportRecoveryEpoch"));
        binary(expected,"command_sha256",HexFormat.of().parseHex(command.sha256())); binary(expected,"command_bytes",command.bytes());
        binary(expected,"receipt_sha256",HexFormat.of().parseHex(HandoverCommand.sha(response))); binary(expected,"receipt_bytes",response);
        literal(expected,"recorded_at",at.toString()); literal(expected,"request_id",requestId); return expected;
    }
    private JsonNode read() {
        var rows=object().putArray("rows");
        jdbc.query("SELECT * FROM recovery_handover_confirmation ORDER BY case_id LIMIT 2 FOR UPDATE",rs->{
            while(rs.next()) {
                var row=rows.addObject(); var metadata=rs.getMetaData();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    String name=metadata.getColumnLabel(i); byte[] bytes=name.equals("recorded_at")
                        ?rs.getTimestamp(i).toInstant().toString().getBytes(StandardCharsets.UTF_8):rs.getBytes(i);
                    HandoverCommand.corruptUnless(bytes!=null); binary(row,name,bytes);
                }
            } return null;
        }); return rows;
    }
    private static byte[] decoded(JsonNode row,String field) { return Base64.getDecoder().decode(text(row,field)); }
    private static void binary(ObjectNode row,String field,byte[] bytes) { row.put(field,Base64.getEncoder().encodeToString(bytes)); }
    private static void literal(ObjectNode row,String field,String value) { binary(row,field,value.getBytes(StandardCharsets.UTF_8)); }
}
