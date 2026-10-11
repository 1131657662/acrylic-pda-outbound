package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="outbound.recall.fault-withdrawal-enabled=true") @AutoConfigureMockMvc
class FaultRestartApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private FaultRestartFixture fixture(boolean shortPlan) throws Exception { return new FaultRestartFixture(mvc,json,jdbc,passwords,shortPlan); }

    @Test void independentNewDeviceStartsWithEmptyWorkAndOnlyItsOwnDispatch() throws Exception {
        var f=fixture(false);var receipt=f.withdraw(f.withdrawal(false),UUID.randomUUID().toString());
        assertThat(receipt.path("restartRevision").asInt()).isEqualTo(2);
        f.publish(UUID.randomUUID().toString());f.claim();
        assertThat(f.assignment.path("id")).isNotEqualTo(f.source.assignment.path("id"));
        assertThat(f.assignment.path("deviceId")).isNotEqualTo(f.source.assignment.path("deviceId"));
        assertThat(f.assignment.path("startOrderScanSeq").asText()).isEqualTo("1");
        assertThat(f.assignment.path("revision").asInt()).isEqualTo(2);
        var body=f.body();var result=f.submit(body);
        assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");assertThat(f.submit(body)).isEqualTo(result);
        assertThat(f.source.count("dispatch_record")).isEqualTo(2);assertThat(f.source.count("inventory_ledger")).isEqualTo(2);
        f.source.send(f.source.body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_WITHDRAWN"));
        assertThat(f.source.count("order_change_request")).isEqualTo(1);
        assertThat(f.source.count("order_publication")).isEqualTo(2);
    }

    @Test void receivedReservationsRequirePhysicalProofAndRetainOriginalFacts() throws Exception {
        var f=fixture(true);var original=f.source.body.deepCopy();f.source.submit();
        var receipt=f.withdraw(f.withdrawal(true),UUID.randomUUID().toString());
        assertThat(receipt.path("stockReleased").asBoolean()).isTrue();assertThat(receipt.path("releasedBales").size()).isEqualTo(2);
        assertThat(f.source.count("bale_commitment")).isZero();assertThat(f.source.count("dispatch_record")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fault_withdrawal_stock WHERE request_id=?",Integer.class,
            Ids.bytes(UUID.fromString(receipt.path("changeRequestId").asText())))).isEqualTo(2);
        assertThat(f.source.body).isEqualTo(original);
        f.publish(UUID.randomUUID().toString());f.claim();assertThat(f.assignment.path("workRound").asText()).isEqualTo("2");
        var body=f.body();assertThat(f.submit(body).path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(f.approve(body).path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(f.source.count("dispatch_record")).isEqualTo(2);assertThat(f.source.count("inventory_ledger")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(f.source.order)))).isEqualTo(4);
    }

    @Test void tailFailureRestoresStockOldRoundAndPublication() throws Exception {
        var f=fixture(true);f.source.submit();var command=f.withdrawal(true);String key=UUID.randomUUID().toString();
        String trigger="fault_restart_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN "+
            "IF NEW.route_scope='order.fault-withdrawal' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fault-restart-tail'; END IF; END");
        try { f.source.web.send(f.orders.command("/"+f.source.order+"/change-requests",command,key)).andExpect(status().is5xxServerError()); }
        finally { jdbc.execute("DROP TRIGGER "+trigger); }
        assertThat(f.source.count("bale_commitment")).isEqualTo(2);assertThat(f.source.count("order_revision")).isEqualTo(1);
        assertThat(f.source.count("order_change_request")).isZero();
        assertThat(f.orders.detail(f.source.order).at("/order/status").asText()).isEqualTo("REVIEW_REQUIRED");
        var receipt=f.withdraw(command,key);assertThat(receipt.path("stockReleased").asBoolean()).isTrue();
        assertThat(f.withdraw(command,key)).isEqualTo(receipt);assertThat(f.source.count("order_revision")).isEqualTo(2);
    }
}
