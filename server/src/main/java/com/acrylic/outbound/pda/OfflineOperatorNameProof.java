package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.reports.OperatorNames;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Durable display evidence only; this audience cannot authorize work or extend a credential. */
@Component
public class OfflineOperatorNameProof {
    private final JdbcTemplate jdbc;
    private final OfflineSigner signer;
    public OfflineOperatorNameProof(JdbcTemplate jdbc,OfflineSigner signer) {
        this.jdbc=jdbc;this.signer=signer;
    }
    public String issue(String source,UUID authorization,PdaSessions.Principal actor,String original,Instant issued) {
        if(!Set.of("TEMP","INDEPENDENT").contains(source))throw new IllegalArgumentException("Name proof source");
        var rows=jdbc.query("SELECT display_name,username FROM app_user WHERE id=?",
            (rs,n)->new Name(rs.getString(1),rs.getString(2)),Ids.bytes(actor.userId()));
        if(rows.size()!=1)return null;
        var name=rows.getFirst();
        if(!OperatorNames.usable(name.display(),actor.userId().toString(),name.account()))return null;
        var claims=new Claims("acrylic-outbound","acrylic-pda-operator-name-v1","READONLY_OPERATOR_NAME",
            source,authorization.toString(),actor.userId().toString(),actor.deviceId().toString(),
            actor.warehouseId().toString(),DeviceCrypto.sha(original),name.display(),issued.toString());
        return signer.sign(claims).token();
    }
    private record Name(String display,String account) { }
    private record Claims(String iss,String aud,String purpose,String source,String authorizationId,
        String operatorId,String deviceId,String warehouseId,String originalSha256,String displayName,String issuedAt) { }
}
