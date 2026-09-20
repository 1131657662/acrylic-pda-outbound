package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.exports.ExportSourceJson.*;
import static com.acrylic.outbound.exports.ExportFacts.check;

@Component
public class PdaExportSource {
    private final JdbcTemplate jdbc;private final ExportFacts facts;private final SubmissionFiles files;private final ExportOperatorNames names;
    public PdaExportSource(JdbcTemplate jdbc,ExportFacts facts,SubmissionFiles files,ExportOperatorNames names) {
        this.jdbc=jdbc;this.facts=facts;this.files=files;this.names=names;
    }
    public ConfirmedV1Input.Order read(ExportFacts.Head head,boolean scanRecords) {
        var frozen=jdbc.queryForMap("""
            SELECT p.payload_sha256,s.receipt_text,s.status FROM submission s
            INNER JOIN submission_payload p ON p.submission_id=s.id WHERE s.id=?
            """,Ids.bytes(UUID.fromString(head.submission())));
        check("SUCCEEDED".equals(frozen.get("status")),"PDA原提交不是成功状态");
        var body=parse(files.read((String)frozen.get("payload_sha256")));
        check(sha(body).equals(text(head.receipt(),"payloadSha256")),"PDA冻结正文与回执摘要不一致");
        check(parse((String)frozen.get("receipt_text")).equals(head.receipt()),"PDA首版回执不一致");
        check(text(body,"submissionId").equals(head.submission()) && text(body.path("source"),"sourceOrderId").equals(head.order().toString()),"PDA原提交归属不一致");
        var captures=index(body,"captures","id");var scans=index(body,"scans","captureId");
        var excluded=com.acrylic.outbound.continuation.DispositionProofs.excluded(body);
        var records=facts.dispatches(head);var operators=new HashSet<String>();
        for(var d:records) operators.add(text(required(captures,d.capture()),"operatorId").toLowerCase(Locale.ROOT));
        var displayNames=scanRecords?names.resolve(operators):Map.<String,String>of();
        var dispatches=new ArrayList<ConfirmedV1Input.Dispatch>();
        for(var d:records) {
            check(!excluded.contains(d.capture()),"已排除原输入不可进入确认事实");
            dispatches.add(line(d,required(scans,d.capture()),required(captures,d.capture()),displayNames));
        }
        check(com.acrylic.outbound.continuation.DispositionProofs.effective(body).size()==dispatches.size(),"PDA有效冻结行与原确认事实数量不一致");
        int revision=body.path("orderRevision").intValue();var plan=facts.plan(head.order(),revision);
        return new ConfirmedV1Input.Order(head.order().toString(),head.number(),revision,plan.header(),head.confirmedAt(),
                ConfirmedV1Input.Channel.PDA,"dispatch_record.order_scan_seq / submission:"+head.submission(),
                facts.products(plan),dispatches,facts.result(head),head.receipt(),exceptions(captures,scans,body));
    }
    private ConfirmedV1Input.Dispatch line(ExportFacts.Dispatch d,JsonNode scan,JsonNode capture,Map<String,String> names) {
        match(d,scan,false);
        check(number(capture,"orderScanSeq")==d.sequence() && text(capture,"orderId").equals(d.order()),"PDA原采集顺序或归属不一致");
        String operatorId=text(capture,"operatorId");
        String operator=names.getOrDefault(operatorId.toLowerCase(Locale.ROOT),"原人员ID:"+operatorId);
        var line=new ReportLine(d.id(),d.capture(),d.sequence(),text(capture,"originDeviceId"),number(capture,"deviceCaptureSeq"),
                operator,text(capture,"inputSource"),text(scan,"barcode"),d.cycle(),d.code(),d.weight(),d.rawWeight(),
                wall(capture),text(scan,"savedAt"),"DEVICE_UNVERIFIED",names.isEmpty()?"":"姓名来源：导出申请时人员资料；原人员ID:"+operatorId);
        return new ConfirmedV1Input.Dispatch(d.order(),d.submission(),line);
    }
    private List<OrderSnapshot.ExceptionRow> exceptions(Map<String,JsonNode> captures,Map<String,JsonNode> scans,JsonNode body) {
        var result=new ArrayList<OrderSnapshot.ExceptionRow>();
        var proofs=new HashMap<String,JsonNode>();
        for(var proof:body.path("dispositions")) proofs.put(text(proof,"captureId"),proof);
        for(var c:captures.values()) {
            var scan=scans.get(text(c,"id"));String kind=scan==null?text(c,"state"):text(scan,"status");
            var proof=proofs.get(text(c,"id"));String detail=optional(c,"error");
            if(proof!=null) { kind=text(proof,"action");detail="原批准凭据:"+text(proof,"approvedEvidenceId"); }
            if(kind.equals("ACTIVE")) continue;
            result.add(new OrderSnapshot.ExceptionRow(text(c,"id"),number(c,"orderScanSeq"),text(c,"originDeviceId"),kind,
                    scan==null?optional(c,"barcode"):text(scan,"barcode"),detail,wall(c)));
        }
        return result;
    }
}
