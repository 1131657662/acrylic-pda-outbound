package com.acrylic.outbound.inventory;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

/** Explicit test assembly only: application production operations remain empty. */
final class TemporaryConfirmationFixture {
    final WebApplicationContext context;final JdbcTemplate jdbc;final TemporarySubmissionFixture source;
    final TemporaryAccountingChecks transport;final TemporaryCommandService service;
    record Setup(boolean shortShipment,boolean deduct,Consumer<TemporarySubmissionFixture> prepare) { }

    TemporaryConfirmationFixture(WebApplicationContext context,boolean shortShipment) throws Exception {
        this(context,new Setup(shortShipment,true,source->{}));
    }
    TemporaryConfirmationFixture(WebApplicationContext context,boolean shortShipment,Consumer<TemporarySubmissionFixture> prepare) throws Exception {
        this(context,new Setup(shortShipment,true,prepare));
    }
    TemporaryConfirmationFixture(WebApplicationContext context,Setup setup) throws Exception {
        this.context=context;jdbc=bean(JdbcTemplate.class);transport=new TemporaryAccountingChecks(context);
        source=transport.fixture(setup.deduct());
        // Keep the fixture's distinct old formal order outside the documented candidate window.
        jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) WHERE id=UUID_TO_BIN(?)",source.f.order);
        if(setup.shortShipment()) {
            ((ObjectNode)source.body.at("/temporaryOrder/lines/0")).put("plannedCount","2");
            source.body.put("planSha256",SubmissionJson.sha(source.body.path("temporaryOrder"))).put("shortShipmentReason","原车实际短装");
        }
        setup.prepare().accept(source);transport.http(source,"/pda/temporary-orders/submissions",source.body);
        grant(operator(),List.of("temporary_order:confirm","submission:review"));
        service=service(bean(CommandReceiptCodec.class),bean(AuditWriter.class));
    }
    TemporaryCommandService service(CommandReceiptCodec codec,AuditWriter audit) {
        return service(codec,audit,bean(SubmissionAccounting.class));
    }
    TemporaryCommandService service(CommandReceiptCodec codec,AuditWriter audit,SubmissionAccounting accounting) {
        return service(new Assembly(codec,audit,bean(CommandLocks.class),bean(TemporaryConfirmationSource.class),accounting));
    }
    record Assembly(CommandReceiptCodec codec,AuditWriter audit,CommandLocks locks,TemporaryConfirmationSource source,SubmissionAccounting accounting) { }
    TemporaryCommandService service(Assembly components) {
        var dependencies=new TemporaryConfirmationOperation.Dependencies(components.source(),
            bean(TemporaryDecisionCandidates.class),bean(TemporaryShortApprovals.class),bean(SubmissionStock.class),components.accounting());
        List<CommandOperation> operations=List.of(new TemporaryConfirmationOperation("NORMAL_CONFIRM",dependencies),
            new TemporaryConfirmationOperation("SHORT_APPROVE",dependencies));
        return assemble(components,operations);
    }
    TemporaryCommandService assemble(Assembly components,List<CommandOperation> operations) {
        var work=new CommandUnitOfWork(bean(CommandIdentity.class),bean(CommandFacts.class),components.locks(),bean(CommandReplay.class),
            bean(ServerState.class),bean(CommandStore.class),components.codec(),components.audit(),operations,bean(CommandSyntheticEligibility.class));
        return new TemporaryCommandService(bean(CommandIdentity.class),bean(CommandReplay.class),work,bean(PlatformTransactionManager.class));
    }
    ObjectNode command(String kind,SessionUser actor) {
        var facts=bean(CommandFacts.class).read(UUID.fromString(source.id()));
        var result=SubmissionJson.object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("transportRecoveryEpoch","1").put("reason",kind.equals("NORMAL_CONFIRM")?"":"主管核对原短装");
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))result.set(key,facts.json().get(key));
        result.set("source",source.body.path("source"));
        var expected=CommandExpected.current(facts,Long.toString(actor.authVersion()));
        expected.set("candidateContextSha256",bean(TemporaryDecisionCandidates.class)
            .snapshot(facts.id("warehouseId"),facts.id("submissionId"),source.body).get("contextSha256"));
        result.set("expected",expected);return result;
    }
    byte[] execute(ObjectNode command,SessionUser actor) {
        return execute(service,command,actor);
    }
    byte[] execute(TemporaryCommandService target,ObjectNode command,SessionUser actor) {
        authenticate(actor);try { return target.execute(bytes(command),"internal-confirm-check"); }
        finally { SecurityContextHolder.clearContext(); }
    }
    SessionUser operator() { return source.f.web.identity().user(); }
    SessionUser supervisor() {
        var identity=IdentityFixture.create(jdbc,bean(org.springframework.security.crypto.password.PasswordEncoder.class),"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(source.f.web.identity().warehouse()));
        grant(identity.user(),List.of("temporary_order:confirm","submission:review"));
        return identity.user();
    }
    void only(SessionUser actor,List<String> permissions) {
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(actor.id()));grant(actor,permissions);
    }
    private void grant(SessionUser actor,List<String> permissions) {
        UUID role=Ids.next();jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"CONF_"+role.toString().substring(20),"隔离权限");
        for(String p:permissions)jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),p);
        jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.id()),Ids.bytes(role));
    }
    Map<String,Object> state() { return TemporaryConfirmationState.read(this); }
    CommandReadService.View read(ObjectNode command,SessionUser actor) {
        authenticate(actor);try { return bean(CommandReadService.class).read(source.f.web.identity().warehouse(),command.path("commandId").asText()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    <T>T bean(Class<T> type) { return context.getBean(type); }
    static byte[] bytes(ObjectNode command) { return SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8); }
    static void authenticate(SessionUser actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"unused",List.of()));
    }
}
