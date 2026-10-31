package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

record PublicationDriver(MockMvc mvc, ObjectMapper json, IdentityFixture identity) {
    JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(send(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.param("warehouseId", identity.warehouse().toString()).header("X-Request-Id", UUID.randomUUID().toString())
            .with(user(identity.user())).with(csrf())
            .with(req -> { req.removeHeader("X-Server-Recovery-Epoch"); req.addHeader("X-Server-Recovery-Epoch", "1"); return req; }));
    }
    String stage(byte[] bytes) throws Exception {
        return data(multipart("/api/v1/inventory/imports").file(new MockMultipartFile("file", "sku.txt", "text/plain", bytes))
            .header("Idempotency-Key", UUID.randomUUID().toString())).get("id").asText();
    }
    JsonNode preview(String id) throws Exception { return data(get("/api/v1/inventory/imports/" + id + "/diff")); }
    ObjectNode decisions(String id) throws Exception {
        var p = preview(id);
        ObjectNode node = json.createObjectNode();
        node.set("expectedActiveSnapshot", p.at("/baseline/activeSnapshotId"));
        return node.put("expectedMappingRevision", p.at("/baseline/mappingRevision").asText()).put("acceptIdenticalDuplicates", true)
            .put("acceptMappingChanges", true).put("confirmedRemovedCount", p.get("removed").asInt()).put("reason", "测试核对");
    }
    MockHttpServletRequestBuilder publication(String id, ObjectNode body, String key) throws Exception {
        return post("/api/v1/inventory/imports/" + id + "/publish").contentType("application/json")
            .header("Idempotency-Key", key).content(json.writeValueAsBytes(body));
    }
    JsonNode publish(String id) throws Exception { return data(publication(id, decisions(id), UUID.randomUUID().toString())); }
    JsonNode revise(String code, String version, String model, boolean enabled) throws Exception {
        var body = json.createObjectNode().put("deliveryCode", code).put("expectedVersion", version).put("model", model)
            .put("name", "有光短纤").put("enabled", enabled).put("reason", "人工核对");
        return data(post("/api/v1/delivery-mappings/revisions").contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString()).content(json.writeValueAsBytes(body)));
    }
}
