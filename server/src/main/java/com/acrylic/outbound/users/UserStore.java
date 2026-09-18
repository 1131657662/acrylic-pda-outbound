package com.acrylic.outbound.users;

import com.acrylic.outbound.common.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserStore {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS="BIN_TO_UUID(u.id),u.username,u.display_name,u.status,u.auth_version";
    private static final String SCOPE="""
        EXISTS(SELECT 1 FROM user_warehouse tw WHERE tw.user_id=u.id AND tw.warehouse_id=?)
        AND NOT EXISTS(SELECT 1 FROM user_warehouse tw WHERE tw.user_id=u.id AND NOT EXISTS(
            SELECT 1 FROM user_warehouse aw INNER JOIN warehouse w ON w.id=aw.warehouse_id
            WHERE aw.user_id=? AND aw.warehouse_id=tw.warehouse_id AND w.status='ACTIVE'))
        AND NOT EXISTS(SELECT 1 FROM user_role tr INNER JOIN role_permission tp ON tp.role_id=tr.role_id
            WHERE tr.user_id=u.id AND NOT EXISTS(SELECT 1 FROM user_role ar
            INNER JOIN role_permission ap ON ap.role_id=ar.role_id WHERE ar.user_id=? AND ap.permission_id=tp.permission_id))
        """;
    public UserStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public UserCommands.Page list(UserAccess.Scope scope,UUID warehouse,UserCommands.Query query) {
        if(query.page()<1 || query.page()>100000 || !Set.of(20,50,100).contains(query.pageSize()) || query.username().length()>64 ||
            !Set.of("","ACTIVE","DISABLED").contains(query.status())) throw new BusinessException(400,"INVALID_REQUEST","人员查询条件或分页不正确");
        var args=new ArrayList<Object>(List.of(Ids.bytes(warehouse),Ids.bytes(scope.actor()),Ids.bytes(scope.actor())));
        String where=SCOPE;
        if(!query.username().isBlank()) { where+=" AND u.username=?";args.add(query.username()); }
        if(!query.status().isBlank()) { where+=" AND u.status=?";args.add(query.status()); }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM app_user u WHERE "+where,Long.class,args.toArray());
        int page=(int)Math.min(query.page(),Math.max(1,(total+query.pageSize()-1)/query.pageSize()));
        args.add(query.pageSize());args.add((page-1)*query.pageSize());
        var rows=jdbc.query("SELECT "+COLUMNS+" FROM app_user u WHERE "+where+" ORDER BY u.id DESC LIMIT ? OFFSET ?",this::map,args.toArray());
        return new UserCommands.Page(hydrate(rows),total,page,query.pageSize());
    }
    public UserCommands.Item find(UUID id) {
        var rows=jdbc.query("SELECT "+COLUMNS+" FROM app_user u WHERE u.id=?",this::map,Ids.bytes(id));
        if(rows.isEmpty()) throw UserAccess.missing();return hydrate(rows).getFirst();
    }
    private List<UserCommands.Item> hydrate(List<UserCommands.Item> rows) {
        if(rows.isEmpty()) return rows;
        String marks=String.join(",",Collections.nCopies(rows.size(),"?"));
        Object[] ids=rows.stream().map(u->Ids.bytes(u.id())).toArray();
        var roles=new HashMap<UUID,List<String>>();var warehouses=new HashMap<UUID,List<UserCommands.Warehouse>>();
        jdbc.query("SELECT BIN_TO_UUID(ur.user_id),r.code FROM user_role ur INNER JOIN role r ON r.id=ur.role_id WHERE ur.user_id IN ("+marks+") ORDER BY r.code",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->roles.computeIfAbsent(UUID.fromString(rs.getString(1)),k->new ArrayList<>()).add(rs.getString(2)),ids);
        jdbc.query("SELECT BIN_TO_UUID(uw.user_id),BIN_TO_UUID(w.id),w.code,w.name FROM user_warehouse uw INNER JOIN warehouse w ON w.id=uw.warehouse_id WHERE uw.user_id IN ("+marks+") ORDER BY w.code",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->warehouses.computeIfAbsent(UUID.fromString(rs.getString(1)),k->new ArrayList<>())
                .add(new UserCommands.Warehouse(UUID.fromString(rs.getString(2)),rs.getString(3),rs.getString(4))),ids);
        return rows.stream().map(u->new UserCommands.Item(u.id(),u.username(),u.displayName(),u.status(),u.rowVersion(),
            roles.getOrDefault(u.id(),List.of()),warehouses.getOrDefault(u.id(),List.of()))).toList();
    }
    public List<UserCommands.Role> roles() {
        var result=new LinkedHashMap<String,UserCommands.Role>();
        jdbc.query("""
            SELECT r.code,r.name,p.code,p.name FROM role r LEFT JOIN role_permission rp ON rp.role_id=r.id
            LEFT JOIN permission p ON p.id=rp.permission_id ORDER BY r.code,p.code
            """,(org.springframework.jdbc.core.RowCallbackHandler)rs->{
                var role=result.computeIfAbsent(rs.getString(1),k->{
                    try { return new UserCommands.Role(k,rs.getString(2),new ArrayList<>(),new ArrayList<>()); }
                    catch(SQLException error) { throw new IllegalStateException(error); }
                });
                if(rs.getString(3)!=null) { role.permissions().add(rs.getString(3));role.permissionNames().add(rs.getString(4)); }
            });
        return List.copyOf(result.values());
    }
    public void create(UUID id,UserCommands.Create input,String hash) {
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id),input.username(),input.displayName().strip(),hash);
        grants(id,input.roles(),input.warehouseIds());
    }
    public void update(UUID id,UserCommands.Update input) {
        jdbc.update("UPDATE app_user SET display_name=?,status=?,auth_version=auth_version+1 WHERE id=?",input.displayName().strip(),input.status(),Ids.bytes(id));
        grants(id,input.roles(),input.warehouseIds());revoke(id);
    }
    public void password(UUID id,String hash) {
        jdbc.update("UPDATE app_user SET password_hash=?,auth_version=auth_version+1 WHERE id=?",hash,Ids.bytes(id));revoke(id);
    }
    private void revoke(UUID id) {
        jdbc.update("UPDATE pda_session SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE user_id=?",Ids.bytes(id));
    }
    private void grants(UUID id,List<String> roles,List<UUID> warehouses) {
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(id));jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",Ids.bytes(id));
        for(String role:roles) jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code=?",Ids.bytes(id),role);
        for(UUID warehouse:warehouses) jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(warehouse));
    }
    private UserCommands.Item map(ResultSet rs,int index) throws SQLException {
        return new UserCommands.Item(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),List.of(),List.of());
    }
}
