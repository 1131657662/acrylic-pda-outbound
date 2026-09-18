package com.acrylic.outbound.users;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class UserService {
    private final UserAccess access;private final UserStore store;private final UserReceipts receipts;private final PasswordEncoder passwords;
    public UserService(UserAccess access,UserStore store,UserReceipts receipts,PasswordEncoder passwords) {
        this.access=access;this.store=store;this.receipts=receipts;this.passwords=passwords;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserCommands.Receipt create(MutationAccess.Context context,UserCommands.Create input) {
        var scope=access.lock(context);password(input.password());
        var metadata=Map.of("username",input.username(),"displayName",input.displayName(),"roles",input.roles(),"warehouseIds",input.warehouseIds(),"reason",input.reason());
        var ticket=receipts.prepare(context,scope.actor(),new UserReceipts.Payload("CREATE",metadata,input.password()));
        if(ticket.replay()!=null) return ticket.replay();
        access.grants(scope,context.warehouse(),new UserAccess.Grants(input.roles(),input.warehouseIds(),store.roles()));
        UUID id=Ids.next();String hash=passwords.encode(input.password());store.create(id,input,hash);
        return receipts.save(ticket,context,new UserReceipts.Change(null,store.find(id),input.reason(),hash));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserCommands.Receipt update(MutationAccess.Context context,UUID id,UserCommands.Update input) {
        var scope=access.lock(context);
        var ticket=receipts.prepare(context,scope.actor(),new UserReceipts.Payload("UPDATE",Map.of("id",id,"command",input),null));
        if(ticket.replay()!=null) return ticket.replay();
        var before=store.find(id);access.target(scope,before,context.warehouse());version(before,input.expectedVersion());
        if(scope.actor().equals(id)) throw new BusinessException(409,"USER_SELF_CHANGE_DENIED","不能在此修改本人资料、角色、仓库或启停状态，请由其他有权限管理员处理");
        access.grants(scope,context.warehouse(),new UserAccess.Grants(input.roles(),input.warehouseIds(),store.roles()));
        store.update(id,input);return receipts.save(ticket,context,new UserReceipts.Change(before,store.find(id),input.reason(),null));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserCommands.Receipt password(MutationAccess.Context context,UUID id,UserCommands.Password input) {
        var scope=access.lock(context);password(input.password());
        var metadata=Map.of("id",id,"expectedVersion",input.expectedVersion(),"reason",input.reason());
        var ticket=receipts.prepare(context,scope.actor(),new UserReceipts.Payload("PASSWORD",metadata,input.password()));
        if(ticket.replay()!=null) return ticket.replay();
        var before=store.find(id);access.target(scope,before,context.warehouse());version(before,input.expectedVersion());
        String hash=passwords.encode(input.password());store.password(id,hash);
        return receipts.save(ticket,context,new UserReceipts.Change(before,store.find(id),input.reason(),hash));
    }
    private void password(String value) {
        if(value.getBytes(StandardCharsets.UTF_8).length>72) throw new BusinessException(400,"INVALID_REQUEST","密码不能超过72个UTF-8字节");
    }
    private void version(UserCommands.Item before,String expected) {
        if(!before.rowVersion().equals(expected)) throw new BusinessException(409,"USER_VERSION_CONFLICT","人员资料已变化，请刷新后重新核对");
    }
}
