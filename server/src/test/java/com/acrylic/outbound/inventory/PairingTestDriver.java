package com.acrylic.outbound.inventory;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

record PairingTestDriver(PublicationDriver web,KeyPair key,ObjectNode payload,String remote) {
    static PairingTestDriver fresh(PublicationDriver web) throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var key=generator.generateKeyPair();String id=UUID.randomUUID().toString();
        var p=web.json().createObjectNode().put("applicationId",id).put("credential",DeviceCrypto.secret())
            .put("publicKey",Base64.getEncoder().encodeToString(key.getPublic().getEncoded())).put("model","Pairing test PDA").put("approvalSha256","");
        return new PairingTestDriver(web,key,p,"pairing-test-"+id);
    }
    String id() { return payload.get("applicationId").asText(); }
    MockHttpServletRequestBuilder postBody(String path,JsonNode body) throws Exception {
        return post(path).contentType("application/json").content(web.json().writeValueAsBytes(body)).with(r->{r.setRemoteAddr(remote);return r;});
    }
    ObjectNode proof(String action) throws Exception {
        String binding=id()+"\n"+payload.get("publicKey").asText()+"\n"+payload.get("credential").asText()+"\n"+
            payload.get("model").asText()+"\n"+payload.get("approvalSha256").asText();
        var request=web.json().createObjectNode().put("applicationId",id()).put("publicKey",payload.get("publicKey").asText())
            .put("purpose",action).put("bindingSha256",DeviceCrypto.sha(binding));
        var challenge=data(postBody("/api/v1/pda/pairing/challenges",request));
        var body=web.json().createObjectNode().put("challengeId",challenge.get("challengeId").asText())
            .put("signature",PdaTestDriver.sign(new PdaTestDriver.Device(id(),key),challenge.get("signedText").asText()));
        body.set("payload",payload.deepCopy());return body;
    }
    MockHttpServletRequestBuilder command(String action,ObjectNode body) throws Exception {
        return postBody("/api/v1/pda/pairing/"+(action.equals("CREATE")?"applications":action.toLowerCase(Locale.ROOT)),body);
    }
    JsonNode call(String action) throws Exception { return data(command(action,proof(action))); }
    JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        return web.json().readTree(web.mvc().perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    ObjectNode approvalBody() { return web.json().createObjectNode().put("deviceCode","P-"+id()).put("name","短码登记设备"); }
    MockHttpServletRequestBuilder approval(ObjectNode body,String requestKey) throws Exception {
        return postBody("/api/v1/device-pairings/"+id()+"/approval",body).header("Idempotency-Key",requestKey);
    }
    JsonNode approve() throws Exception {
        var result=web.data(approval(approvalBody(),UUID.randomUUID().toString()));
        payload.put("approvalSha256",result.get("approvalSha256").asText());return result;
    }
}
