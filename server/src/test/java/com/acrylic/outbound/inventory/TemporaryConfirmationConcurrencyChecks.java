package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class TemporaryConfirmationConcurrencyChecks {
    private final WebApplicationContext context;
    TemporaryConfirmationConcurrencyChecks(WebApplicationContext context) { this.context=context; }
    void verify() throws Exception {
        for(String variant:List.of("same-key","different-content","different-key"))race(variant);
        new TemporaryConfirmationMutationRaceChecks(context).verify();
    }
    private void race(String variant) throws Exception {
        var f=new TemporaryConfirmationFixture(context,false);var actor=f.operator();var probe=new TemporaryConfirmationRaceProbe(f);
        var first=f.command("NORMAL_CONFIRM",actor);var second=first.deepCopy();
        if(variant.equals("different-content"))second.put("reason","同键异文");
        if(variant.equals("different-key"))second.put("commandId",UUID.randomUUID().toString());
        var codec=new CommandReceiptCodec(f.bean(OfflineSigner.class)) {
            @Override public Receipt sign(CommandWrite write,JsonNode decision) { var receipt=super.sign(write,decision);probe.holder();return receipt; }
        };
        var service=f.service(new TemporaryConfirmationFixture.Assembly(codec,f.bean(com.acrylic.outbound.audit.AuditWriter.class),
            probe.locks(),f.bean(TemporaryConfirmationSource.class),f.bean(com.acrylic.outbound.submissions.SubmissionAccounting.class)));
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->f.execute(service,first,actor));TemporaryConfirmationRaceProbe.gate(probe.held);
            var b=pool.submit(()->{Thread.currentThread().setName("confirmation-waiter");return f.execute(service,second,actor);});
            probe.waiting();probe.release.countDown();var original=a.get(15,TimeUnit.SECONDS);
            if(variant.equals("same-key"))assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(original);
            else failure(b,variant.equals("different-content")?"IDEMPOTENCY_CONTENT_MISMATCH":"COMMAND_CONTEXT_CHANGED");
            assertThat(f.execute(first,actor)).isEqualTo(original);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id())).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.id())).isEqualTo(2);
            probe.save(variant);
        } finally { probe.release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue(); }
    }
    static void failure(Future<?> result,String code) {
        assertThatThrownBy(()->result.get(15,TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class,
            error->assertThat(error.getCause()).isInstanceOfSatisfying(BusinessException.class,business->assertThat(business.code()).isEqualTo(code)));
    }
}
