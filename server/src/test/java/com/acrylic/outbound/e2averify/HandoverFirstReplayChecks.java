package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** First delivery and restart assertions now start from authentic stop/abort evidence. */
final class HandoverFirstReplayChecks {
    private final HandoverRollbackFixture fixture;
    private final HandoverRollbackCommands commands;
    private final HandoverRollbackFacts facts;
    private final java.nio.file.Path attempt;
    private final ObjectNode command;
    private ObjectNode before, committed;
    private byte[] original;
    HandoverFirstReplayChecks(ConfigurableApplicationContext context, java.nio.file.Path attempt) {
        this.attempt=attempt;fixture=new HandoverRollbackFixture(context);commands=commands(context);
        commands.prepare();command=commands.activate();facts=new HandoverRollbackFacts(fixture,fixture.jdbc);
    }
    void firstDeliveryRace() throws Exception {
        before=facts.snapshot();write("before",before);write("before-summary",facts.summary());write("command",command);
        assertThat(facts.summary().path("activationReceipts").asLong()).isZero();
        var results=new HandoverFirstReplayRace(fixture,attempt).execute(()->commands.call(command,fixture.approver));
        var outcomes=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        for(int i=0;i<results.size();i++) {
            var result=results.get(i);outcomes.addObject().put("worker",i).put("code",result.code());
            if(result.response()!=null)java.nio.file.Files.write(attempt.resolve("first-replay-response-"+i+".json"),result.response());
        }
        java.nio.file.Files.writeString(attempt.resolve("first-replay-outcomes.json"),outcomes.toPrettyString());
        committed=facts.snapshot();write("committed",committed);write("committed-summary",facts.summary());assertSingleActivation();
        assertThat(results).extracting(HandoverFirstReplayRace.Outcome::code).containsExactly("OK","OK");
        original=results.getFirst().response();assertThat(results.getLast().response()).isEqualTo(original);
        assertThat(commands.call(command,fixture.approver)).isEqualTo(original);assertThat(facts.snapshot()).isEqualTo(committed);
    }
    void replayAfterRestart(ConfigurableApplicationContext context) throws Exception {
        assertThat(commands(context).call(command,fixture.approver)).isEqualTo(original);
        var restarted=new HandoverRollbackFacts(fixture,context.getBean(JdbcTemplate.class)).snapshot();
        write("restarted",restarted);assertThat(restarted).isEqualTo(committed);
    }
    private void assertSingleActivation() {
        var summary=facts.summary();assertThat(summary.path("caseStatus").asText()).isEqualTo("ACTIVATED");
        for(String field:java.util.List.of("activeAssignments","oldRevoked","outbox","pendingOutbox","activationReceipts","activationAudits","retained"))
            assertThat(summary.path(field).asLong()).as(field).isEqualTo(1);
        for(String field:java.util.List.of("scopeRound","slotRound"))assertThat(summary.path(field).asLong()).as(field).isEqualTo(2);
        assertThat(committed.path("bale_commitment")).isEqualTo(before.path("bale_commitment"));
    }
    private HandoverRollbackCommands commands(ConfigurableApplicationContext context) {
        return new HandoverRollbackCommands(fixture,context.getBean(HandoverStore.class),context.getBean(HandoverCommandService.class));
    }
    private void write(String name,ObjectNode value)throws Exception {
        java.nio.file.Files.writeString(attempt.resolve("first-replay-"+name+".json"),value.toPrettyString());
    }
}
