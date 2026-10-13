package com.acrylic.outbound.relay;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.inventory.parser.SkuParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reception checks a public evidence scope. It does not certify scan completeness or business eligibility. */
public final class RelayIndependentOriginal {
    public static final Set<String> FILES=Set.of("independent-sku.bin","independent-source.json",
        "independent-voids.ndjson","independent-state.json");
    private static final Set<String> CREDENTIAL=Set.of("credentialId","operatorId","deviceId","warehouseId",
        "deviceKeySha256","serverRecoveryEpoch","userAuthVersion","deviceVersion","issuedAt","expiresAt","credentialSha256");
    private RelayIndependentOriginal() { }

    public static JsonNode read(Map<String,byte[]> files,JsonNode order,String keySha) {
        var present=new HashSet<>(files.keySet());present.retainAll(FILES);
        if(!"INDEPENDENT".equals(text(order,"source"))) {
            require(present.isEmpty(),"其他来源不能携带独立原件");return null;
        }
        equal(present,FILES,"独立原件成员不完整");
        for(String name:List.of("submission.json","submission-info.json","receipt.json","source-evidence.ndjson"))
            require(!files.containsKey(name),"独立证据不能补造正式冻结批次或回执");
        var source=parse(files.get("independent-source.json"));publicOnly(source);
        equal(text(source,"purpose"),"INDEPENDENT_READONLY_SOURCE_V1","独立原件用途不符");
        var task=source.path("task");var credential=source.path("credential");
        equal(fields(credential),CREDENTIAL,"独立原凭据公开字段不符");
        for(String field:List.of("operatorId","deviceId","warehouseId")) {
            equal(uuid(task,field),uuid(order,field),"独立任务范围不符");
            equal(uuid(credential,field),uuid(order,field),"原采集凭据范围不符");
        }
        equal(uuid(task,"id"),uuid(order,"localOrderId"),"原独立任务ID不符");
        equal(uuid(task,"id"),uuid(order,"orderId"),"独立原订单ID不符");
        equal(uuid(task,"credentialId"),uuid(credential,"credentialId"),"原采集凭据ID不符");
        equal(text(task,"recoveryEpoch"),text(credential,"serverRecoveryEpoch"),"原采集代次不符");
        equal(text(credential,"deviceKeySha256"),keySha,"独立采集与原封包设备钥不符");
        require(text(credential,"credentialSha256").matches("[0-9a-f]{64}"),"原凭据摘要无效");
        require(order.path("pendingReconciliation").isBoolean() && order.path("pendingReconciliation").booleanValue()
            && order.path("serverAuthorizationVerified").isBoolean() && !order.path("serverAuthorizationVerified").booleanValue(),"独立原件必须待对账");
        window(credential);sku(files,source);return source;
    }
    private static void sku(Map<String,byte[]> files,JsonNode source) {
        var task=source.path("task");var imported=source.path("import");var decision=source.path("decision");
        equal(ImportFiles.sha256(files.get("independent-sku.bin")),text(task,"skuSha256"),"原独立SKU摘要不符");
        equal(text(imported,"sha256"),text(task,"skuSha256"),"原导入摘要不符");
        equal(uuid(imported,"id"),uuid(task,"importId"),"原导入ID不符");
        equal(uuid(decision,"importId"),uuid(task,"importId"),"原启用决定不符");
        equal(text(decision,"outcome"),"ACTIVATED","原资料未启用");
        var preview=new SkuParser().parse(files.get("independent-sku.bin"));require(preview.errorRows()==0,"原资料存在阻断行");
        require(preview.duplicateRows()==0 || text(decision,"duplicatesConfirmed").equals("1"),"原重复资料未明确确认");
    }
    private static void window(JsonNode credential) {
        try {
            var duration=Duration.between(Instant.parse(text(credential,"issuedAt")),Instant.parse(text(credential,"expiresAt")));
            require(!duration.isNegative() && !duration.isZero() && duration.compareTo(Duration.ofHours(48))<=0,"原采集期限无效");
        } catch(DateTimeException error) { throw bad("原采集时间格式无效"); }
    }
    private static Set<String> fields(JsonNode node) {
        require(node.isObject(),"原凭据必须为对象");var fields=new HashSet<String>();node.fieldNames().forEachRemaining(fields::add);return fields;
    }
    private static void publicOnly(JsonNode source) {
        equal(fields(source),Set.of("purpose","task","import","decision","credential","timeAnchor"),"独立公开原件字段不符");
        exact(source.path("task"),"id importId skuSha256 credentialId operatorId deviceId warehouseId recoveryEpoch vehicleNo reason plannedTotal commandSha256 createdWall createdElapsed createdBoot createdTrustedAt");
        exact(source.path("import"),"id sha256 displayName selectedWall selectedElapsed selectedBoot");
        exact(source.path("decision"),"importId outcome previousImportId duplicatesConfirmed wall elapsed boot");
        exact(source.path("timeAnchor"),"server wall elapsed boot");
    }
    private static void exact(JsonNode node,String names) {
        equal(fields(node),Set.of(names.split(" ")),"独立公开原件字段不符");
        node.forEach(value->require(value.isTextual()||value.isNull(),"独立公开原件只能包含公开字符串"));
    }
}
