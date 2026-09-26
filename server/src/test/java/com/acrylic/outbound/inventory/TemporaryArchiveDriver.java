package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

/** Test assembly only; none of these operations are registered on a production route. */
public final class TemporaryArchiveDriver {
    private final WebApplicationContext context;
    public TemporaryArchiveDriver(WebApplicationContext context) { this.context=context; }
    public TemporaryArchiveService service(SubmissionArchiveStore archives,SubmissionArchiveProofs proofs) {
        var evidence=new TemporaryArchiveEvidence(bean(SubmissionFiles.class),bean(SubmissionStore.class),bean(JdbcTemplate.class),
            bean(CommandStore.class),bean(CommandReceiptIntegrity.class));
        return new TemporaryArchiveService(new TemporaryArchiveService.Dependencies(bean(SubmissionAccess.class),bean(SubmissionStore.class),
            archives,evidence,proofs),bean(PlatformTransactionManager.class));
    }
    public JsonNode archive(UUID id,String token,JsonNode request) {
        return archive(service(bean(SubmissionArchiveStore.class),bean(SubmissionArchiveProofs.class)),id,token,"1",request);
    }
    JsonNode archive(TemporaryArchiveService service,UUID id,String token,String epoch,JsonNode request) {
        var actor=bean(PdaSessions.class).authenticate(token);authenticate(actor);
        try { return service.confirm(id,epoch,request); } finally { SecurityContextHolder.clearContext(); }
    }
    public JsonNode confirm(UUID id,String token) {
        var principal=bean(PdaSessions.class).authenticate(token);
        var actor=bean(IdentityStore.class).loadUserByUsername(principal.username());
        var facts=bean(CommandFacts.class).read(id);var body=SubmissionJson.parse(bean(SubmissionFiles.class).read(facts.json().path("receivedPayloadSha256").asText()));
        var command=SubmissionJson.object().put("commandVersion",1).put("commandType","NORMAL_CONFIRM")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","");
        for(String field:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))command.set(field,facts.json().get(field));
        command.set("source",body.path("source"));var expected=CommandExpected.current(facts,Long.toString(actor.authVersion()));
        expected.set("candidateContextSha256",bean(TemporaryDecisionCandidates.class).snapshot(facts.id("warehouseId"),id,body).get("contextSha256"));
        command.set("expected",expected);authenticate(actor);
        try { return SubmissionJson.parse(commands().execute(TemporaryConfirmationFixture.bytes(command),"android-confirm-chain")); }
        finally { SecurityContextHolder.clearContext(); }
    }
    public void grantTestConfirmation(String token) {
        var actor=bean(PdaSessions.class).authenticate(token);UUID role=Ids.next();var jdbc=bean(JdbcTemplate.class);
        jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"ARC_"+role.toString().substring(20),"隔离确认测试");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:confirm'",Ids.bytes(role));
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.userId()),Ids.bytes(role));
    }
    private TemporaryCommandService commands() {
        var dependencies=new TemporaryConfirmationOperation.Dependencies(bean(TemporaryConfirmationSource.class),bean(TemporaryDecisionCandidates.class),
            bean(TemporaryShortApprovals.class),bean(SubmissionStock.class),bean(SubmissionAccounting.class));
        var work=new CommandUnitOfWork(bean(CommandIdentity.class),bean(CommandFacts.class),bean(CommandLocks.class),bean(CommandReplay.class),
            bean(ServerState.class),bean(CommandStore.class),bean(CommandReceiptCodec.class),bean(AuditWriter.class),
            List.of(new TemporaryConfirmationOperation("NORMAL_CONFIRM",dependencies)),bean(CommandSyntheticEligibility.class));
        return new TemporaryCommandService(bean(CommandIdentity.class),bean(CommandReplay.class),work,bean(PlatformTransactionManager.class));
    }
    private void authenticate(Object principal) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"unused",List.of())); }
    private <T>T bean(Class<T> type) { return context.getBean(type); }
}
