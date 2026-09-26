package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportFacts.check;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Read-only historical confirmation closure; never executes a decision or releases a guard. */
@Component
public class TemporaryExportEvidence {
    private final JdbcTemplate jdbc;private final SubmissionStore submissions;private final SubmissionFiles files;
    private final CommandStore commands;private final CommandReceiptIntegrity integrity;
    public TemporaryExportEvidence(JdbcTemplate jdbc,SubmissionStore submissions,SubmissionFiles files,
            CommandStore commands,CommandReceiptIntegrity integrity) {
        this.jdbc=jdbc;this.submissions=submissions;this.files=files;this.commands=commands;this.integrity=integrity;
    }
    public Value read(ExportFacts.Head target,Instant cutoff) {
        try { return checked(target,cutoff); }
        catch(RuntimeException damaged) {
            throw new BusinessException(409,"EXPORT_SOURCE_UNAVAILABLE","临时来源原确认凭据不完整或不一致，无法导出");
        }
    }
    private Value checked(ExportFacts.Head target,Instant cutoff) {
        check(target.pdaSubmission()!=null && target.pcSubmission()==null,"TEMP必须保留原PDA提交");
        var head=submissions.required(UUID.fromString(target.pdaSubmission()));
        check(head.warehouse().equals(target.warehouse()) && target.order().equals(head.order())
                && head.status().equals("SUCCEEDED") && head.control().temporary()!=null && head.control().round()==1,"TEMP原批次归属不符");
        String hash=submissions.payloadHash(head.control().id());var body=parse(files.read(hash));
        check(hash.equals(sha(body)) && hash.equals(text(target.receipt(),"payloadSha256")),"TEMP原正文摘要不符");
        check(SubmissionControl.read(uuid(body,"submissionId"),body).equals(head.control()),"TEMP原控制不符");
        var ids=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) FROM temporary_command_receipt r
            JOIN temporary_decision_event e ON e.receipt_id=r.id
            JOIN submission s ON s.id=r.submission_id AND s.scope_id=r.scope_id
            WHERE r.submission_id=? AND r.command_type='NORMAL_CONFIRM' AND e.decision_kind='NORMAL_CONFIRM'
            """,String.class,Ids.bytes(head.control().id()));
        check(ids.size()==1,"TEMP原确认不唯一");var record=commands.byReceipt(UUID.fromString(ids.getFirst()));
        var decision=integrity.verify(record);var context=decision.path("originalContext");
        check(!Instant.parse(text(decision,"recordedAt")).isAfter(cutoff),"TEMP原确认晚于截点");
        check(text(context,"mappingVersion").equals("1")
                && uuid(context,"warehouseId").equals(target.warehouse()) && uuid(context,"canonicalOrderId").equals(target.order())
                && uuid(context,"originalOperatorId").equals(head.actor()),"TEMP原映射或身份不符");
        check(decision.at("/businessDecision/submissionReceipt").equals(target.receipt())
                && parse(head.receipt()).equals(target.receipt()),"TEMP原成功回执不符");
        return new Value(head,body,text(decision,"receiptId"),record.text("original_response_sha256"));
    }
    public record Value(SubmissionStore.Head head,JsonNode body,String confirmationId,String confirmationHash) { }
}
