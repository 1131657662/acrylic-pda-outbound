package com.acrylic.outbound.correction;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Rollback-only, deliberately untrusted SQL fixtures exercise independent 52-row pages; never a source proof. */
public final class QuarantinePagingChecks {
    private QuarantinePagingChecks() { }
    public static void verify(WebApplicationContext context,String warehouse,String username) {
        var jdbc=context.getBean(JdbcTemplate.class); var reader=context.getBean(QuarantineReadService.class);
        var actor=context.getBean(IdentityStore.class).loadUserByUsername(username); var previous=SecurityContextHolder.getContext();
        var authentication=SecurityContextHolder.createEmptyContext(); authentication.setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
        long bales=jdbc.queryForObject("SELECT COUNT(*) FROM bale",Long.class),cases=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reconciliation",Long.class);
        try {
            SecurityContextHolder.setContext(authentication);
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                tx.setRollbackOnly(); seed(jdbc,warehouse); check(reader,warehouse);
            });
        } finally { SecurityContextHolder.setContext(previous); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale",Long.class)).isEqualTo(bales);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reconciliation",Long.class)).isEqualTo(cases);
    }
    private static void seed(JdbcTemplate jdbc,String warehouse) {
        var origin=jdbc.queryForMap("SELECT d.id,d.scope_id,d.evidence_set_id,i.bale_id,r.original_receipt_id FROM quarantine_origin r JOIN temporary_physical_disposition d ON d.id=r.disposition_id JOIN temporary_physical_disposition_item i ON i.disposition_id=d.id AND i.bale_id=r.bale_id WHERE d.warehouse_id=UUID_TO_BIN(?) LIMIT 1",warehouse);
        for(int index=0;index<104;index++) {
            UUID bale=Ids.next(); jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) SELECT ?,?,warehouse_id,dispatch_cycle,created_at FROM bale WHERE id=?",
                Ids.bytes(bale),String.format("009Q%07d",index),origin.get("bale_id"));
            if(index<52) jdbc.update("INSERT INTO bale_commitment(bale_id,source_scope_id,source_warehouse_id,source_evidence_set_id,disposition_id,dispatch_cycle,state,created_at) VALUES(?,?,UUID_TO_BIN(?),?, ?,1,'QUARANTINED',UTC_TIMESTAMP(3))",
                Ids.bytes(bale),origin.get("scope_id"),warehouse,origin.get("evidence_set_id"),origin.get("id"));
            else caseRow(jdbc,origin,bale);
        }
    }
    private static void caseRow(JdbcTemplate jdbc,Map<String,Object> origin,UUID bale) {
        UUID id=Ids.next(),receipt=Ids.next();
        jdbc.update("INSERT INTO temporary_physical_disposition_item(disposition_id,bale_id,dispatch_cycle,disposition,action) VALUES(?,?,1,'PHYSICAL_DISPOSED','QUARANTINE')",origin.get("id"),Ids.bytes(bale));
        jdbc.update("INSERT INTO inventory_reconciliation(id,warehouse_id,kind,bale_id,status,created_at,updated_at) SELECT ?,warehouse_id,'QUARANTINE_RELEASE',?,'OPEN',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM temporary_physical_disposition WHERE id=?",Ids.bytes(id),Ids.bytes(bale),origin.get("id"));
        var columns=jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='correction_command_receipt' ORDER BY ORDINAL_POSITION",String.class);
        var projection=columns.stream().map(column->Set.of("id","original_key","event_id").contains(column)?"UUID_TO_BIN('"+receipt+"')":"`"+column+"`").toList();
        jdbc.update("INSERT INTO correction_command_receipt SELECT "+String.join(",",projection)+" FROM correction_command_receipt WHERE command_type='RECONCILE_QUARANTINE_DISCOVER' LIMIT 1");
        jdbc.update("INSERT INTO reconciliation_request VALUES(?,NULL,?)",Ids.bytes(id),Ids.bytes(receipt));
        jdbc.update("INSERT INTO quarantine_origin(case_id,disposition_id,bale_id,original_receipt_id) VALUES(?,?,?,?)",
            Ids.bytes(id),origin.get("id"),Ids.bytes(bale),origin.get("original_receipt_id"));
    }
    private static void check(QuarantineReadService reader,String warehouse) {
        UUID wh=UUID.fromString(warehouse); var first=reader.list(wh,false,new QuarantineReadService.Cursors(null,null));
        assertThat(first.path("candidates").size()).isEqualTo(50); assertThat(first.path("cases").size()).isEqualTo(50);
        var nextCandidates=reader.list(wh,false,new QuarantineReadService.Cursors(UUID.fromString(first.path("nextCursor").asText()),null));
        var nextCases=reader.list(wh,false,new QuarantineReadService.Cursors(null,UUID.fromString(first.path("nextCaseCursor").asText())));
        assertThat(nextCandidates.path("candidates").size()).isEqualTo(2); assertThat(nextCandidates.path("cases")).isEqualTo(first.path("cases"));
        assertThat(nextCases.path("cases").size()).isEqualTo(3); assertThat(nextCases.path("candidates")).isEqualTo(first.path("candidates"));
        assertThat(nextCandidates.path("nextCursor").isNull()).isTrue(); assertThat(nextCases.path("nextCaseCursor").isNull()).isTrue();
        unique(first.path("candidates"),nextCandidates.path("candidates"),"baleId"); unique(first.path("cases"),nextCases.path("cases"),"caseId");
        assertThatThrownBy(()->reader.context(wh,null,UUID.fromString(first.path("cases").get(0).path("caseId").asText()),"RECONCILE_QUARANTINE_SUBMIT"))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }
    private static void unique(JsonNode first,JsonNode next,String key) {
        var ids=new HashSet<String>(); for(var row:first) assertThat(ids.add(row.path(key).asText())).isTrue();
        for(var row:next) assertThat(ids.add(row.path(key).asText())).isTrue();
    }
}
