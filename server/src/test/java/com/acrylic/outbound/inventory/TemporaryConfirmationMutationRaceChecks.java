package com.acrylic.outbound.inventory;

import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.users.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class TemporaryConfirmationMutationRaceChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationMutationRaceChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception { race("candidate");race("revoke"); }
    private void race(String kind) throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var admin=f.supervisor();
        var command=f.command("NORMAL_CONFIRM",actor);var probe=new TemporaryConfirmationRaceProbe(f);
        var service=f.service(new TemporaryConfirmationFixture.Assembly(f.bean(CommandReceiptCodec.class),f.bean(com.acrylic.outbound.audit.AuditWriter.class),
            probe.locks(),f.bean(TemporaryConfirmationSource.class),f.bean(com.acrylic.outbound.submissions.SubmissionAccounting.class)));
        var tx=new TransactionTemplate(f.bean(PlatformTransactionManager.class));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{
                TemporaryConfirmationFixture.authenticate(kind.equals("candidate")?actor:admin);
                try { return tx.execute(status->{if(kind.equals("candidate"))publish(f);else revoke(f);probe.holder();return true;}); }
                finally { SecurityContextHolder.clearContext(); }
            });
            TemporaryConfirmationRaceProbe.gate(probe.held);var before=f.state();
            var b=pool.submit(()->{Thread.currentThread().setName("confirmation-waiter");return f.execute(service,command,actor);});
            probe.waiting();probe.release.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isTrue();
            TemporaryConfirmationConcurrencyChecks.failure(b,kind.equals("candidate")?"COMMAND_CONTEXT_CHANGED":"SESSION_REVOKED");
            assertThat(f.state()).isEqualTo(before);probe.save(kind);
        } finally { probe.release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue(); }
    }
    private void publish(TemporaryConfirmationFixture f) {
        var warehouse=f.source.f.web.identity().warehouse();
        var base=f.bean(com.acrylic.outbound.inventory.master.WarehouseBaseline.class).read(warehouse,false);
        var plan=new OrderCommands.Plan("临时合成车001","","隔离竞争候选",UUID.fromString(base.activeSnapshotId()),base.mappingRevision(),List.of(new OrderCommands.Line("003",1)));
        var created=f.bean(OrderService.class).create(mutation(warehouse),plan);
        f.bean(OrderReleaseService.class).publish(mutation(warehouse),UUID.fromString(created.orderId()),new OrderReleaseService.Command("1","1","候选并发发布"));
    }
    private void revoke(TemporaryConfirmationFixture f) {
        var operator=f.operator();var before=f.bean(UserStore.class).find(operator.id());
        f.bean(UserService.class).update(mutation(f.source.f.web.identity().warehouse()),operator.id(),
            new UserCommands.Update(before.rowVersion(),before.displayName(),"ACTIVE",List.of("WAREHOUSE_ADMIN"),
                before.warehouses().stream().map(UserCommands.Warehouse::id).toList(),"隔离并发撤销临时确认权限"));
    }
    private MutationAccess.Context mutation(UUID warehouse) { return new MutationAccess.Context(warehouse,"1",UUID.randomUUID().toString(),"confirmation-mutation-race"); }
}
