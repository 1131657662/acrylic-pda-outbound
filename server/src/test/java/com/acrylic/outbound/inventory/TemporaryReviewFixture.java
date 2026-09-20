package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

final class TemporaryReviewFixture {
    final JdbcTemplate jdbc;final MockMvc mvc;final ObjectMapper json;final PasswordEncoder passwords;
    final TemporarySubmissionFixture temporary;final PublicationDriver reviewer;
    TemporaryReviewFixture(JdbcTemplate jdbc,MockMvc mvc,ObjectMapper json,PasswordEncoder passwords) throws Exception {
        this.jdbc=jdbc;this.mvc=mvc;this.json=json;this.passwords=passwords;
        var owner=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        temporary=new TemporarySubmissionFixture(new SubmissionFixture(owner,jdbc,false,false));
        reviewer=reader("TEMPORARY_ORDER_CONFIRMER");
    }
    PublicationDriver reader(String... roles) {
        var identity=IdentityFixture.create(jdbc,passwords,"ORDER_VIEWER");
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(identity.user().id()));
        for(String role:roles) jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",Ids.bytes(identity.user().id()),role);
        var warehouse=temporary.f.web.identity().warehouse();
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(warehouse));
        return new PublicationDriver(mvc,json,new IdentityFixture(identity.user(),warehouse));
    }
    String path() { return "/api/v1/temporary-reviews/"+temporary.id(); }
    JsonNode detail() throws Exception { return reviewer.data(get(path())); }
    JsonNode list() throws Exception { return reviewer.data(get("/api/v1/temporary-reviews")); }
    String facts() {
        return jdbc.queryForMap("SELECT status,issues_json,receipt_text FROM submission WHERE id=UUID_TO_BIN(?)",temporary.id()).toString()
            +jdbc.queryForList("SELECT BIN_TO_UUID(bale_id),BIN_TO_UUID(submission_id),state,dispatch_cycle FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) ORDER BY bale_id",temporary.id())
            +jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,temporary.id())
            +jdbc.queryForObject("SELECT COUNT(*) FROM submission_review WHERE submission_id=UUID_TO_BIN(?)",Integer.class,temporary.id())
            +jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM inventory_ledger) AS ledger,(SELECT COUNT(*) FROM order_result_version) AS results,
                  (SELECT COUNT(*) FROM operation_event) AS events,(SELECT COUNT(*) FROM barcode_sync_pending) AS barcodeChanges,
                  (SELECT COUNT(*) FROM order_sync_outbox) AS orderChanges
                """)
            +jdbc.queryForList("""
                SELECT o.status,o.row_version,o.current_result_version FROM outbound_order o
                INNER JOIN submission_scope c ON c.canonical_order_id=o.id WHERE c.current_submission_id=UUID_TO_BIN(?)
                """,temporary.id());
    }
}
