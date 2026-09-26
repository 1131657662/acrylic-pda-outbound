package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Test-only internal accounting; no production confirmation operation is registered. */
public final class TemporaryAccountingChecks {
    private final WebApplicationContext context;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TemporaryAccountingChecks(WebApplicationContext context) {
        this.context = context; jdbc = context.getBean(JdbcTemplate.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    public void verify() throws Exception {
        assertThat(context.getBeansOfType(com.acrylic.outbound.temporary.command.CommandOperation.class)).isEmpty();
        success(false); success(true);
        new TemporaryAccountingFailureChecks(context).verify();
        formal(false); formal(true);
        continuation();
    }

    private void success(boolean deduct) throws Exception {
        var fixture = fixture(deduct);
        var pending = http(fixture, "/pda/temporary-orders/submissions", fixture.body);
        assertThat(pending.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        new OrderTestDriver(fixture.f.web).policy("1", !deduct);
        String unrelated = jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=UUID_TO_BIN(?)",
            String.class, fixture.f.assignment.path("id").asText());
        var receipt = transaction.execute(status -> commit(fixture));
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("inventoryDeducted").asBoolean()).isEqualTo(deduct);
        assertThat(http(fixture, "/pda/submissions/" + fixture.id(), null)).isEqualTo(receipt);
        assertThat(http(fixture, "/pda/temporary-orders/submissions", fixture.body)).isEqualTo(receipt);
        assertThat(http(fixture, "/pda/submissions/" + fixture.id() + "/abort",
            fixture.control().put("reason", "成功后迟到取消"))).isEqualTo(receipt);
        context.getBean(SubmissionArchiveProofs.class).verify(receipt, "signedReceipt", "acrylic-pda-receipt", "receipt");
        assertThat(receipt.path("source")).isEqualTo(fixture.body.path("source"));
        assertThat(receipt.path("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(fixture.body));
        assertThat(receipt.path("actualCount").asText()).isEqualTo("2");
        assertThat(receipt.path("actualWeightGrams").asText()).isEqualTo("601500");
        assertThatThrownBy(() -> transaction.execute(status -> commit(fixture)))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class)
            .extracting(error -> ((com.acrylic.outbound.common.BusinessException) error).code())
            .isEqualTo("TEMPORARY_ACCOUNTING_CONTEXT_CHANGED");
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=UUID_TO_BIN(?)", String.class,
            fixture.f.assignment.path("id").asText())).isEqualTo(unrelated);
        successRows(fixture, deduct);
    }

    private void successRows(TemporarySubmissionFixture fixture, boolean deduct) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, fixture.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id "
            + "WHERE d.submission_id=UUID_TO_BIN(?)", Integer.class, fixture.id())).isEqualTo(deduct ? 2 : 0);
        assertThat(jdbc.queryForList("SELECT state FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?)",
            String.class, fixture.id())).containsExactly("DISPATCHED", "DISPATCHED");
        assertThat(jdbc.queryForList("SELECT p.book_status FROM inventory_position p JOIN dispatch_record d ON d.bale_id=p.bale_id "
            + "WHERE d.submission_id=UUID_TO_BIN(?)", String.class, fixture.id()))
            .containsExactly(deduct ? "DEDUCTED" : "NOT_DEDUCTED", deduct ? "DEDUCTED" : "NOT_DEDUCTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, fixture.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT phase FROM order_submission_slot WHERE current_submission_id=UUID_TO_BIN(?)",
            String.class, fixture.id())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=(SELECT canonical_order_id FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)) "
            + "AND event_type='SUBMISSION_COMPLETED'", Integer.class, fixture.id())).isEqualTo(1);
    }

    JsonNode commit(TemporarySubmissionFixture fixture) {
        var store = context.getBean(SubmissionStore.class);
        var head = store.lockExisting(store.required(SubmissionJson.uuid(fixture.body, "submissionId")));
        var plan = context.getBean(TemporarySubmissionPlan.class).validate(head, fixture.body);
        var value = context.getBean(TemporarySubmissionSnapshot.class).validate(plan, fixture.body);
        return context.getBean(SubmissionAccounting.class).commit(head, fixture.body, value, head.actor());
    }

    TemporarySubmissionFixture fixture(boolean deduct) throws Exception {
        return new TemporarySubmissionFixture(formalFixture(deduct));
    }

    private SubmissionFixture formalFixture(boolean deduct) throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        var identity = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        var web = new PublicationDriver(mvc, context.getBean(ObjectMapper.class), identity);
        return new SubmissionFixture(web, jdbc, deduct, false);
    }

    JsonNode http(TemporarySubmissionFixture fixture, String path, JsonNode body) throws Exception {
        return http(fixture.f.tokens.path("accessToken").asText(), path, body);
    }

    private void formal(boolean deduct) throws Exception {
        var f = formalFixture(deduct); var token = f.tokens.path("accessToken").asText();
        var receipt = http(token, "/pda/orders/" + f.order + "/submissions", f.body);
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("inventoryDeducted").asBoolean()).isEqualTo(deduct);
        assertThat(http(token, "/pda/orders/" + f.order + "/submissions", f.body)).isEqualTo(receipt);
        assertThat(f.count("dispatch_record")).isEqualTo(2);
        assertThat(f.count("inventory_ledger")).isEqualTo(deduct ? 2 : 0);
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=UUID_TO_BIN(?)", String.class,
            f.assignment.path("id").asText())).isEqualTo("CLOSED");
    }

    private void continuation() throws Exception {
        var c = new ContinuationFixture(formalFixture(true));
        c.request(); c.approve(); c.activate(); var body = c.second();
        var receipt = http(c.r.f.tokens.path("accessToken").asText(), "/pda/orders/" + c.r.f.order + "/submissions", body);
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(c.r.f.count("dispatch_record")).isEqualTo(2);
        assertThat(c.r.f.count("inventory_ledger")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM order_continuation WHERE id=UUID_TO_BIN(?)", String.class,
            body.path("continuationId").asText())).isEqualTo("COMPLETED");
    }

    private JsonNode http(String token, String path, JsonNode body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path))
            .header("Authorization", "Bearer " + token)
            .header("X-Server-Recovery-Epoch", "1");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)));
        var response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("HTTP %s: %s", path, response.body()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
}
