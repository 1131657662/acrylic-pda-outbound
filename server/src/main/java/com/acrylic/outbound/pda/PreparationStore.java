package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PreparationStore {
    private final JdbcTemplate jdbc;
    public PreparationStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Receipt find(UUID assignment) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),manifest_text,manifest_sha256,signed_manifest FROM assignment_preparation WHERE assignment_id=?",
            (rs,n)->new Receipt(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),Ids.bytes(assignment));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void insert(UUID assignment,Receipt receipt,List<PreparationArchive> parts) {
        jdbc.update("INSERT INTO assignment_preparation VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(UUID.fromString(receipt.bundleId())),
            Ids.bytes(assignment),receipt.manifest(),receipt.manifestSha256(),receipt.signedManifest(),parts.get(0).compressed(),
            parts.get(1).compressed(),parts.get(2).compressed());
    }
    public byte[] content(UUID assignment,String bundle,String part) {
        if(!Set.of("plan","inventory","availability").contains(part)) throw new BusinessException(404,"NOT_FOUND","下载文件不存在");
        var receipt=find(assignment);
        if(receipt==null || !receipt.bundleId().equals(bundle)) throw new BusinessException(404,"NOT_FOUND","下载版本不存在");
        return jdbc.queryForObject("SELECT "+part+"_gzip FROM assignment_preparation WHERE assignment_id=?",byte[].class,Ids.bytes(assignment));
    }
    public String acknowledge(UUID assignment) {
        jdbc.update("UPDATE order_assignment SET download_ack_at=COALESCE(download_ack_at,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(assignment));
        return jdbc.queryForObject("SELECT download_ack_at FROM order_assignment WHERE id=?",(rs,n)->rs.getTimestamp(1).toInstant().toString(),Ids.bytes(assignment));
    }
    public record Receipt(String bundleId,String manifest,String manifestSha256,String signedManifest) { }
}
