package com.acrylic.outbound.exports;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.exports.ExportFacts.check;

final class ExportSourceJson {
    static Map<String,JsonNode> index(JsonNode body,String array,String key) {
        var result=new LinkedHashMap<String,JsonNode>();
        for(var row:array(body,array)) check(result.put(text(row,key),row)==null,"冻结记录ID重复");
        return result;
    }
    static JsonNode required(Map<String,JsonNode> rows,String key) {
        var row=rows.get(key);check(row!=null,"出库事实缺少对应冻结原文");return row;
    }
    static void match(ExportFacts.Dispatch d,JsonNode row,boolean pc) {
        check(text(row,"baleId").equals(d.bale()) && text(row,"deliveryCode").equals(d.code())
                && text(row,"weightGrams").equals(d.weight()) && text(row,"rawWeight").equals(d.rawWeight()),"冻结包身份或重量与确认事实不一致");
        check(Long.parseLong(text(row,pc?"sequence":"orderScanSeq"))==d.sequence(),"冻结顺序与确认顺序不一致");
        long cycle=pc?row.path("cycle").longValue():number(row,"expectedDispatchCycle");
        check(cycle==d.cycle() && text(row,"status").equals("ACTIVE"),"冻结出库周期或有效状态不一致");
    }
    static String optional(JsonNode row,String field) { return row.path(field).isTextual()?row.path(field).textValue():""; }
    static String wall(JsonNode row) { return Instant.ofEpochMilli(number(row,"receivedWallMs")).toString(); }
    private ExportSourceJson() { }
}
