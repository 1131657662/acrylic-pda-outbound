package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Synthetic registered P-256 device and scoped personnel; never uses original PDA identity. */
final class RescueFixture {
    final PublicationDriver operator,supervisor;final PdaTestDriver pda;final PdaTestDriver.Device device;
    final JsonNode tokens;final JdbcTemplate jdbc;final ObjectNode document;final String key=UUID.randomUUID().toString();
    RescueFixture(MockMvc mvc,ObjectMapper json,JdbcTemplate jdbc,PasswordEncoder passwords) throws Exception {
        this.jdbc=jdbc;operator=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        var recipient=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(recipient.user().id()),Ids.bytes(operator.identity().warehouse()));
        supervisor=new PublicationDriver(mvc,json,new IdentityFixture(recipient.user(),operator.identity().warehouse()));
        pda=new PdaTestDriver(operator);device=pda.register();tokens=pda.login(device);
        document=SubmissionJson.object().put("purpose","PDA_RESCUE_REQUEST_V1").put("requestKey",key)
            .put("deviceId",device.id()).put("operatorId",operator.identity().user().id().toString())
            .put("warehouseId",operator.identity().warehouse().toString()).put("rescuerId",recipient.user().id().toString())
            .put("reason","隔离预置：原操作员不在时仅取回指定原件");
        document.putArray("orderScope").addObject().put("source","TEMP").put("localOrderId",UUID.randomUUID().toString());
    }
    ObjectNode signed(ObjectNode doc) throws Exception {
        String original=SubmissionJson.canonical(doc);
        return SubmissionJson.object().put("originalText",original).put("signature",PdaTestDriver.sign(device,"PDA_RESCUE_REQUEST_V1\n"+original));
    }
    MockHttpServletRequestBuilder request(ObjectNode input,String requestKey) {
        return post("/api/v1/pda/rescue-requests").header("X-Server-Recovery-Epoch","1").header("Idempotency-Key",requestKey)
            .contentType("application/json").content(input.toString());
    }
    JsonNode create() throws Exception { return pda.data(request(signed(document),key),tokens); }
    MockHttpServletRequestBuilder approve(String id,ObjectNode input,String requestKey) {
        return post("/api/v1/rescue-requests/"+id+"/approve").header("Idempotency-Key",requestKey)
            .contentType("application/json").content(input.toString());
    }
    ObjectNode approval() { return SubmissionJson.object().putNull("expiresAt").put("reason","主管核对指定原件，仅授只读保全"); }
    JsonNode approve(String id) throws Exception { return supervisor.data(approve(id,approval(),UUID.randomUUID().toString())); }
    MockHttpServletRequestBuilder revoke(String id,ObjectNode input,String requestKey) {
        return post("/api/v1/rescue-grants/"+id+"/revoke").header("Idempotency-Key",requestKey)
            .contentType("application/json").content(input.toString());
    }
    JsonNode own(String id) throws Exception { return pda.data(get("/api/v1/pda/rescue-requests/"+id),tokens); }
    int rows(String table,String column,String id) {
        if(!java.util.Set.of("rescue_request","rescue_grant","rescue_revocation","rescue_request_scope").contains(table)
            || !java.util.Set.of("id","request_id","grant_id").contains(column)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Integer.class,Ids.bytes(UUID.fromString(id)));
    }
}
