package com.acrylic.outbound.pairing;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PairingChallenges {
    private final JdbcTemplate jdbc; private final ServerState state;
    public PairingChallenges(JdbcTemplate jdbc,ServerState state) { this.jdbc=jdbc;this.state=state; }
    @Transactional
    public PairingCommands.Challenge create(PairingCommands.ChallengeRequest request) {
        DeviceCrypto.publicKey(request.publicKey());String epoch=state.current().epoch();state.lockWritable(epoch);
        UUID id=Ids.next();String nonce=DeviceCrypto.secret();Instant expires=Instant.now().plusSeconds(120);
        String hash=DeviceCrypto.sha(request.publicKey());
        jdbc.update("INSERT INTO pairing_challenge VALUES(?,?,?,?,?,?,?,?,NULL)",Ids.bytes(id),Ids.bytes(request.applicationId()),
            hash,request.purpose(),request.bindingSha256(),nonce,epoch,Timestamp.from(expires));
        return new PairingCommands.Challenge(id.toString(),text(id,request,nonce,epoch),expires.toString());
    }
    public void consume(String purpose,PairingCommands.Command command) {
        var p=command.payload();
        var rows=jdbc.queryForList("SELECT * FROM pairing_challenge WHERE id=? AND used_at IS NULL AND expires_at>UTC_TIMESTAMP(3) FOR UPDATE",
            Ids.bytes(command.challengeId()));
        if(rows.isEmpty()) throw new BusinessException(401,"PAIRING_PROOF_EXPIRED","登记证明已过期，请重试原操作");
        var row=rows.getFirst();String epoch=row.get("recovery_epoch").toString();state.lockWritable(epoch);
        var request=new PairingCommands.ChallengeRequest(p.applicationId(),p.publicKey(),purpose,DeviceCrypto.sha(p.binding()));
        boolean bound=java.util.Arrays.equals((byte[])row.get("application_id"),Ids.bytes(p.applicationId())) &&
            row.get("key_sha256").equals(DeviceCrypto.sha(p.publicKey())) && row.get("purpose").equals(purpose) &&
            row.get("binding_sha256").equals(request.bindingSha256());
        if(!bound) throw new BusinessException(401,"INVALID_DEVICE_PROOF","登记证明不匹配");
        DeviceCrypto.verify(p.publicKey(),text(command.challengeId(),request,row.get("nonce").toString(),epoch),command.signature());
        jdbc.update("UPDATE pairing_challenge SET used_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(command.challengeId()));
    }
    private String text(UUID id,PairingCommands.ChallengeRequest r,String nonce,String epoch) {
        return "acrylic-pairing-proof-v1\n"+id+"\n"+r.applicationId()+"\n"+r.purpose()+"\n"+nonce+"\n"+
            DeviceCrypto.sha(r.publicKey())+"\n"+r.bindingSha256()+"\n"+epoch;
    }
}
