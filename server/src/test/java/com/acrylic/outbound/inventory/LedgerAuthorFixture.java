package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real preparation/activation/ABORT source, with retained fresh EC identity for subsequent challenge login. */
final class LedgerAuthorFixture {
    final TemporaryFixture temp;final String submission=UUID.randomUUID().toString();final ObjectNode control;
    LedgerAuthorFixture(TemporaryFixture temp) throws Exception {
        this.temp=temp;temp.stock();var authorization=temp.prepare();
        temp.pda.data(temp.acknowledge(authorization,temp.ack(authorization),UUID.randomUUID().toString()),temp.tokens);
        control=SubmissionJson.object().putNull("assignmentId").putNull("continuationId")
            .put("authorizationId",authorization.get("authorizationId").asText()).put("freezeDeviceId",temp.device.id())
            .put("workRound","1").put("sourceRecoveryEpoch","1").put("reason","仅合成台账测试来源");
        control.putObject("source").put("kind","TEMP").put("creatorDeviceId",temp.device.id()).put("clientOrderId",UUID.randomUUID().toString());
        temp.pda.data(post("/api/v1/pda/submissions/"+submission+"/abort").header("X-Server-Recovery-Epoch","1")
            .contentType("application/json").content(control.toString()),temp.tokens);
        var directory=LedgerRuntimeEnvironment.ROOT.resolve("evidence/devices");Files.createDirectories(directory);
        Files.write(directory.resolve(temp.device.id()+".pk8"),temp.device.key().getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
    }
    ObjectNode command(CommandFacts facts,String kind) {
        var context=facts.read(UUID.fromString(submission));var command=SubmissionJson.object().put("commandVersion",1)
            .put("commandType",kind).put("commandId",UUID.randomUUID().toString()).put("warehouseId",temp.web.identity().warehouse().toString())
            .put("submissionId",submission).put("transportRecoveryEpoch","1").put("reason",kind.equals("NORMAL_CONFIRM")?"":"合成短装事务");
        for(String key:List.of("source","authorizationId","freezeDeviceId","workRound","sourceRecoveryEpoch"))command.set(key,control.get(key));
        command.set("expected",CommandExpected.current(context,"1"));return command;
    }
    void web() { authenticate(temp.web.identity().user()); }
    static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"unused",List.of()));
    }
    int count(String table) {
        String sql=switch(table) {
            case "request"->"SELECT COUNT(*) FROM api_idempotency_request WHERE route_scope='r06.command.v1' AND principal_id=UUID_TO_BIN(?)";
            case "receipt"->"SELECT COUNT(*) FROM temporary_command_receipt r JOIN api_idempotency_request q ON q.id=r.api_request_id WHERE q.principal_id=UUID_TO_BIN(?)";
            case "event"->"SELECT COUNT(*) FROM temporary_decision_event e JOIN temporary_command_receipt r ON r.id=e.receipt_id JOIN api_idempotency_request q ON q.id=r.api_request_id WHERE q.principal_id=UUID_TO_BIN(?)";
            case "audit"->"SELECT COUNT(*) FROM operation_event WHERE entity_type='TEMPORARY_COMMAND' AND actor_id=UUID_TO_BIN(?)";
            default->throw new IllegalArgumentException();
        };
        return temp.jdbc.queryForObject(sql,Integer.class,temp.web.identity().user().id().toString());
    }
}
