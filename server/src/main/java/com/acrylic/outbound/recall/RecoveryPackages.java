package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RecoveryPackages {
    private final RecallAccess access;private final RecallStore store;private final AssignmentStore assignments;private final RecoveryEvidence evidence;
    private final SubmissionFiles files;private final JdbcTemplate jdbc;private final RecoveryStock stock;
    public RecoveryPackages(RecallAccess access,RecallStore store,AssignmentStore assignments,RecoveryEvidence evidence,SubmissionFiles files,JdbcTemplate jdbc,RecoveryStock stock) {
        this.access=access;this.store=store;this.assignments=assignments;this.evidence=evidence;this.files=files;this.jdbc=jdbc;this.stock=stock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode receive(JsonNode body,String epoch) {
        var before=store.get(uuid(body,"changeRequestId"));access.pda(before.assignment(),epoch,true);var h=store.lock(before);
        UUID id=uuid(body,"recoveryPackageId");String hash=sha(body);var old=store.recovery(h.id());
        if(old!=null) {
            if(!old.id().equals(id) || !old.hash().equals(hash))throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","原召回恢复包已固定，请恢复原ID及正文");
            return store.packageView(old);
        }
        require(h.status().equals("REQUESTED"),"召回状态不接受新恢复包");
        var value=evidence.validate(h,assignments.get(h.assignment()),body);byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);
        require(bytes.length<=33554432,"恢复包超过32MiB，须保留数据并人工处理");equal(files.store(bytes),hash,"恢复文件摘要不一致");
        jdbc.update("INSERT INTO recovery_package VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(h.id()),hash,bytes.length,
            value.stock().scans().size(),value.stock().weight(),number(body,"captureHighWatermark"),value.auditHigh(),"[]");
        var issues=stock.reserve(h,id,value.stock());jdbc.update("UPDATE recovery_package SET issues_json=? WHERE id=?",canonical(issues),Ids.bytes(id));
        return store.packageView(store.recovery(h.id()));
    }
    @Transactional(readOnly=true) public JsonNode detail(UUID id) {
        var request=jdbc.queryForList("SELECT BIN_TO_UUID(request_id) FROM recovery_package WHERE id=?",String.class,Ids.bytes(id));
        if(request.isEmpty())throw new BusinessException(404,"RECOVERY_NOT_FOUND","恢复包不存在或无权查看");
        var h=store.get(UUID.fromString(request.getFirst()));access.web(h.warehouse(),"",false);var p=store.recovery(h.id());var body=parse(files.read(p.hash()));
        var result=object();result.set("recovery",store.packageView(p));result.set("scans",array(body,"scans"));result.set("captureSummary",body.get("captureSummary"));
        result.set("dispositions",com.acrylic.outbound.continuation.DispositionProofs.rows(body));return result;
    }
}
