package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.exports.ExportSourceJson.*;
import static com.acrylic.outbound.exports.ExportFacts.check;

@Component
public class PcExportSource {
    private final JdbcTemplate jdbc;
    private final ExportFacts facts;
    public PcExportSource(JdbcTemplate jdbc,ExportFacts facts) { this.jdbc=jdbc;this.facts=facts; }
    public ConfirmedV1Input.Order read(ExportFacts.Head head) {
        var frozen=jdbc.queryForMap("SELECT payload_text,payload_sha256,receipt_json,status FROM pc_submission WHERE id=?",Ids.bytes(UUID.fromString(head.submission())));
        check("SUCCEEDED".equals(frozen.get("status")),"PC原提交不是成功状态");
        var body=parse((String)frozen.get("payload_text"));
        check(sha(body).equals(frozen.get("payload_sha256")) && sha(body).equals(text(head.receipt(),"payloadSha256")),"PC冻结正文摘要不一致");
        check(parse((String)frozen.get("receipt_json")).equals(head.receipt()),"PC首版回执不一致");
        check(text(body,"orderId").equals(head.order().toString()),"PC冻结订单不一致");
        String origin="pc-work:"+text(body,"workId");int revision=body.path("revision").intValue();
        var entries=index(body,"entries","id");var dispatches=new ArrayList<ConfirmedV1Input.Dispatch>();
        for(var d:facts.dispatches(head)) dispatches.add(line(d,required(entries,d.capture()),origin));
        check(entries.values().stream().filter(e->text(e,"status").equals("ACTIVE")).count()==dispatches.size(),"PC有效冻结行与原确认事实数量不一致");
        var plan=facts.plan(head.order(),revision);
        return new ConfirmedV1Input.Order(head.order().toString(),head.number(),revision,plan.header(),head.confirmedAt(),
                ConfirmedV1Input.Channel.PC,"dispatch_record.order_scan_seq / pc_submission:"+head.submission(),
                facts.products(plan),dispatches,facts.result(head),head.receipt(),exceptions(entries,origin,body));
    }
    private ConfirmedV1Input.Dispatch line(ExportFacts.Dispatch d,JsonNode entry,String origin) {
        match(d,entry,true);String saved=text(entry,"createdAt");
        var line=new ReportLine(d.id(),d.capture(),d.sequence(),origin,d.sequence(),text(entry,"operatorName"),"PC_MANUAL",
                text(entry,"barcode"),d.cycle(),d.code(),d.weight(),d.rawWeight(),saved,saved,"SERVER_CAPTURED","");
        return new ConfirmedV1Input.Dispatch(d.order(),d.submission(),line);
    }
    private List<OrderSnapshot.ExceptionRow> exceptions(Map<String,JsonNode> entries,String origin,JsonNode body) {
        var result=new ArrayList<OrderSnapshot.ExceptionRow>();
        var proofs=new HashMap<String,String>();
        for(var proof:body.path("dispositions")) proofs.put(text(proof,"entryId"),text(proof,"action")+": "+text(proof,"physicalEvidence"));
        for(var e:entries.values()) if(!text(e,"status").equals("ACTIVE"))
            result.add(new OrderSnapshot.ExceptionRow(text(e,"id"),number(e,"sequence"),origin,text(e,"status"),
                    text(e,"barcode"),proofs.getOrDefault(text(e,"id"),optional(e,"voidReason")),text(e,"createdAt")));
        return result;
    }
}
