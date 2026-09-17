package com.acrylic.outbound.devices;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceReceipts {
    private final JdbcTemplate jdbc;private final IdempotencyRequests requests;private final JsonText json;private final AuditWriter audit;
    public DeviceReceipts(JdbcTemplate jdbc,IdempotencyRequests requests,JsonText json,AuditWriter audit) {
        this.jdbc=jdbc;this.requests=requests;this.json=json;this.audit=audit;
    }
    public Ticket prepare(MutationAccess.Context context,UUID actor,Payload payload) {
        var key=new IdempotencyRequests.Key(actor,"device."+payload.action(),context.key());
        String digest=json.digest(new Body(context.warehouse(),payload));UUID id=requests.find(key,digest);
        return new Ticket(key,digest,id==null?null:get(id));
    }
    public DeviceCommands.Receipt save(Ticket ticket,MutationAccess.Context context,Result result) {
        UUID id=Ids.next();
        jdbc.update("INSERT INTO device_command_receipt VALUES(?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(result.deviceId()),result.version(),result.status());
        requests.record(ticket.key(),ticket.digest(),id);
        audit.append(new AuditWriter.Event(ticket.key().actor(),result.event(),"DEVICE",result.deviceId(),json.encode(result)),context.requestId());
        return get(id);
    }
    private DeviceCommands.Receipt get(UUID id) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(id),BIN_TO_UUID(device_id),row_version,status FROM device_command_receipt WHERE id=?",
            (rs,n)->new DeviceCommands.Receipt(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),Ids.bytes(id));
    }
    public record Payload(String action,UUID id,Object command) { }
    private record Body(UUID warehouse,Payload payload) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,DeviceCommands.Receipt replay) { }
    public record Result(UUID deviceId,String version,String status,String event,String reason) { }
}
