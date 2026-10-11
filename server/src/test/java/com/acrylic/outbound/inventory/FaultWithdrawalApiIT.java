package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="outbound.recall.fault-withdrawal-enabled=true") @AutoConfigureMockMvc
class FaultWithdrawalApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    private SubmissionFixture fixture(boolean shortPlan) throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new SubmissionFixture(web,jdbc,true,shortPlan);
    }

    private ObjectNode command(SubmissionFixture f) throws Exception {
        return SubmissionJson.object().put("type","FAULT_WITHDRAWAL").put("reason","网卡故障，原机离线，撤回后重新核对实物作业")
            .put("expectedVersion",new OrderTestDriver(f.web).detail(f.order).at("/order/rowVersion").asText());
    }

    private MockHttpServletRequestBuilder request(SubmissionFixture f,ObjectNode command,String key) {
        return post("/api/v1/orders/"+f.order+"/change-requests").header("Idempotency-Key",key)
            .contentType("application/json").content(command.toString());
    }

    private int submissionCount(SubmissionFixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id WHERE c.source_order_id=?",
            Integer.class,Ids.bytes(UUID.fromString(f.order)));
    }

    @Test void offlineFrozenWorkCanBeWithdrawnWithoutOriginalStop() throws Exception {
        var f=fixture(false);var original=f.body.deepCopy();
        var receipt=f.web.data(request(f,command(f),UUID.randomUUID().toString()));

        assertThat(receipt.get("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(receipt.get("originalDeviceStopReceived").asBoolean()).isFalse();
        assertThat(receipt.get("mayResumeScanning").asBoolean()).isFalse();
        assertThat(new OrderTestDriver(f.web).detail(f.order).at("/order/status").asText()).isEqualTo("DRAFT");
        assertThat(submissionCount(f)).isZero();assertThat(f.count("dispatch_record")).isZero();
        assertThat(jdbc.queryForObject("SELECT stop_receipt FROM order_change_request WHERE id=?",String.class,
            Ids.bytes(UUID.fromString(receipt.get("changeRequestId").asText())))).isNull();
        assertThat(f.body).isEqualTo(original);
        f.send(f.body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_WITHDRAWN"));
        assertThat(submissionCount(f)).isZero();
    }

    @Test void replayReturnsOriginalSignedReceiptAndChangedCommandIsRejected() throws Exception {
        var f=fixture(false);var command=command(f);String key=UUID.randomUUID().toString();
        var receipt=f.web.data(request(f,command,key));
        assertThat(f.web.data(request(f,command,key))).isEqualTo(receipt);
        f.web.send(request(f,command.deepCopy().put("reason","另一理由"),key)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        assertThat(f.count("order_change_request")).isEqualTo(1);
    }

    @Test void staleVersionCannotRevokeTheOriginalCredential() throws Exception {
        var f=fixture(false);
        f.web.send(request(f,command(f).put("expectedVersion","1"),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));
        assertThat(f.count("order_change_request")).isZero();
        assertThat(f.submit().get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test void successfulDispatchCannotBeWithdrawn() throws Exception {
        var f=fixture(false);var receipt=f.submit();
        f.web.send(request(f,command(f),UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
        assertThat(f.submit()).isEqualTo(receipt);assertThat(f.count("dispatch_record")).isEqualTo(2);
        assertThat(f.count("inventory_ledger")).isEqualTo(2);assertThat(f.count("order_change_request")).isZero();
    }

    @Test void receivedStockIsPreservedUntilItsControlledReleaseConsumerExists() throws Exception {
        var f=fixture(true);assertThat(f.submit().get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        f.web.send(request(f,command(f),UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FAULT_WITHDRAWAL_STOCK_REQUIRED"));
        assertThat(f.count("bale_commitment")).isEqualTo(2);assertThat(f.count("order_change_request")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,Ids.bytes(UUID.fromString(f.id()))))
            .isEqualTo("REVIEW_REQUIRED");
    }

    @Test void warehouseAndRecallPermissionAreRequired() throws Exception {
        var f=fixture(false);var outsider=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        outsider.send(request(f,command(f),UUID.randomUUID().toString())).andExpect(status().isNotFound());
        var operator=IdentityFixture.create(jdbc,passwords,"PDA_OPERATOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(operator.user().id()),Ids.bytes(f.web.identity().warehouse()));
        var limited=new PublicationDriver(mvc,json,new IdentityFixture(operator.user(),f.web.identity().warehouse()));
        limited.send(request(f,command(f),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(f.count("order_change_request")).isZero();
    }

    @Test void finalIdempotencyWriteFailureRollsBackRevocationAndOutbox() throws Exception {
        var f=fixture(false);var command=command(f);String key=UUID.randomUUID().toString();
        int before=f.count("order_sync_outbox");String trigger="fault_tail_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN "+
            "IF NEW.route_scope='order.fault-withdrawal' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fault-final-write'; END IF; END");
        try { f.web.send(request(f,command,key)).andExpect(status().is5xxServerError()); }
        finally { jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(f.count("order_change_request")).isZero();assertThat(f.count("order_sync_outbox")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?",String.class,
            Ids.bytes(UUID.fromString(f.assignment.get("id").asText())))).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM offline_authorization WHERE id=?",java.sql.Timestamp.class,
            Ids.bytes(UUID.fromString(f.assignment.get("offlineAuthorizationId").asText())))).isNull();
        assertThat(f.web.data(request(f,command,key)).get("status").asText()).isEqualTo("WITHDRAWN");
    }
}
