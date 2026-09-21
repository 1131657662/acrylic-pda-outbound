package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderWriter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Collect PC identity dependencies before acquiring warehouse/order locks. */
@Component
public class PcIdentityLocks {
    private final JdbcTemplate jdbc;
    private final MutationAccess access;
    private final OrderWriter orders;
    public PcIdentityLocks(JdbcTemplate jdbc,MutationAccess access,OrderWriter orders) {
        this.jdbc=jdbc;this.access=access;this.orders=orders;
    }
    public Locked operator(MutationAccess.Context context,String permission,UUID order) {
        return lock(context,permission,new Request(order,null,null));
    }
    public Locked target(MutationAccess.Context context,UUID order,String username) {
        return lock(context,"order:resume",new Request(order,username.strip(),null));
    }
    public Locked plan(MutationAccess.Context context,String permission,UUID order,UUID plan) {
        return lock(context,permission,new Request(order,null,plan));
    }
    private Locked lock(MutationAccess.Context context,String permission,Request request) {
        var before=hint(request);
        var actor=access.lock(context,permission,before.users()).actor();
        orders.lock(context.warehouse(),request.order());
        return new Locked(actor,before.equals(hint(request)));
    }
    private Hint hint(Request request) {
        UUID operator=one("SELECT BIN_TO_UUID(operator_id) FROM pc_work WHERE order_id=?",Ids.bytes(request.order()));
        UUID target=request.username()==null?null:one("SELECT BIN_TO_UUID(id) FROM app_user WHERE username=?",request.username());
        List<PlanUsers> plan=request.plan()==null?List.of():jdbc.query("""
            SELECT BIN_TO_UUID(p.target_user_id),BIN_TO_UUID(p.approved_by)
            FROM pc_exception_plan p INNER JOIN pc_exception_case c ON c.id=p.case_id
            INNER JOIN pc_work w ON w.id=c.work_id WHERE p.id=? AND w.order_id=?
            """,(rs,n)->new PlanUsers(UUID.fromString(rs.getString(1)),rs.getString(2)==null?null:UUID.fromString(rs.getString(2))),
            Ids.bytes(request.plan()),Ids.bytes(request.order()));
        var current=jdbc.queryForList("""
            SELECT BIN_TO_UUID(p.id) FROM pc_exception_plan p
            INNER JOIN pc_exception_case c ON c.id=p.case_id
            INNER JOIN pc_work w ON w.id=c.work_id WHERE w.order_id=?
            ORDER BY c.work_round DESC,p.id DESC LIMIT 1
            """,String.class,Ids.bytes(request.order()));
        return new Hint(operator,target,plan,current);
    }
    private UUID one(String sql,Object value) {
        var rows=jdbc.queryForList(sql,String.class,value);
        return rows.isEmpty()?null:UUID.fromString(rows.getFirst());
    }
    private record Request(UUID order,String username,UUID plan) { }
    public record Locked(UUID actor,boolean unchanged) {
        public void recheck() {
            if(!unchanged) throw new BusinessException(409,"PC_IDENTITY_CONTEXT_CHANGED","作业人员或交接方案已变化，请刷新后重新核对");
        }
    }
    private record PlanUsers(UUID target,UUID approver) { }
    private record Hint(UUID operator,UUID target,List<PlanUsers> plan,List<String> currentPlan) {
        List<UUID> users() {
            var users=new ArrayList<UUID>();users.add(operator);users.add(target);
            for(var row:plan) { users.add(row.target());users.add(row.approver()); }
            return users;
        }
    }
}
