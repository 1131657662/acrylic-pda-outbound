package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Device-scoped read of the device's own TEMP source bindings. */
@Repository
public class TemporarySourceBindingQueries {
    private final JdbcTemplate jdbc;
    public TemporarySourceBindingQueries(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Page<ObjectNode> mine(UUID device,String before,int pageSize) {
        int limit=Page.checkedLimit(pageSize);
        UUID cursor;
        try { cursor=before.isEmpty()?new UUID(-1L,-1L):UUID.fromString(before); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_CURSOR","绑定分页游标无效"); }
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(c.id) scope_id,BIN_TO_UUID(c.client_order_id) client_order_id,
              BIN_TO_UUID(c.canonical_order_id) canonical_order_id,CAST(c.mapping_version AS CHAR) mapping_version,
              CAST(c.work_round AS CHAR) work_round,IFNULL(BIN_TO_UUID(c.current_submission_id),'null') submission_id,
              IFNULL(l.phase,'null') phase,IFNULL(s.status,'null') submission_status,
              IFNULL(BIN_TO_UUID(m.id),'null') mapping_id,IFNULL(m.origin,'null') mapping_origin,
              IFNULL(o.order_no,'null') order_no,IFNULL(o.source,'null') order_source,IFNULL(o.status,'null') order_status,
              c.id AS ordering
            FROM submission_scope c
            LEFT JOIN order_submission_slot l ON l.scope_id=c.id
            LEFT JOIN submission s ON s.id=c.current_submission_id
            LEFT JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
            LEFT JOIN outbound_order o ON o.id=c.canonical_order_id
            WHERE c.creator_device_id=? AND c.id<?
            ORDER BY c.id DESC LIMIT ?
            """,(rs,n)->{
                var node=SubmissionJson.object().put("scopeId",rs.getString(1)).put("clientOrderId",rs.getString(2))
                    .put("canonicalOrderId",rs.getString(3)).put("mappingVersion",rs.getString(4))
                    .put("workRound",rs.getString(5)).put("currentSubmissionId",text(rs.getString(6)))
                    .put("phase",text(rs.getString(7))).put("submissionStatus",text(rs.getString(8)));
                node.put("mappingId",text(rs.getString(9))).put("mappingOrigin",text(rs.getString(10)));
                node.put("orderNo",text(rs.getString(11))).put("orderSource",text(rs.getString(12)))
                    .put("orderStatus",text(rs.getString(13)));
                node.put("installRequired",rs.getString(10)!=null && rs.getString(10).equals("CONTROLLED_LINK"));
                node.put("scopeCursor",rs.getString(1));
                return node;
            },Ids.bytes(device),Ids.bytes(cursor),limit+1);
        return Page.from(rows,limit,node->node.path("scopeCursor").asText());
    }

    private static String text(String value) { return value==null?"null":value; }
}