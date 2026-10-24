package com.acrylic.outbound.independent;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.pda.OfflineOperatorNameProof;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IndependentCredentialStore {
    private final JdbcTemplate jdbc;private final IdempotencyRequests requests;private final AuditWriter audit;private final JsonText json;
    private final OfflineOperatorNameProof names;
    public IndependentCredentialStore(JdbcTemplate jdbc,IdempotencyRequests requests,AuditWriter audit,JsonText json,
        OfflineOperatorNameProof names) {
        this.jdbc=jdbc;this.requests=requests;this.audit=audit;this.json=json;this.names=names;
    }
    public Ticket prepare(PdaSessions.Principal actor,IndependentCredentialService.Context context,
        IndependentCredentialService.Command command) {
        var key=new IdempotencyRequests.Key(actor.userId(),"independent.credential",context.key());
        String digest=json.digest(new Request(actor.deviceId(),actor.warehouseId(),context.epoch(),command));
        return new Ticket(key,digest,requests.find(key,digest));
    }
    public Receipt read(UUID id,PdaSessions.Principal actor) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),signed_credential,issued_at,expires_at,operator_name_proof FROM independent_capture_credential
            WHERE id=? AND operator_id=? AND device_id=? AND warehouse_id=?
            """,(rs,n)->new Receipt(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant().toString(),
                rs.getTimestamp(4).toInstant().toString(),rs.getString(5)),Ids.bytes(id),Ids.bytes(actor.userId()),
                Ids.bytes(actor.deviceId()),Ids.bytes(actor.warehouseId()));
        if(rows.isEmpty())throw new BusinessException(404,"INDEPENDENT_CREDENTIAL_NOT_FOUND","凭据不存在或无权读取");
        return rows.getFirst();
    }
    public Receipt save(IndependentCredentialTokens.Input input,String token,Ticket ticket,String requestId) {
        var actor=input.actor();
        String nameProof=names.issue("INDEPENDENT",input.id(),actor,token,input.issued());
        jdbc.update("""
            INSERT INTO independent_capture_credential(id,operator_id,device_id,warehouse_id,device_key_sha256,
                user_auth_version,device_version,recovery_epoch,issued_at,expires_at,signed_credential,credential_sha256,operator_name_proof)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(input.id()),Ids.bytes(actor.userId()),Ids.bytes(actor.deviceId()),Ids.bytes(actor.warehouseId()),
                input.keySha256(),Long.parseLong(actor.userAuthVersion()),Long.parseLong(actor.deviceVersion()),
                Long.parseLong(input.epoch()),java.sql.Timestamp.from(input.issued()),
                java.sql.Timestamp.from(input.issued().plusSeconds(48*3600)),token,DeviceCrypto.sha(token),nameProof);
        requests.record(ticket.key(),ticket.digest(),input.id());
        audit.append(new AuditWriter.Event(actor.userId(),"INDEPENDENT_CREDENTIAL_ISSUED","INDEPENDENT_CREDENTIAL",
            input.id(),json.encode(new Issued(actor.deviceId(),actor.warehouseId(),DeviceCrypto.sha(token)))),requestId);
        return read(input.id(),actor);
    }
    public record Receipt(String credentialId,String signedCredential,String issuedAt,String expiresAt,String operatorNameProof) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,UUID replay) { }
    private record Request(UUID device,UUID warehouse,String epoch,IndependentCredentialService.Command command) { }
    private record Issued(UUID device,UUID warehouse,String credentialSha256) { }
}
