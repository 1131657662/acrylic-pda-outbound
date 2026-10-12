package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RescueRequestProof {
    private final ObjectMapper mapper;
    public RescueRequestProof(ObjectMapper mapper) {
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public RescueCommands.Document verify(RescueCommands.Request input,PdaSessions.Principal actor,
        DeviceStore.Device device,String requestKey) {
        if(input==null || input.originalText()==null || input.signature()==null || input.signature().length()>256 ||
            input.originalText().getBytes(StandardCharsets.UTF_8).length>32768) throw invalid();
        DeviceCrypto.verify(device.publicKey(),"PDA_RESCUE_REQUEST_V1\n"+input.originalText(),input.signature());
        var root=parse(input.originalText());
        if(!fields(root).equals(Set.of("purpose","requestKey","deviceId","operatorId","warehouseId","rescuerId","orderScope","reason"))) throw invalid();
        for(var field:List.of("deviceId","operatorId","warehouseId","rescuerId")) canonicalUuid(root.get(field));
        for(var field:List.of("purpose","requestKey","reason")) if(!root.get(field).isTextual()) throw invalid();
        scope(root);
        var document=convert(root);
        if(!"PDA_RESCUE_REQUEST_V1".equals(document.purpose()) || !requestKey.equals(document.requestKey()) ||
            !actor.deviceId().equals(document.deviceId()) || !actor.userId().equals(document.operatorId()) ||
            !actor.warehouseId().equals(document.warehouseId()) || document.rescuerId()==null || actor.userId().equals(document.rescuerId())) throw invalid();
        if(document.reason()==null || document.reason().isBlank() || document.reason().length()>1000) throw invalid();
        return document;
    }
    private void scope(JsonNode root) {
        var scope=root.get("orderScope");var targets=new HashSet<String>();
        if(!scope.isArray() || scope.isEmpty() || scope.size()>100) throw invalid();
        for(var row:scope) {
            if(!fields(row).equals(Set.of("source","localOrderId"))) throw invalid();
            canonicalUuid(row.get("localOrderId"));
            var source=row.get("source");
            if(!source.isTextual() || !Set.of("FORMAL","TEMP").contains(source.textValue()) ||
                !targets.add(source.textValue()+":"+row.get("localOrderId").textValue())) throw invalid();
        }
    }
    private void canonicalUuid(JsonNode value) {
        if(value==null || !value.isTextual()) throw invalid();
        try { if(!UUID.fromString(value.textValue()).toString().equals(value.textValue())) throw invalid(); }
        catch(IllegalArgumentException error) { throw invalid(); }
    }
    private Set<String> fields(JsonNode value) {
        if(value==null || !value.isObject()) throw invalid();
        var result=new HashSet<String>();value.fieldNames().forEachRemaining(result::add);return result;
    }
    private JsonNode parse(String text) {
        try { return mapper.readTree(text); } catch(Exception error) { throw invalid(); }
    }
    private RescueCommands.Document convert(JsonNode value) {
        try { return mapper.treeToValue(value,RescueCommands.Document.class); } catch(Exception error) { throw invalid(); }
    }
    private BusinessException invalid() { return new BusinessException(400,"INVALID_RESCUE_SCOPE","救援申请范围、身份或原件格式无效"); }
}
