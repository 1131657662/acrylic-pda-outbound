package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

record UserManagementDriver(PublicationDriver web) {
    ObjectNode createBody() {
        var body=web.json().createObjectNode().put("username","managed-"+UUID.randomUUID()).put("displayName","联调人员")
            .put("password","Test-password-123").put("reason","新增测试人员");
        body.putArray("roles").add("PDA_OPERATOR");body.putArray("warehouseIds").add(web.identity().warehouse().toString());return body;
    }
    ObjectNode updateBody(JsonNode item) {
        var body=web.json().createObjectNode().put("expectedVersion",item.get("rowVersion").asText())
            .put("displayName",item.get("displayName").asText()).put("status",item.get("status").asText()).put("reason","核对人员权限后调整");
        body.set("roles",item.get("roles"));var warehouses=body.putArray("warehouseIds");
        item.get("warehouses").forEach(w->warehouses.add(w.get("id").asText()));return body;
    }
    MockHttpServletRequestBuilder command(String suffix,JsonNode body,String key) throws Exception {
        return post("/api/v1/users"+suffix).header("Idempotency-Key",key).contentType("application/json").content(web.json().writeValueAsBytes(body));
    }
    JsonNode create(ObjectNode body) throws Exception { return web.data(command("",body,UUID.randomUUID().toString())); }
    JsonNode item(String id) throws Exception { return web.data(get("/api/v1/users/"+id)); }
}
