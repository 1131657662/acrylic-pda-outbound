package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

final class TemporaryDuplicateFixture {
    final TemporaryReviewFixture review;final OrderTestDriver orders;final JdbcTemplate jdbc;
    TemporaryDuplicateFixture(JdbcTemplate jdbc,MockMvc mvc,ObjectMapper json,PasswordEncoder passwords) throws Exception {
        this.jdbc=jdbc;review=new TemporaryReviewFixture(jdbc,mvc,json,passwords);orders=new OrderTestDriver(review.temporary.f.web);
        review.temporary.submit();
        jdbc.update("UPDATE outbound_order SET created_at='2001-01-01 00:00:00' WHERE id=UUID_TO_BIN(?)",review.temporary.f.order);
    }
    String create(boolean ready) throws Exception {
        String id=orders.create(plan()).path("orderId").asText();if(ready) publish(id);return id;
    }
    ObjectNode plan() throws Exception {
        var plan=orders.plan().put("vehicleNo",review.temporary.body.at("/temporaryOrder/vehicleNo").asText());
        plan.withArray("lines").forEach(l->((ObjectNode)l).put("plannedCount",1));return plan;
    }
    void publish(String id) throws Exception { orders.api().data(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString())); }
    String path() { return review.path()+"/duplicate-candidates"; }
    MockHttpServletRequestBuilder request() { return get(path()); }
    JsonNode page() throws Exception { return review.reviewer.data(request()); }
    JsonNode page(String cursor) throws Exception { return review.reviewer.data(request().param("limit","1").param("cursor",cursor)); }
    String facts() {
        return review.facts()+jdbc.queryForList("SELECT HEX(id),HEX(current_submission_id),work_round,HEX(canonical_order_id) FROM submission_scope ORDER BY id")
            +jdbc.queryForList("SELECT HEX(submission_id),payload_sha256,file_key,byte_count FROM submission_payload ORDER BY submission_id")
            +jdbc.queryForList("SELECT HEX(order_id),HEX(scope_id),work_round,HEX(current_submission_id),phase FROM order_submission_slot ORDER BY order_id");
    }
    JsonNode candidate(JsonNode response,String id) {
        for(var row:response.path("items"))if(row.at("/order/id").asText().equals(id))return row;
        throw new AssertionError("candidate missing: "+id);
    }
}
