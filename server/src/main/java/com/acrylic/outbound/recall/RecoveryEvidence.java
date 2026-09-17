package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecoveryEvidence {
    private final SubmissionEvidence evidence;private final SubmissionSnapshot snapshots;private final RecoveryAudit audit;
    private final JdbcTemplate jdbc;private final SubmissionFiles files;
    public RecoveryEvidence(SubmissionEvidence evidence,SubmissionSnapshot snapshots,RecoveryAudit audit,JdbcTemplate jdbc,SubmissionFiles files) {
        this.evidence=evidence;this.snapshots=snapshots;this.audit=audit;this.jdbc=jdbc;this.files=files;
    }
    public Validated validate(RecallStore.Head h,AssignmentStore.View a,JsonNode body) {
        equal(uuid(body,"changeRequestId"),h.id(),"恢复包召回来源错误");equal(uuid(body,"assignmentId"),h.assignment(),"恢复包原授权错误");
        equal(text(body,"sourceRecoveryEpoch"),h.epoch(),"恢复代次错误");equal(text(body,"orderId"),h.order().toString(),"恢复包原订单错误");
        equal(text(body,"freezeDeviceId"),a.deviceId(),"停止设备错误");equal(text(body,"assignmentEpoch"),a.epoch(),"原授权代次错误");
        require(body.path("unresolvedCount").isTextual() && number(body,"unresolvedCount")==0,"输入队列尚未核对完毕");
        evidence.validateRecovery(a,h.warehouse(),body);var stock=snapshots.validateRecovery(a,body);
        var hashes=jdbc.queryForList("SELECT p.payload_sha256 FROM submission_payload p INNER JOIN submission s ON s.id=p.submission_id WHERE s.assignment_id=?",String.class,Ids.bytes(h.assignment()));
        for(String hash:hashes) unchanged(parse(files.read(hash)),body);
        return new Validated(stock,audit.validate(UUID.fromString(a.deviceId()),body));
    }
    private void unchanged(JsonNode previous,JsonNode body) {
        for(String key:List.of("captures","scans","events")) {
            String id=key.equals("scans")?"captureId":"id";var records=new HashMap<String,String>();
            array(body,key).forEach(row->records.put(text(row,id),sha(row)));
            for(var row:array(previous,key))equal(sha(row),records.get(text(row,id)),"已提交原事实被遗漏或改写");
        }
    }
    public record Validated(SubmissionSnapshot.Validated stock,long auditHigh) {}
}
