package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

record OrderTestDriver(PublicationDriver api) {
    ObjectNode plan() throws Exception {
        var baseline=api.data(get("/api/v1/inventory/current"));
        var node=api.json().createObjectNode().put("vehicleNo","苏A-测试车").put("containerNo","").put("notes","不同货堆装车");
        node.set("expectedActiveSnapshot",baseline.get("activeSnapshotId"));
        node.set("expectedMappingRevision",baseline.get("mappingRevision"));
        node.putArray("lines").addObject().put("deliveryCode","003").put("plannedCount",180);
        node.withArray("lines").addObject().put("deliveryCode","571").put("plannedCount",181);
        return node;
    }
    void stock() throws Exception {
        api.publish(api.stage(SkuFixture.concat(SkuFixture.row("003B0051601","300.5"),SkuFixture.row("571B0051602","301"))));
    }
    JsonNode create(ObjectNode plan) throws Exception { return api.data(command("",plan,UUID.randomUUID().toString())); }
    JsonNode detail(String id) throws Exception { return api.data(get("/api/v1/orders/"+id)); }
    MockHttpServletRequestBuilder command(String path,ObjectNode body,String key) throws Exception {
        return post("/api/v1/orders"+path).contentType("application/json").header("Idempotency-Key",key)
            .content(api.json().writeValueAsBytes(body));
    }
    ObjectNode revision(ObjectNode plan,String version) {
        var body=api.json().createObjectNode().put("expectedVersion",version).put("reason","核对后调整计划");
        body.set("plan",plan);return body;
    }
    ObjectNode cancellation(String version) { return api.json().createObjectNode().put("expectedVersion",version).put("reason","车辆取消"); }
    ObjectNode release(String version,String policy) {
        return cancellation(version).put("expectedPolicyVersion",policy).put("reason","确认计划并发布");
    }
    JsonNode policy(String version,boolean deduct) throws Exception {
        var body=api.json().createObjectNode().put("expectedVersion",version).put("autoDeductInventory",deduct).put("reason","核实后设置扣账");
        return api.data(post("/api/v1/policies").contentType("application/json").header("Idempotency-Key",UUID.randomUUID().toString())
            .content(api.json().writeValueAsBytes(body)));
    }
}
