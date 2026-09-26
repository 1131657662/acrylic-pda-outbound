package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.reports.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportFacts.check;
import static com.acrylic.outbound.exports.ExportSourceJson.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporaryExportSource {
    private final ExportFacts facts;private final TemporaryExportEvidence evidence;
    private final TemporaryExportPlan plans;private final ExportOperatorNames names;
    public TemporaryExportSource(ExportFacts facts,TemporaryExportEvidence evidence,TemporaryExportPlan plans,ExportOperatorNames names) {
        this.facts=facts;this.evidence=evidence;this.plans=plans;this.names=names;
    }
    public ConfirmedV1Input.Order read(ExportFacts.Head head,boolean scanRecords,Instant cutoff) {
        try { return checked(head,scanRecords,cutoff); }
        catch(BusinessException error) {
            if(error.code().startsWith("EXPORT_"))throw error;
            throw unavailable();
        } catch(RuntimeException error) { throw unavailable(); }
    }
    private ConfirmedV1Input.Order checked(ExportFacts.Head head,boolean scanRecords,Instant cutoff) {
        var original=evidence.read(head,cutoff);var body=original.body();
        int revision=Math.toIntExact(number(body,"orderRevision"));var plan=facts.plan(head.order(),revision);
        var products=facts.products(plan);plans.validate(original,plan,products);
        var captures=index(body,"captures","id");var scans=index(body,"scans","captureId");
        var records=facts.dispatches(head);var operators=new HashSet<String>();
        for(var d:records)operators.add(text(required(captures,d.capture()),"operatorId").toLowerCase(Locale.ROOT));
        var displayNames=scanRecords?names.resolve(operators):Map.<String,String>of();
        var dispatches=new ArrayList<ConfirmedV1Input.Dispatch>();
        for(var d:records)dispatches.add(line(d,required(scans,d.capture()),required(captures,d.capture()),displayNames));
        long active=scans.values().stream().filter(s->text(s,"status").equals("ACTIVE")).count();
        check(active==dispatches.size(),"TEMP有效冻结行与确认事实数量不符");
        String reference="dispatch_record.order_scan_seq\nTEMP:"+text(body.path("source"),"creatorDeviceId")+":"
                +text(body.path("source"),"clientOrderId")+"\nauthorization:"+text(body,"authorizationId")
                +"\nconfirmation:"+original.confirmationId()+"\nsha256:"+original.confirmationHash()+"\n";
        return new ConfirmedV1Input.Order(head.order().toString(),head.number(),revision,plan.header(),head.confirmedAt(),
                ConfirmedV1Input.Channel.TEMP_PDA,reference,products,dispatches,facts.result(head),head.receipt(),exceptions(body));
    }
    private ConfirmedV1Input.Dispatch line(ExportFacts.Dispatch d,JsonNode scan,JsonNode capture,Map<String,String> names) {
        match(d,scan,false);check(number(capture,"orderScanSeq")==d.sequence(),"TEMP原采集顺序不符");
        String id=text(capture,"operatorId"),operator=names.getOrDefault(id.toLowerCase(Locale.ROOT),"原人员ID:"+id);
        var line=new ReportLine(d.id(),d.capture(),d.sequence(),text(capture,"originDeviceId"),number(capture,"deviceCaptureSeq"),
                operator,text(capture,"inputSource"),text(scan,"barcode"),d.cycle(),d.code(),d.weight(),d.rawWeight(),wall(capture),
                text(scan,"savedAt"),"DEVICE_UNVERIFIED",names.isEmpty()?"":"姓名来源：导出申请时人员资料；原人员ID:"+id);
        return new ConfirmedV1Input.Dispatch(d.order(),d.submission(),line);
    }
    private List<OrderSnapshot.ExceptionRow> exceptions(JsonNode body) {
        var result=new ArrayList<OrderSnapshot.ExceptionRow>();var scans=index(body,"scans","captureId");
        for(var c:array(body,"captures")) {
            var scan=scans.get(text(c,"id"));String kind=scan==null?text(c,"state"):text(scan,"status");
            if(kind.equals("ACTIVE"))continue;
            result.add(new OrderSnapshot.ExceptionRow(text(c,"id"),number(c,"orderScanSeq"),text(c,"originDeviceId"),kind,
                    scan==null?optional(c,"barcode"):text(scan,"barcode"),optional(c,"error"),wall(c)));
        }
        return result;
    }
    private BusinessException unavailable() {
        return new BusinessException(409,"EXPORT_SOURCE_UNAVAILABLE","临时来源原件不完整或不一致，无法导出");
    }
}
