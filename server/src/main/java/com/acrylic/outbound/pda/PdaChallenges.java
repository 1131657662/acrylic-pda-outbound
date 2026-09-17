package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PdaChallenges {
    private final JdbcTemplate jdbc;private final DeviceStore devices;
    public PdaChallenges(JdbcTemplate jdbc,DeviceStore devices) { this.jdbc=jdbc;this.devices=devices; }
    public Challenge create(Request input) {
        devices.find(input.deviceId(),false).requireActive();UUID id=Ids.next();String nonce=DeviceCrypto.secret();
        Instant expires=Instant.now().plusSeconds(120);
        jdbc.update("INSERT INTO device_challenge VALUES(?,?,?,?,?,NULL,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(input.deviceId()),
            input.purpose(),nonce,java.sql.Timestamp.from(expires));
        return new Challenge(id.toString(),message(id,input.deviceId(),input.purpose(),nonce,input.bindingSha256()),expires.toString());
    }
    public void consume(Proof proof,DeviceStore.Device device,String binding) {
        var rows=jdbc.query("SELECT nonce FROM device_challenge WHERE id=? AND device_id=? AND purpose=? AND used_at IS NULL AND expires_at>UTC_TIMESTAMP(3) FOR UPDATE",
            (rs,n)->rs.getString(1),Ids.bytes(proof.challenge()),Ids.bytes(device.id()),proof.purpose());
        if(rows.isEmpty()) throw new BusinessException(401,"DEVICE_CHALLENGE_EXPIRED","设备认证挑战已失效，请重新认证");
        DeviceCrypto.verify(device.publicKey(),message(proof.challenge(),device.id(),proof.purpose(),rows.getFirst(),DeviceCrypto.sha(binding)),proof.signature());
        jdbc.update("UPDATE device_challenge SET used_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(proof.challenge()));
    }
    private String message(UUID id,UUID device,String purpose,String nonce,String binding) {
        return "acrylic-pda-proof-v1\n"+id+"\n"+device+"\n"+purpose+"\n"+nonce+"\n"+binding;
    }
    public record Request(@NotNull UUID deviceId,@NotNull @Pattern(regexp="LOGIN|REFRESH") String purpose,
        @NotNull @Pattern(regexp="[0-9a-f]{64}") String bindingSha256) { }
    public record Challenge(String challengeId,String signedText,String expiresAt) { }
    public record Proof(UUID challenge,String purpose,String signature) { }
}
