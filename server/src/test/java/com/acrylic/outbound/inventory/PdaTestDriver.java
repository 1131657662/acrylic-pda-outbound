package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

record PdaTestDriver(PublicationDriver web) {
    Device register() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var key=generator.generateKeyPair();
        var body=web.json().createObjectNode().put("deviceCode",UUID.randomUUID().toString()).put("name","测试PDA")
            .put("publicKey",Base64.getEncoder().encodeToString(key.getPublic().getEncoded()));
        var data=web.data(post("/api/v1/devices").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(web.json().writeValueAsBytes(body)));
        return new Device(data.get("deviceId").asText(),key);
    }
    ObjectNode loginBody(Device device) throws Exception {
        String username=web.identity().user().username();var challenge=challenge(device,"LOGIN",username);
        return web.json().createObjectNode().put("username",username).put("password","Test-password-123")
            .put("deviceId",device.id()).put("challengeId",challenge.get("challengeId").asText())
            .put("signature",sign(device,challenge.get("signedText").asText()));
    }
    JsonNode login(Device device) throws Exception { return data(command("/auth/login",loginBody(device))); }
    JsonNode challenge(Device device,String purpose,String binding) throws Exception {
        var body=web.json().createObjectNode().put("deviceId",device.id()).put("purpose",purpose).put("bindingSha256",sha(binding));
        return data(command("/auth/challenges",body));
    }
    ObjectNode refreshBody(Device device,String refresh) throws Exception {
        var challenge=challenge(device,"REFRESH",refresh);
        return web.json().createObjectNode().put("deviceId",device.id()).put("refreshToken",refresh)
            .put("challengeId",challenge.get("challengeId").asText()).put("signature",sign(device,challenge.get("signedText").asText()));
    }
    MockHttpServletRequestBuilder command(String path,ObjectNode body) throws Exception {
        return post("/api/v1/pda"+path).contentType("application/json").content(web.json().writeValueAsBytes(body));
    }
    MockHttpServletRequestBuilder claim(String order,String key) throws Exception {
        return command("/orders/"+order+"/assignments",claimBody()).header("Idempotency-Key",key).header("X-Server-Recovery-Epoch","1");
    }
    ObjectNode claimBody() { return web.json().createObjectNode().put("expectedVersion","2").put("areaNote","东侧第一堆"); }
    ResultActions send(MockHttpServletRequestBuilder request,JsonNode tokens) throws Exception {
        return web.mvc().perform(request.header("Authorization","Bearer "+tokens.get("accessToken").asText()));
    }
    JsonNode data(MockHttpServletRequestBuilder request,JsonNode tokens) throws Exception {
        return web.json().readTree(send(request,tokens).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        return web.json().readTree(web.mvc().perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    String readyOrder() throws Exception {
        String serial=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        web.publish(web.stage(SkuFixture.concat(SkuFixture.row("003X"+serial,"300.5"),SkuFixture.row("571X"+serial,"301"))));
        var orders=new OrderTestDriver(web);orders.policy("0",false);String id=orders.create(orders.plan()).get("orderId").asText();
        web.data(orders.command("/"+id+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));return id;
    }
    JsonNode setStatus(Device device,String version,String value) throws Exception {
        var body=web.json().createObjectNode().put("expectedVersion",version).put("status",value).put("reason","测试设备状态");
        return web.data(post("/api/v1/devices/"+device.id()+"/status").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(web.json().writeValueAsBytes(body)));
    }
    static String sign(Device device,String text) throws Exception {
        var signer=Signature.getInstance("SHA256withECDSA");signer.initSign(device.key().getPrivate());
        signer.update(text.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(signer.sign());
    }
    static String sha(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    record Device(String id,KeyPair key) { }
}
