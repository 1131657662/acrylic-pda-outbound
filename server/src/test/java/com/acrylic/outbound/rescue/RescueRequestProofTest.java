package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RescueRequestProofTest {
    private final ObjectMapper json=new ObjectMapper();private final RescueRequestProof proof=new RescueRequestProof(json);
    private KeyPair key;private DeviceStore.Device device;private PdaSessions.Principal actor;private ObjectNode document;
    private final String requestKey=UUID.randomUUID().toString();
    @BeforeEach void input() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));key=generator.generateKeyPair();
        UUID id=UUID.randomUUID(),user=UUID.randomUUID(),warehouse=UUID.randomUUID();
        device=new DeviceStore.Device(id,warehouse,"synthetic","synthetic",Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),
            "unused","ACTIVE","1","2026-10-12T00:00:00Z",null,false);
        actor=new PdaSessions.Principal(UUID.randomUUID(),user,"synthetic",id,warehouse,"1","1");
        document=json.createObjectNode().put("purpose","PDA_RESCUE_REQUEST_V1").put("requestKey",requestKey)
            .put("deviceId",id.toString()).put("operatorId",user.toString()).put("warehouseId",warehouse.toString())
            .put("rescuerId",UUID.randomUUID().toString()).put("reason","预授权原件保全");
        document.putArray("orderScope").addObject().put("source","TEMP").put("localOrderId",UUID.randomUUID().toString());
    }
    private RescueCommands.Request signed(String text) throws Exception {
        var signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key.getPrivate());
        signer.update(("PDA_RESCUE_REQUEST_V1\n"+text).getBytes(StandardCharsets.UTF_8));
        return new RescueCommands.Request(text,Base64.getEncoder().encodeToString(signer.sign()));
    }
    @Test void acceptsExactSignedScopeWithoutCreatingWorkCapabilities() throws Exception {
        var verified=proof.verify(signed(document.toString()),actor,device,requestKey);
        assertThat(verified.deviceId()).isEqualTo(actor.deviceId());assertThat(verified.orderScope()).hasSize(1);
        assertThat(verified.orderScope().getFirst().source()).isEqualTo("TEMP");
    }
    @Test void changedOriginalBytesCannotUseTheSignature() throws Exception {
        var input=signed(document.toString());var tampered=new RescueCommands.Request(document.deepCopy().put("reason","异文").toString(),input.signature());
        assertThatThrownBy(()->proof.verify(tampered,actor,device,requestKey)).isInstanceOf(BusinessException.class)
            .extracting("code").isEqualTo("INVALID_DEVICE_PROOF");
    }
    @ParameterizedTest @ValueSource(strings={"purpose","deviceId","operatorId","warehouseId","requestKey"})
    void refusesSignedDifferentIdentityOrPurpose(String field) throws Exception {
        document.put(field,UUID.randomUUID().toString());var input=signed(document.toString());
        assertThatThrownBy(()->proof.verify(input,actor,device,requestKey)).isInstanceOf(BusinessException.class);
    }
    @Test void refusesOwnIdentityAsRescuer() throws Exception {
        document.put("rescuerId",actor.userId().toString());var input=signed(document.toString());
        assertThatThrownBy(()->proof.verify(input,actor,device,requestKey)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","empty","newSource","missingId","uppercaseId","extraCapability"})
    void refusesScopeThatIsAmbiguousOrExpandsCapabilities(String kind) throws Exception {
        var scope=document.withArray("orderScope");var row=(ObjectNode)scope.get(0);
        switch(kind) {
            case "duplicate" -> scope.add(row.deepCopy());
            case "empty" -> scope.removeAll();
            case "newSource" -> row.put("source","ALL");
            case "missingId" -> row.remove("localOrderId");
            case "uppercaseId" -> row.put("localOrderId","AAAAAAAA-AAAA-4AAA-AAAA-AAAAAAAAAAAA");
            case "extraCapability" -> document.put("maySubmit",true);
            default -> throw new IllegalArgumentException();
        }
        var input=signed(document.toString());
        assertThatThrownBy(()->proof.verify(input,actor,device,requestKey)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"duplicateField","trailingDocument","blankReason","largeScope"})
    void refusesMalformedButCorrectlySignedOriginal(String kind) throws Exception {
        String text=document.toString();
        if(kind.equals("duplicateField")) text=text.replace("{\"purpose\":","{\"reason\":\"duplicated\",\"purpose\":");
        if(kind.equals("trailingDocument")) text+="{}";
        if(kind.equals("blankReason")) text=document.put("reason"," ").toString();
        if(kind.equals("largeScope")) {
            for(int i=0;i<100;i++) document.withArray("orderScope").addObject().put("source","TEMP").put("localOrderId",UUID.randomUUID().toString());
            text=document.toString();
        }
        var input=signed(text);
        assertThatThrownBy(()->proof.verify(input,actor,device,requestKey)).isInstanceOf(BusinessException.class);
    }
}
