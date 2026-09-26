package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Historical committed decision closure, without reevaluating today's business permissions. */
public final class TemporaryArchiveEvidence {
    private final SubmissionFiles files;private final SubmissionStore submissions;
    private final JdbcTemplate jdbc;private final CommandStore commands;private final CommandReceiptIntegrity integrity;
    public TemporaryArchiveEvidence(SubmissionFiles files,SubmissionStore submissions,JdbcTemplate jdbc,
        CommandStore commands,CommandReceiptIntegrity integrity) {
        this.files=files;this.submissions=submissions;this.jdbc=jdbc;this.commands=commands;this.integrity=integrity;
    }
    public Evidence read(SubmissionStore.Head head,JsonNode request) {
        if(!head.status().equals("SUCCEEDED"))throw new BusinessException(409,"ARCHIVE_NOT_READY","原临时批次尚未成功，本机原件必须保留");
        try {
            require(head.control().temporary()!=null && head.control().round()==1 && head.order()!=null,"不支持的归档来源");
            String hash=submissions.payloadHash(head.control().id());byte[] payload=files.confirm(hash);var body=parse(payload);
            equal(SubmissionControl.read(head.control().id(),body),head.control(),"归档正文来源不符");
            equal(uuid(body,"submissionId"),head.control().id(),"归档批次不符");
            var command=command(head);var decision=integrity.verify(command);var context=decision.path("originalContext");
            require(decision.path("status").asText().equals("RECORDED") && decision.at("/businessDecision/kind").asText().equals("NORMAL_CONFIRM"),"缺少实际正常确认");
            equal(uuid(context,"warehouseId"),head.warehouse(),"归档仓不符");equal(uuid(context,"originalOperatorId"),head.actor(),"归档原人不符");
            equal(uuid(context,"canonicalOrderId"),head.order(),"归档原目标不符");equal(text(context,"mappingVersion"),"1","不支持更高映射归档");
            var receipt=decision.at("/businessDecision/submissionReceipt");equal(receipt,parse(head.receipt()),"归档成功原件不符");
            byte[] receiptBytes=canonical(receipt).getBytes(StandardCharsets.UTF_8);String receiptHash=sha(receipt);
            if(!text(request,"payloadSha256").equals(hash) || !text(request,"receiptSha256").equals(receiptHash))
                throw new BusinessException(409,"ARCHIVE_CONTENT_MISMATCH","归档摘要与原正文或成功回执不符");
            return new Evidence(hash,payload.length,receiptHash,receiptBytes,command.id("id"),command.bytes("original_response_bytes"),
                ImportFiles.sha256(command.bytes("original_response_bytes")),receipt,context);
        } catch(BusinessException error) {
            if(error.code().equals("ARCHIVE_CONTENT_MISMATCH"))throw error;throw SubmissionArchiveProofs.unavailable();
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
    private CommandRecord command(SubmissionStore.Head head) {
        var ids=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) FROM temporary_command_receipt r
            JOIN temporary_decision_event e ON e.receipt_id=r.id
            WHERE r.submission_id=? AND r.command_type='NORMAL_CONFIRM' AND e.decision_kind='NORMAL_CONFIRM'
            """,String.class,Ids.bytes(head.control().id()));
        require(ids.size()==1,"原成功确认记录不唯一");return commands.byReceipt(UUID.fromString(ids.getFirst()));
    }
    public void persist(Evidence evidence,boolean existing) {
        try {
            persist(evidence.receiptHash(),evidence.receiptBytes(),existing);
            persist(evidence.commandHash(),evidence.commandBytes(),existing);
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
    private void persist(String hash,byte[] bytes,boolean existing) {
        if(!existing)equal(files.store(bytes),hash,"原件存储摘要不符");
        require(Arrays.equals(files.confirm(hash),bytes),"归档原件不符");
    }
    public record Evidence(String payloadHash,int payloadBytes,String receiptHash,byte[] receiptBytes,UUID commandId,
        byte[] commandBytes,String commandHash,JsonNode receipt,JsonNode context) { }
}
