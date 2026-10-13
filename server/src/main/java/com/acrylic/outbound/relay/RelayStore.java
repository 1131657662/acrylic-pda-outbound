package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RelayStore {
    private final JdbcTemplate jdbc;
    public RelayStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void insert(UUID id,RelayArchive.Bundle b,UUID actor,boolean trusted) {
        var o=b.original();
        jdbc.update("INSERT INTO original_relay VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(b.exportId()),
            Ids.bytes(o.warehouse()),Ids.bytes(o.device()),Ids.bytes(o.user()),Ids.bytes(actor),b.archiveSha256(),b.keySha256(),
            o.source(),Ids.bytes(o.localOrder()),trusted?"TRUSTED":"UNVERIFIED");
    }
    public Row byExport(UUID export) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM original_relay WHERE export_id=?",String.class,Ids.bytes(export));
        return rows.isEmpty()?null:get(UUID.fromString(rows.getFirst()));
    }
    public Row get(UUID id) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(warehouse_id),archive_sha256,source_trust FROM original_relay WHERE id=?",
            (rs,n)->new Row(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),rs.getString(3),rs.getString(4)),Ids.bytes(id));
        if(rows.isEmpty())throw new BusinessException(404,"ORIGINAL_RELAY_NOT_FOUND","原件中转不存在或无权访问");return rows.getFirst();
    }
    public Result result(UUID id) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(relay_id),receipt_text FROM original_relay_result WHERE id=?",
            (rs,n)->new Result(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),rs.getString(3)),Ids.bytes(id));
        if(rows.isEmpty())throw new BusinessException(404,"ORIGINAL_RELAY_RESULT_NOT_FOUND","原件结果不存在或无权访问");return rows.getFirst();
    }
    public Result matching(UUID relay,String digest) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM original_relay_result WHERE relay_id=? AND result_sha256=?",
            String.class,Ids.bytes(relay),digest);return rows.isEmpty()?null:result(UUID.fromString(rows.getFirst()));
    }
    public void result(Result result,String digest,UUID operator) {
        jdbc.update("INSERT INTO original_relay_result VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(result.id()),Ids.bytes(result.relay()),
            Ids.bytes(operator),digest,result.text());
    }
    public record Row(UUID id,UUID warehouse,String archiveSha256,String trust) { }
    public record Result(UUID id,UUID relay,String text) { }
}
