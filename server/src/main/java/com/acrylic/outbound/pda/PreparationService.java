package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.devices.DeviceCrypto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PreparationService {
    private final PreparationAccess access;private final PreparationStore store;private final PreparationPayloads payloads;
    private final OfflineSigner signer;private final JsonText json;private final AuditWriter audit;
    public PreparationService(PreparationAccess access,PreparationStore store,PreparationPayloads payloads,OfflineSigner signer,JsonText json,AuditWriter audit) {
        this.access=access;this.store=store;this.payloads=payloads;this.signer=signer;this.json=json;this.audit=audit;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PreparationStore.Receipt prepare(UUID id,String epoch) {
        var assignment=access.lock(id,epoch);var existing=store.find(id);if(existing!=null) return existing;
        var data=payloads.read(assignment);
        var parts=List.of(PreparationArchive.pack("plan",data.plan(),1),PreparationArchive.pack("inventory",data.inventory(),data.rows()),
            PreparationArchive.pack("availability",data.availability(),data.rows()));
        if(!parts.get(0).descriptor().sha256().equals(assignment.planSha256()) || !parts.get(1).descriptor().sha256().equals(assignment.inventorySha256()))
            throw new BusinessException(409,"PREPARATION_INCONSISTENT","固定版本摘要不一致，请保留占用并联系管理员");
        String bundle=Ids.next().toString();
        var manifest=new Manifest(1,bundle,assignment.id(),assignment.orderId(),assignment.deviceId(),assignment.operatorId(),assignment.epoch(),
            assignment.serverRecoveryEpoch(),assignment.inventorySnapshotId(),assignment.revision(),Instant.now().toString(),
            assignment.expiresAt(),parts.stream().map(PreparationArchive::descriptor).toList());
        String text=json.encode(manifest),sha=DeviceCrypto.sha(text);
        var proof=signer.sign(Map.of("iss","acrylic-outbound","aud","acrylic-pda-preparation","manifestSha256",sha,
            "assignmentId",assignment.id(),"exp",Instant.parse(assignment.expiresAt()).getEpochSecond()));
        var receipt=new PreparationStore.Receipt(bundle,text,sha,proof.token());store.insert(id,receipt,parts);return receipt;
    }
    @Transactional(readOnly=true)
    public byte[] content(UUID id,String bundle,String part) { access.read(id);return store.content(id,bundle,part); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Ack acknowledge(UUID id,Command command,Context context) {
        var assignment=access.lock(id,context.epoch());var receipt=store.find(id);
        if(receipt==null || !receipt.bundleId().equals(command.bundleId().toString()) || !receipt.manifestSha256().equals(command.manifestSha256())
            || !assignment.epoch().equals(command.assignmentEpoch()) || !assignment.inventorySnapshotId().equals(command.inventorySnapshotId().toString()))
            throw new BusinessException(409,"DOWNLOAD_CONFIRMATION_MISMATCH","下载确认与固定清单不匹配");
        if(command.activatedAt().isAfter(Instant.now().plusSeconds(300)) || command.activatedAt().isBefore(Instant.parse(assignment.issuedAt())))
            throw new BusinessException(400,"INVALID_ACTIVATION_TIME","本机激活时间不在授权范围，请联网校时");
        if(assignment.downloadAckAt()==null) audit.append(new AuditWriter.Event(UUID.fromString(assignment.operatorId()),"ORDER_DOWNLOAD_ACK",
            "ORDER",UUID.fromString(assignment.orderId()),json.encode(command)),context.requestId());
        return new Ack(assignment.id(),receipt.bundleId(),store.acknowledge(id));
    }
    public record Manifest(int schemaVersion,String bundleId,String assignmentId,String orderId,String deviceId,String operatorId,
        String assignmentEpoch,String serverRecoveryEpoch,String inventorySnapshotId,int revision,String stateCapturedAt,String expiresAt,
        List<PreparationArchive.Part> parts) { }
    public record Command(@NotNull UUID bundleId,@NotNull @Pattern(regexp="[0-9a-f]{64}") String manifestSha256,
        @NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String assignmentEpoch,@NotNull UUID inventorySnapshotId,@NotNull Instant activatedAt) { }
    public record Context(String epoch,String requestId) { }
    public record Ack(String assignmentId,String bundleId,String downloadAckAt) { }
}
