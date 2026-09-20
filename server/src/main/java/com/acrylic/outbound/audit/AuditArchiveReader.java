package com.acrylic.outbound.audit;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reads original archived events without trusting an archive index or an unsigned checkpoint. */
@Component
public class AuditArchiveReader {
    private final SubmissionFiles files;private final SubmissionArchiveProofs signatures;
    public AuditArchiveReader(SubmissionFiles files,SubmissionArchiveProofs signatures) { this.files=files;this.signatures=signatures; }
    public ArrayNode read(JsonNode proof,UUID device,UUID warehouse) {
        signatures.verify(proof,"signedArchive","acrylic-pda-audit-archive","archive");
        equal(uuid(proof,"originDeviceId"),device,"归档原设备错误");equal(uuid(proof,"warehouseId"),warehouse,"归档仓库错误");
        try {
            range(proof);equal(proof.path("archiveVersion").intValue(),1,"审计归档版本错误");
            require(proof.path("localDeletionAllowed").isBoolean() && !proof.get("localDeletionAllowed").asBoolean(),"归档不能授权清理");
            equal(text(proof,"storagePolicy"),"SERVER_DURABLE_FILES_V1","审计归档存储策略错误");
            var at=Instant.parse(text(proof,"archivedAt"));
            require(!Instant.parse(text(proof,"retainUntil")).isBefore(at.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()),"审计归档期限错误");
            byte[] bytes=files.confirm(text(proof,"payloadSha256"));
            require(bytes.length > 0 && bytes.length <= 512 * 1024,"审计归档超出资源限制");
            equal((long)bytes.length,number(proof,"byteCount"),"审计归档长度错误");
            var body=parse(bytes);equal(uuid(body,"originDeviceId"),device,"审计正文设备错误");
            equal(number(body,"afterSeq"),number(proof,"afterSeq"),"审计正文起点错误");
            equal(number(body,"throughSeq"),number(proof,"throughSeq"),"审计正文终点错误");
            var events=array(body,"events");validateEvents(events,device,number(proof,"afterSeq"),number(proof,"throughSeq"));return events;
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
    public long prefix(JsonNode body,UUID device,UUID warehouse,Consumer<JsonNode> consume) {
        var coverage=array(body,"auditCoverage");require(coverage.size()==1,"当前恢复仅接受原设备审计");
        long after=number(coverage.get(0),"afterSeq"),through=0;
        var archives=body.has("auditArchives")?array(body,"auditArchives"):object().putArray("unused");
        require(archives.size()<=1024,"审计归档段超过当前恢复资源限额，请保留数据联系管理员");
        for(var proof:archives) {
            equal(number(proof,"afterSeq"),through,"归档前缀存在缺口、重叠或乱序");
            equal(text(proof,"recoveryEpoch"),text(body,"sourceRecoveryEpoch"),"归档须在当前恢复代次重新核对");
            read(proof,device,warehouse).forEach(consume);through=number(proof,"throughSeq");
        }
        equal(through,after,"归档前缀不覆盖审计起点");
        return through;
    }
    public static void range(JsonNode value) {
        long after=number(value,"afterSeq"),through=number(value,"throughSeq");
        require(through>after && through-after<=200,"审计归档每段须连续1至200条");
    }
    public static void validateEvents(ArrayNode events,UUID device,long after,long through) {
        equal((long)events.size(),through-after,"审计归档存在缺口");long next=after;var ids=new HashSet<UUID>();
        for(var event:events) {
            PdaAuditFacts.validate(event);equal(uuid(event,"originDeviceId"),device,"归档事件设备错误");
            equal(number(event,"localSeq"),++next,"归档事件存在缺口或乱序");require(ids.add(uuid(event,"id")),"归档事件ID重复");
        }
    }
}
