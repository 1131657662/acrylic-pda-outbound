package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.RequestIdFilter;
import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

/** Test-only facade over the established synthetic fixture; commands use the real HTTP entry. */
public final class FaultWithdrawalRecoveryScenario {
    private final FaultWithdrawalHttp http;
    private final UUID withdrawalKey = UUID.randomUUID(), publishKey = UUID.randomUUID();
    private JsonNode withdrawal, withdrawalReceipt, publication, publicationReceipt, submitted, success;

    public FaultWithdrawalRecoveryScenario(WebApplicationContext context) throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
        var fixture = new FaultRestartFixture(mvc, context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            context.getBean(JdbcTemplate.class), context.getBean(PasswordEncoder.class), false);
        http = new FaultWithdrawalHttp(context, fixture);
    }

    public void withdraw() throws Exception {
        withdrawal = http.f.withdrawal(false);
        withdrawalReceipt = http.data(http.withdraw(withdrawal, withdrawalKey), 200);
        assertThat(withdrawalReceipt.path("originalDeviceStopReceived").asBoolean()).isFalse();
        assertThat(http.submit(http.f.source.body, http.f.source.tokens).statusCode()).isEqualTo(409);
    }

    public void publish() throws Exception {
        publication = http.f.orders.release(http.f.version(), "1");
        publicationReceipt = http.data(http.web.post(orderPath("/publish"), publication, publishKey), 200);
    }

    public void claim() throws Exception { http.claimAndDownload(); submitted = http.f.body(); }

    public void succeed() throws Exception {
        success = http.data(http.submit(submitted, http.f.tokens), 200);
        assertThat(success.path("status").asText()).isEqualTo("SUCCEEDED");
    }

    public void save(Path path) throws Exception {
        var input = SubmissionJson.object().put("orderId", http.f.source.order)
            .put("withdrawalKey", withdrawalKey.toString()).put("publishKey", publishKey.toString());
        input.set("withdrawal", withdrawal); input.set("withdrawalReceipt", withdrawalReceipt);
        input.set("publication", publication); input.set("publicationReceipt", publicationReceipt);
        input.set("submission", submitted); input.set("success", success);
        input.set("oldSubmission", http.f.source.body); input.set("oldTokens", http.f.source.tokens);
        input.set("newTokens", http.f.tokens);
        Files.writeString(path, input.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    public void verifyRestored(WebApplicationContext context, Path evidence) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var original = http.f.source;
        var scope = new ExportModels.Scope(original.web.identity().user().id(), original.web.identity().warehouse(),
            UUID.fromString(original.order));
        var web = CorrectionExportHttpClient.login(context, scope);
        var before = accounting(jdbc);
        var receipt = http.data(web.post(orderPath("/change-requests"), withdrawal, withdrawalKey), 200);
        assertThat(receipt).isEqualTo(withdrawalReceipt);
        assertThat(http.data(web.post(orderPath("/publish"), publication, publishKey), 200)).isEqualTo(publicationReceipt);
        assertThat(http.data(http.submit(submitted, http.f.tokens), 200)).isEqualTo(success);
        assertThat(http.data(http.submit(submitted, http.f.tokens), 200)).isEqualTo(success);
        assertThat(http.submit(original.body, original.tokens).statusCode()).isEqualTo(409);
        assertThat(http.get("/pda/assignments/" + original.assignment.path("id").asText()
            + "/content/plan?bundleId=" + UUID.randomUUID(), original.tokens).statusCode()).isEqualTo(409);
        assertThat(web.post(orderPath("/change-requests"), ((com.fasterxml.jackson.databind.node.ObjectNode) withdrawal)
            .deepCopy().put("reason", "异文"), withdrawalKey).statusCode()).isEqualTo(409);
        assertThat(accounting(jdbc)).isEqualTo(before);
        var result = SubmissionJson.object().put("oldSubmissionStatus", 409).put("oldPlanStatus", 409)
            .put("changedWithdrawalStatus", 409).put("sameReceipt", true).put("successReplay", true);
        result.set("accounting", before); result.set("withdrawalReceipt", receipt); result.set("success", success);
        Files.writeString(evidence, result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private JsonNode accounting(JdbcTemplate jdbc) {
        var rows = SubmissionJson.object();
        var all = new E2aSnapshotRows(jdbc).capture();
        for (String table : java.util.List.of("dispatch_record", "inventory_ledger", "bale_commitment")) {
            var snapshot = all.path(table);
            if (!table.equals("bale_commitment")) assertThat(snapshot.path("rows").size()).as(table).isEqualTo(2);
            rows.set(table, snapshot);
        }
        return rows;
    }

    private String orderPath(String suffix) { return "/orders/" + http.f.source.order + suffix; }
}
