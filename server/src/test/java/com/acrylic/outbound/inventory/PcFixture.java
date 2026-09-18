package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PcFixture {
    final PublicationDriver api;final OrderTestDriver orders;final String order;final String serial;final List<String> barcodes;
    String writer=UUID.randomUUID().toString();
    PcFixture(PublicationDriver api,boolean deduct,int plan,int perCode) throws Exception {
        this.api=api;orders=new OrderTestDriver(api);serial=unusedSerial(api);
        var rows=new ArrayList<byte[]>();barcodes=new ArrayList<>();
        for(String code:List.of("003","571","719")) for(int i=0;i<perCode;i++) {
            String barcode=code+String.format("%02d",i)+serial;barcodes.add(barcode);rows.add(SkuFixture.row(barcode,"300.501"));
        }
        api.publish(api.stage(SkuFixture.concat(rows.toArray(byte[][]::new))));orders.policy("0",deduct);
        var input=orders.plan();input.withArray("lines").removeAll();
        for(String code:List.of("003","571","719")) input.withArray("lines").addObject().put("deliveryCode",code).put("plannedCount",plan);
        order=orders.create(input).get("orderId").asText();
    }
    private static String unusedSerial(PublicationDriver api) {
        var context=Objects.requireNonNull(api.mvc().getDispatcherServlet().getWebApplicationContext());
        var jdbc=context.getBean(JdbcTemplate.class);
        for(int attempt=0;attempt<10;attempt++) {
            String candidate=UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase();
            int count=jdbc.queryForObject("SELECT COUNT(*) FROM bale WHERE barcode IN (?,?,?)",Integer.class,
                "00300"+candidate,"57100"+candidate,"71900"+candidate);
            if(count==0) return candidate;
        }
        throw new IllegalStateException("无法为隔离PC夹具分配未使用条码");
    }
    PcFixture(PcFixture previous) throws Exception {
        api=previous.api;orders=previous.orders;serial=previous.serial;barcodes=previous.barcodes;
        order=orders.create(orders.plan()).get("orderId").asText();
    }
    String path(String action) { return "/api/v1/orders/"+order+"/pc"+(action.isEmpty()?"":"/"+action); }
    JsonNode view() throws Exception { return api.data(get(path(""))); }
    ObjectNode startBody(String version) { return api.json().createObjectNode().put("expectedVersion",version).put("writerId",writer).put("policyVersion","1").put("reason","核对计划并开始PC作业"); }
    JsonNode start() throws Exception { return send("start",startBody("1")); }
    ObjectNode write(String barcode) throws Exception {
        var work=view().get("work");return api.json().createObjectNode().put("writerId",writer).put("fence",work.get("fence").asText())
            .put("expectedVersion",work.get("version").asText()).put("barcode",barcode).putNull("entryId").put("reason","").put("physicalConfirmed",true);
    }
    MockHttpServletRequestBuilder request(String action,ObjectNode body,String key) throws Exception {
        return post(path(action)).header("Idempotency-Key",key).contentType("application/json").content(api.json().writeValueAsBytes(body));
    }
    JsonNode send(String action,ObjectNode body) throws Exception { return api.data(request(action,body,UUID.randomUUID().toString())); }
    JsonNode add(int index) throws Exception { return send("entries",write(barcodes.get(index))); }
    JsonNode submit(String reason) throws Exception { return send("submit",write("").put("reason",reason)); }
    ObjectNode reviewBody(String decision) throws Exception {
        return api.json().createObjectNode().put("payloadSha256",view().at("/submission/payloadSha256").asText()).put("decision",decision).put("reason","主管已逐包核对实物");
    }
}
