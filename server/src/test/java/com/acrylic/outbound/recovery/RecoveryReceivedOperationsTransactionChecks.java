package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.handover.HandoverRecoveredOperationsReview;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class RecoveryReceivedOperationsTransactionChecks {
    static RecoveryOperationsApproval.Context inspect(WebApplicationContext app,Path attempt,RecoveryReceivedPhysicalReviewChecks.Result input) throws Exception {
        var receipt=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("recovered-fault/approval-receipt.json")));
        var actor=app.getBean(IdentityStore.class).loadUserByUsername("recovered-"+receipt.path("actor").path("principalId").asText());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
        try { return app.getBean(HandoverRecoveredOperationsReview.class).inspect(input.request()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedPhysicalReviewChecks.Result input,
            RecoveryOperationsApproval.Request request) throws Exception {
        var receipt=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("recovered-fault/approval-receipt.json")));
        var actor=app.getBean(IdentityStore.class).loadUserByUsername("recovered-"+receipt.path("actor").path("principalId").asText());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
        try {
            var service=app.getBean(HandoverRecoveredOperationsReview.class);
            var verified=service.verify(request,input.reviewed(),input.request());
            assertThat(service.verify(request,input.reviewed(),input.request()).sha256()).isEqualTo(verified.sha256());
            var jdbc=app.getBean(JdbcTemplate.class);
            tails(jdbc,verified,input,request.file(),"RECOVERY_OPERATIONS_APPROVAL_PIN_MISMATCH");
            var identity=request.authority().identity();
            Path identityFile=request.file().getParent().resolve("identity.json");
            assertThat(Files.readAllBytes(identityFile)).isEqualTo(RecoveryControlJson.encode(identity));
            tails(jdbc,verified,input,identityFile,"RECOVERY_OPERATIONS_PIN_MISMATCH");
            assertThat(service.verify(request,input.reviewed(),input.request()).sha256()).isEqualTo(verified.sha256());
        } finally { SecurityContextHolder.clearContext(); }
    }
    private static void tails(JdbcTemplate jdbc,RecoveryOperationsApproval approval,RecoveryReceivedPhysicalReviewChecks.Result input,
            Path file,String code) throws Exception {
        byte[] original=Files.readAllBytes(file); var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var warehouses=jdbc.queryForList("SELECT id,name FROM warehouse ORDER BY id");
        try {
            assertThatThrownBy(()->tx.executeWithoutResult(status->{
                approval.revalidate(input.reviewed(),input.authority());
                assertThat(jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-operations-tail')")).isPositive();
                try { Files.writeString(file,"tampered"); } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            })).hasMessage(code);
        } finally { Files.write(file,original); }
        var after=jdbc.queryForList("SELECT id,name FROM warehouse ORDER BY id");
        for(int i=0;i<warehouses.size();i++) {
            assertThat((byte[])after.get(i).get("id")).isEqualTo((byte[])warehouses.get(i).get("id"));
            assertThat(after.get(i).get("name")).isEqualTo(warehouses.get(i).get("name"));
        }
    }
    private RecoveryReceivedOperationsTransactionChecks() { }
}
