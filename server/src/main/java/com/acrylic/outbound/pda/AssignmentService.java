package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.orders.OrderWriter;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AssignmentService {
    private final PdaAccess access;private final OrderWriter orders;private final AssignmentStore store;
    private final AssignmentEvents events;private final OfflineSigner signer;private final WarehouseBaseline warehouses;
    public AssignmentService(PdaAccess access,OrderWriter orders,AssignmentStore store,AssignmentEvents events,OfflineSigner signer,WarehouseBaseline warehouses) {
        this.access=access;this.orders=orders;this.store=store;this.events=events;this.signer=signer;this.warehouses=warehouses;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AssignmentStore.View claim(Context context,UUID order,Command command) {
        var principal=access.lock(context.recoveryEpoch());var head=orders.lock(principal.warehouseId(),order);
        var ticket=events.prepare(principal,new AssignmentEvents.Input(order,command),context.key());
        if(ticket.replay()!=null) return store.get(ticket.replay());
        var active=store.active(order);
        if(active!=null) throw new BusinessException(409,"ORDER_ASSIGNED","订单已被领取，请查询本设备活动授权或联系主管，不能直接重领");
        if(!head.rowVersion().equals(command.expectedVersion())) throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单版本已变化，请重新核对");
        if(!head.status().equals("READY")) throw new BusinessException(409,"ORDER_STATE_CONFLICT","仅待领取订单可以领取");
        var publication=store.publication(order,head.revision());var baseline=warehouses.read(principal.warehouseId(),false);
        if(!publication.recoveryEpoch().equals(context.recoveryEpoch())) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","发布版本来自旧恢复代次，需先对账");
        if(!publication.snapshotId().equals(baseline.activeSnapshotId()) || !publication.mappingRevision().equals(baseline.mappingRevision()))
            throw new BusinessException(409,"ORDER_BASELINE_CHANGED","库存或映射已变化，请后台核对并重新发布后领取");
        var issued=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var work=new AssignmentStore.Work(Ids.next(),Ids.next(),order,head.revision(),store.nextEpoch(order),principal,
            DeviceCrypto.sha(publication.snapshotJson()),command.areaNote().strip(),issued,issued.plusSeconds(48*3600));
        var signed=signer.sign(new Claims("acrylic-outbound","acrylic-pda-offline",work.authId().toString(),principal.userId().toString(),
            principal.deviceId().toString(),principal.warehouseId().toString(),work.id().toString(),order.toString(),head.revision(),
            Long.toString(work.epoch()),context.recoveryEpoch(),publication.snapshotId(),publication.policyVersion(),publication.autoDeduct(),
            work.planSha(),publication.inventorySha(),"1","0",store.workRound(order),principal.userAuthVersion(),principal.deviceVersion(),"pda:scan",
            issued.getEpochSecond(),work.expires().getEpochSecond()));
        store.insert(work,publication,signed);events.save(ticket,work,context.requestId());return store.get(work.id());
    }
    public record Context(String recoveryEpoch,String key,String requestId) { }
    public record Command(@NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String expectedVersion,@NotNull @Size(max=500) String areaNote) { }
    private record Claims(String iss,String aud,String jti,String sub,String deviceId,String warehouseId,String assignmentId,String orderId,
        int revision,String assignmentEpoch,String serverRecoveryEpoch,String inventorySnapshotId,String policyVersion,boolean autoDeductInventory,
        String planSha256,String inventorySha256,String startOrderScanSeq,String allocationVersion,String workRound,String userAuthVersion,String deviceVersion,
        String scope,long iat,long exp) { }
}
