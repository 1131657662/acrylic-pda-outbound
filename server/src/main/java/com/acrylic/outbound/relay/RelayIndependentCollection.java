package com.acrylic.outbound.relay;

import com.acrylic.outbound.inventory.parser.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reconcile the sealed public collection with its fixed SKU and original authorization window. */
final class RelayIndependentCollection {
    private RelayIndependentCollection() { }
    static void verify(RelayIndependentClosure.Rows rows,JsonNode source,byte[] sku) {
        var originals=new SkuParser().parse(sku).rows().stream().filter(row->row.errorCode()==null).toList();
        var catalog=new HashMap<String,ImportRow>();originals.forEach(row->catalog.put(row.parsedFields().barcode(),row));
        var captures=index(rows.captures(),"id");index(rows.scans(),"captureId");index(rows.events(),"id");
        captures(rows.captures(),source);scans(rows,source,catalog);
        plans(rows,source,catalog);audits(rows,source);voids(rows.voids(),source);
    }
    private static void captures(List<JsonNode> rows,JsonNode source) {
        long previous=0;String device=text(source.path("task"),"deviceId");
        for(var row:rows) {
            equal(text(row,"deviceId"),device,"结束输入原设备不符");
            equal(text(row,"sequence"),text(row,"orderScanSeq"),"结束输入原序不符");
            equal(text(row,"deviceSequence"),text(row,"deviceCaptureSeq"),"结束设备输入序不符");
            long sequence=number(row,"deviceSequence");require(sequence>previous,"结束设备输入序不递增");previous=sequence;
            require(Set.of("MANUAL","BROADCAST").contains(text(row,"inputSource")),"结束输入来源不符");
            equal(text(row,"rawInput").trim().toUpperCase(Locale.ROOT),text(row,"barcode"),"结束原输入不符");
            equal(anchored(row,source),at(text(row,"capturedTrustedAt"),source),"结束原采集时间不符");
        }
    }
    private static void scans(RelayIndependentClosure.Rows rows,JsonNode source,Map<String,ImportRow> sku) {
        var captures=index(rows.captures(),"id");long previous=0;
        for(var scan:rows.scans()) {
            var original=sku.get(text(scan,"barcode"));require(original!=null,"结束原扫码不在固定SKU");var bale=original.parsedFields();
            var capture=captures.get(text(scan,"captureId"));require(capture!=null,"结束原扫码缺输入");
            for(String field:List.of("taskId","sequence","barcode"))equal(text(scan,field),text(capture,field),"结束原扫码与输入不符");
            equal(text(scan,"sequence"),text(scan,"orderScanSeq"),"结束原扫码序不符");
            long sequence=number(scan,"sequence");require(sequence>previous,"结束原扫码序不递增");previous=sequence;
            for(var field:Map.of("deliveryCode",bale.deliveryCode(),"model",bale.model(),"name",bale.name(),
                    "rawWeight",bale.rawWeight(),"weightGrams",bale.weightGrams(),"skuRawText",original.rawText(),
                    "skuLineNo",Integer.toString(original.lineNo())).entrySet())equal(text(scan,field.getKey()),field.getValue(),"结束原扫码SKU字段不符");
            require(!capture.path("confirmedAt").isNull(),"结束原扫码缺确认时间");
            var confirmed=at(text(capture,"confirmedAt"),source);var saved=at(text(scan,"savedAt"),source);
            require(!confirmed.isBefore(at(text(capture,"capturedTrustedAt"),source)) && !saved.isBefore(confirmed),"结束原扫码时间倒置");
            equal(text(scan,"savedAt"),text(scan,"savedTrustedAt"),"结束原保存时间不符");
        }
    }
    private static void plans(RelayIndependentClosure.Rows rows,JsonNode source,Map<String,ImportRow> sku) {
        var products=new HashMap<String,ParsedBale>();sku.values().forEach(row->products.put(row.parsedFields().deliveryCode(),row.parsedFields()));
        var plans=index(rows.lines(),"deliveryCode");require(plans.size()>=1 && plans.size()<=200,"结束计划产品数无效");long total=0;
        for(var line:rows.lines()) {
            var product=products.get(text(line,"deliveryCode"));require(product!=null,"结束计划不在固定SKU");
            equal(text(line,"model"),product.model(),"结束计划型号不符");equal(text(line,"name"),product.name(),"结束计划品名不符");
            long count=number(line,"plannedCount");require(count>=1 && count<=100000,"结束计划包数无效");total=Math.addExact(total,count);
            require(rows.scans().stream().filter(row->text(row,"deliveryCode").equals(product.deliveryCode()) && text(row,"status").equals("ACTIVE")).count()<=count,"结束产品超计划");
        }
        require(total<=100000,"结束整单计划超限");equal(total,number(source.path("task"),"plannedTotal"),"结束整单计划不符");
        require(rows.scans().stream().allMatch(row->plans.containsKey(text(row,"deliveryCode"))),"结束原扫码不在计划");
    }
    private static void audits(RelayIndependentClosure.Rows rows,JsonNode source) {
        var allowed=Set.of("CREATE_PLANNED","INDEPENDENT_STARTED","INDEPENDENT_RECEIVED","INDEPENDENT_WAITING_CONFIRM",
            "INDEPENDENT_CONFIRM_REQUESTED","INDEPENDENT_INVALID","INDEPENDENT_SAVED","INDEPENDENT_VOID",
            "INDEPENDENT_BUSINESS_REJECTED","INDEPENDENT_FAILURE_ACKNOWLEDGED","INDEPENDENT_DISCARDED","INDEPENDENT_CLOSED");
        for(var row:rows.events()) {
            require(allowed.contains(text(row,"type")),"结束原审计类型无效");
            equal(text(row,"sequence"),text(row,"orderPosition"),"结束原审计序不符");anchored(row,source);
        }
        long started=rows.events().stream().filter(row->text(row,"type").equals("INDEPENDENT_STARTED")).count();
        require(started<=1 && (rows.captures().isEmpty() || started==1),"结束缺原开始采集事件");
        var task=source.path("task");var created=object().put("wall",text(task,"createdWall"))
            .put("elapsed",text(task,"createdElapsed")).put("boot",text(task,"createdBoot"));
        equal(anchored(created,source),at(text(task,"createdTrustedAt"),source),"结束原创建时间不符");
    }
    private static void voids(List<JsonNode> rows,JsonNode source) {
        index(rows,"captureId");
        for(var row:rows) {
            String reason=text(row,"reason");require(!reason.isBlank() && reason.length()<=500,"结束原VOID原因无效");
            var delay=Duration.between(anchored(row,source),at(text(row,"authorizedAt"),source));
            require(!delay.isNegative() && delay.compareTo(Duration.ofMinutes(2))<=0,"结束原VOID授权时间不符");
        }
    }
    private static Instant anchored(JsonNode row,JsonNode source) {
        var anchor=source.path("timeAnchor");long delta=number(row,"elapsed")-number(anchor,"elapsed");
        require(delta>=0 && text(row,"boot").equals(text(anchor,"boot")),"结束原事实启动代次不可信");
        var skew=java.math.BigInteger.valueOf(number(row,"wall")).subtract(java.math.BigInteger.valueOf(number(anchor,"wall")))
            .subtract(java.math.BigInteger.valueOf(delta)).abs();require(skew.compareTo(java.math.BigInteger.valueOf(120000))<=0,"结束原事实采样不可信");
        try { return at(Instant.parse(text(anchor,"server")).plusMillis(delta).toString(),source); }
        catch(DateTimeException error) { throw bad("结束原事实时间无效"); }
    }
    private static Instant at(String value,JsonNode source) {
        try {
            var stamp=Instant.parse(value);var credential=source.path("credential");
            require(!stamp.isBefore(Instant.parse(text(credential,"issuedAt"))) && stamp.isBefore(Instant.parse(text(credential,"expiresAt"))),"结束原事实超授权期限");return stamp;
        } catch(DateTimeException error) { throw bad("结束原事实时间无效"); }
    }
    private static Map<String,JsonNode> index(List<JsonNode> rows,String field) {
        var result=new HashMap<String,JsonNode>();for(var row:rows)require(result.put(text(row,field),row)==null,"结束原事实ID重复");return result;
    }
}
