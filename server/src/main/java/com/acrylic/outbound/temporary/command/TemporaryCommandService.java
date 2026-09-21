package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Owns the outer commit boundary; no controller exposes command execution in this batch. */
@Service
public class TemporaryCommandService {
    private final CommandIdentity identities;private final CommandReplay replay;private final CommandUnitOfWork work;
    private final TransactionTemplate read;private final TransactionTemplate write;
    public TemporaryCommandService(CommandIdentity identities,CommandReplay replay,CommandUnitOfWork work,PlatformTransactionManager manager) {
        this.identities=identities;this.replay=replay;this.work=work;
        read=template(manager,true);write=template(manager,false);
    }
    public byte[] execute(byte[] utf8,String requestId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Command execution must own the outer transaction boundary");
        if(requestId==null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400,"INVALID_REQUEST_ID","请求追踪标识无效");
        var input=CommandInput.parse(utf8);
        byte[] committed=read.execute(s->replay.existing(identities.current(input.uuid("warehouseId")),input));
        if(committed!=null)return committed;
        try { return Objects.requireNonNull(write.execute(s->work.execute(input,requestId))); }
        catch(DuplicateKeyException collision) {
            byte[] original=read.execute(s->replay.existing(identities.current(input.uuid("warehouseId")),input));
            if(original!=null)return original;
            throw new BusinessException(409,"COMMAND_RESULT_UNKNOWN","原键提交结果不可确认，请保留原意图并查询");
        }
    }
    private TransactionTemplate template(PlatformTransactionManager manager,boolean readonly) {
        var tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setReadOnly(readonly);return tx;
    }
}
