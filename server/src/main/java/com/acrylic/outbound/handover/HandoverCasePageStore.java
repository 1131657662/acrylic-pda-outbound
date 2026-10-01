package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads bounded case summaries for one authorized warehouse/order. */
@Repository
class HandoverCasePageStore {
    private final JdbcTemplate jdbc;
    HandoverCasePageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    String orderNo(UUID warehouse, UUID order) {
        var rows = jdbc.queryForList("SELECT order_no FROM outbound_order WHERE warehouse_id=? AND id=?",
            String.class, Ids.bytes(warehouse), Ids.bytes(order));
        if (rows.size() != 1) throw HandoverCommand.missing();
        return rows.getFirst();
    }

    List<Row> rows(UUID warehouse, UUID order, HandoverCaseCursor cursor, int limit) {
        var args = new ArrayList<Object>(List.of(Ids.bytes(warehouse), Ids.bytes(order)));
        String after = "";
        if (cursor != null) {
            after = " AND (requested_at < ? OR (requested_at = ? AND id < ?))";
            args.add(Timestamp.valueOf(cursor.requestedAt())); args.add(Timestamp.valueOf(cursor.requestedAt()));
            args.add(Ids.bytes(cursor.id()));
        }
        args.add(limit);
        return jdbc.query("SELECT BIN_TO_UUID(id) id,status,from_work_round,to_work_round,requested_at,row_version "
            + "FROM handover_case WHERE warehouse_id=? AND canonical_order_id=?" + after
            + " ORDER BY requested_at DESC,id DESC LIMIT ?", (rs, index) -> new Row(
                UUID.fromString(rs.getString("id")), rs.getString("status"), rs.getInt("from_work_round"),
                rs.getInt("to_work_round"), rs.getTimestamp("requested_at").toLocalDateTime(),
                Long.toString(rs.getLong("row_version"))), args.toArray());
    }

    record Row(UUID caseId, String status, int fromWorkRound, int toWorkRound, LocalDateTime requestedAt, String caseVersion) { }
}
