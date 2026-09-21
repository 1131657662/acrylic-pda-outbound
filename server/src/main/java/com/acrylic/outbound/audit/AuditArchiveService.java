package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.recall.RecoveryAudit;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class AuditArchiveService {
    private final PdaAccess access;private final PdaAuthLocks locks;private final IdentityWriteGuard guard;
    private final PdaAuditStore audit;private final AuditArchiveStore archives;private final AuditArchiveReader reader;
    private final SubmissionFiles files;private final OfflineSigner signer;private final RecoveryAudit recoveries;
    public AuditArchiveService(PdaAccess access,PdaAuthLocks locks,IdentityWriteGuard guard,PdaAuditStore audit,AuditArchiveStore archives,
        AuditArchiveReader reader,SubmissionFiles files,OfflineSigner signer,RecoveryAudit recoveries) {
        this.access=access;this.locks=locks;this.guard=guard;this.audit=audit;this.archives=archives;
        this.reader=reader;this.files=files;this.signer=signer;this.recoveries=recoveries;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode confirm(String epoch,JsonNode request) {
        var actor=actor(epoch);require(request.isObject() && request.size()==4,"审计归档请求字段错误");
        equal(uuid(request,"originDeviceId"),actor.deviceId(),"审计归档设备不匹配");AuditArchiveReader.range(request);
        require(text(request,"payloadSha256").matches("[a-f0-9]{64}"),"审计归档摘要无效");
        var body=archives.payload(actor.deviceId(),actor.warehouseId(),request);
        equal(sha(body),text(request,"payloadSha256"),"审计归档与本机原记录摘要不一致");
        guard.users(audit.users(actor,array(body,"events")));locks.principal(actor);actor=access.require("pda:submit");
        recoveries.verifyExisting(actor.deviceId(),array(body,"events"));
        for(var row:array(body,"events")) audit.accept(actor,row);
        require(audit.advance(epoch,actor.deviceId())>=number(request,"throughSeq"),"须先补传此前连续审计");
        return persist(actor,epoch,body);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode restore(String epoch,JsonNode request) {
        var actor=actor(epoch);require(request.isObject() && request.size()==1 && request.has("archive"),"审计恢复请求字段错误");
        var old=request.get("archive");var events=reader.read(old,actor.deviceId(),actor.warehouseId());
        guard.users(audit.users(actor,events));locks.principal(actor);actor=access.require("pda:submit");
        require(number(old,"recoveryEpoch")<=Long.parseLong(epoch),"不能使用未来恢复代次的归档凭据");
        recoveries.verifyExisting(actor.deviceId(),events);for(var row:events) audit.accept(actor,row);
        require(audit.advance(epoch,actor.deviceId())>=number(old,"throughSeq"),"须先恢复此前连续审计");
        var body=object().put("originDeviceId",actor.deviceId().toString()).put("afterSeq",text(old,"afterSeq")).put("throughSeq",text(old,"throughSeq"));
        body.set("events",events);return persist(actor,epoch,body);
    }
    private PdaSessions.Principal actor(String epoch) {
        access.require("pda:submit");guard.lock(epoch);return access.require("pda:submit");
    }
    private JsonNode persist(PdaSessions.Principal actor,String epoch,JsonNode body) {
        long after=number(body,"afterSeq");var old=archives.find(epoch,actor.deviceId(),after);
        if(old!=null) {
            if(number(old,"throughSeq")!=number(body,"throughSeq") || !text(old,"payloadSha256").equals(sha(body)))
                throw new BusinessException(409,"AUDIT_ARCHIVE_CONTENT_MISMATCH","原归档段已固定，请恢复原请求");
            reader.read(old,actor.deviceId(),actor.warehouseId());return old;
        }
        archives.requireNext(epoch,actor.deviceId(),after);byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);
        require(bytes.length > 0 && bytes.length <= 512 * 1024,"审计归档超出资源限制");
        equal(files.store(bytes),sha(body),"审计归档文件摘要不符");files.confirm(sha(body));
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var proof=object().put("archiveVersion",1).put("originDeviceId",actor.deviceId().toString()).put("warehouseId",actor.warehouseId().toString())
            .put("recoveryEpoch",epoch).put("afterSeq",text(body,"afterSeq")).put("throughSeq",text(body,"throughSeq"))
            .put("payloadSha256",sha(body)).put("byteCount",Integer.toString(bytes.length)).put("archivedAt",now.toString())
            .put("retainUntil",now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant().toString())
            .put("storagePolicy","SERVER_DURABLE_FILES_V1").put("localDeletionAllowed",false);
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-audit-archive");claims.set("archive",proof.deepCopy());
        proof.put("signedArchive",signer.sign(claims).token());archives.insert(epoch,actor.deviceId(),proof);return proof;
    }
}
