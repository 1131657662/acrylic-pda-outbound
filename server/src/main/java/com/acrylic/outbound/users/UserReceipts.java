package com.acrylic.outbound.users;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class UserReceipts {
    private final JdbcTemplate jdbc;private final IdempotencyRequests requests;private final JsonText json;
    private final AuditWriter audit;private final PasswordEncoder passwords;
    public UserReceipts(JdbcTemplate jdbc,IdempotencyRequests requests,JsonText json,AuditWriter audit,PasswordEncoder passwords) {
        this.jdbc=jdbc;this.requests=requests;this.json=json;this.audit=audit;this.passwords=passwords;
    }
    public Ticket prepare(MutationAccess.Context context,UUID actor,Payload payload) {
        var key=new IdempotencyRequests.Key(actor,"user.manage",context.key());
        String digest=json.digest(Map.of("warehouse",context.warehouse(),"action",payload.action(),"body",payload.metadata()));
        UUID id=requests.find(key,digest);
        if(id!=null && payload.password()!=null) {
            String hash=jdbc.queryForObject("SELECT password_verifier FROM user_command_receipt WHERE id=?",String.class,Ids.bytes(id));
            if(hash==null || !passwords.matches(payload.password(),hash))
                throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","原请求的密码不一致，请查询原提交结果");
        }
        return new Ticket(key,digest,payload.action(),id==null?null:get(id));
    }
    public UserCommands.Receipt save(Ticket ticket,MutationAccess.Context context,Change change) {
        UUID id=Ids.next();var after=change.after();
        jdbc.update("INSERT INTO user_command_receipt VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(after.id()),
            Ids.bytes(ticket.key().actor()),Ids.bytes(context.warehouse()),after.rowVersion(),after.status(),ticket.action(),change.reason().strip(),change.passwordHash());
        requests.record(ticket.key(),ticket.digest(),id);
        var detail=new LinkedHashMap<String,Object>();detail.put("before",change.before());detail.put("after",after);detail.put("reason",change.reason().strip());
        audit.append(new AuditWriter.Event(ticket.key().actor(),"USER_"+ticket.action(),"USER",after.id(),json.encode(detail)),context.requestId());
        return get(id);
    }
    public UserCommands.Receipt lookup(UUID actor,UUID warehouse,String key) {
        if(!key.matches("[A-Za-z0-9._:-]{16,80}")) throw new BusinessException(400,"INVALID_REQUEST","原请求标识不正确");
        var ids=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) FROM user_command_receipt r INNER JOIN api_idempotency_request q ON q.resource_id=r.id
            WHERE q.principal_id=? AND q.route_scope='user.manage' AND q.idempotency_key=? AND r.warehouse_id=?
            """,String.class,Ids.bytes(actor),key,Ids.bytes(warehouse));
        if(ids.isEmpty()) throw new BusinessException(404,"USER_REQUEST_NOT_FOUND","尚未查到原操作回执，请保留原请求核对");
        return get(UUID.fromString(ids.getFirst()));
    }
    public Page<UserCommands.History> history(UUID user,String cursor) {
        UUID before=null;
        try { if(!cursor.isBlank()) before=UUID.fromString(cursor); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_REQUEST","历史分页位置不正确"); }
        var args=new ArrayList<Object>();args.add(Ids.bytes(user));if(before!=null) args.add(Ids.bytes(before));
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(r.id),r.action,u.display_name,r.reason,r.row_version,r.created_at
            FROM user_command_receipt r INNER JOIN app_user u ON u.id=r.actor_id WHERE r.user_id=?
            """+(before==null?"":" AND r.id<?")+" ORDER BY r.id DESC LIMIT 21",
            (rs,n)->new UserCommands.History(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant().toString()),args.toArray());
        return Page.from(rows,20,UserCommands.History::id);
    }
    private UserCommands.Receipt get(UUID id) {
        return jdbc.queryForObject("SELECT BIN_TO_UUID(id),BIN_TO_UUID(user_id),row_version,status,action,created_at FROM user_command_receipt WHERE id=?",
            (rs,n)->new UserCommands.Receipt(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant().toString()),Ids.bytes(id));
    }
    public record Payload(String action,Object metadata,String password) { }
    public record Ticket(IdempotencyRequests.Key key,String digest,String action,UserCommands.Receipt replay) { }
    public record Change(UserCommands.Item before,UserCommands.Item after,String reason,String passwordHash) { }
}
