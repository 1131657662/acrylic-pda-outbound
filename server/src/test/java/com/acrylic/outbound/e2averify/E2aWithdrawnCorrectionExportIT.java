package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.inventory.WithdrawnCorrectionExportHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Withdrawal cancels the correction's net delta and restores the original confirmed package. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aWithdrawnCorrectionExportIT {
    @Test void withdrawnCorrectionCanExportItsRestoredSnapshot() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"48");E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture=new E2aCorrectionChainIT();var order=fixture.confirmedOrder(profile,jdbc);
        String token=fixture.mintSession(jdbc,order);fixture.grant(jdbc,order.operatorId(),"outbound:correct");
        Path output=profile.attempt.resolve("storage/withdrawn-correction");Files.createDirectories(output);
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.corrections.commands.enabled=true")) {
            var document=correct(fixture,jdbc,order,token,output);
            var initiated=receipt(fixture.post(token,fixture.withdrawBody(order,document.path("documentId").asText(),
                    document.path("documentSha256").asText(),"EFFECTIVE")),output,"withdraw");
            assertThat(initiated.path("status").asText()).isEqualTo("WITHDRAW_PENDING");
            var withdrawn=receipt(fixture.post(token,fixture.withdrawConfirmBody(order,document.path("documentId").asText(),
                    document.path("documentSha256").asText())),output,"withdraw-confirm");
            assertThat(withdrawn.path("status").asText()).isEqualTo("WITHDRAWN");
            assertThat(withdrawn.path("resultVersion").asInt()).isEqualTo(3);
            assertThat(withdrawn.path("effectiveCount").asText()).isEqualTo(order.resultCount());
            assertThat(withdrawn.path("effectiveWeightG").asText()).isEqualTo(order.resultWeight());
            var scope=new ExportModels.Scope(order.operatorId(),UUID.fromString(order.warehouseId()),UUID.fromString(order.orderId()));
            WithdrawnCorrectionExportHttpChecks.verify((WebApplicationContext)app,scope,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"withdrawn correction HTTP export passed\n");
    }

    private static JsonNode correct(E2aCorrectionChainIT fixture,JdbcTemplate jdbc,E2aCorrectionChainIT.Order order,
            String token,Path output) throws Exception {
        var command=fixture.lineBody(jdbc,order,"WEIGHT_CORRECTION","1",List.of(order.dispatchIds().getFirst()),"0","0",false);
        command.put("reason","边界补测：更正第一包后撤销，还原原确认净值");
        var document=receipt(fixture.post(token,command),output,"create-correction");
        var approved=receipt(fixture.post(token,fixture.approveBody(order,document.path("documentId").asText(),
                document.path("documentSha256").asText(),"SUBMITTED","1")),output,"approve");
        assertThat(approved.path("resultVersion").asInt()).isEqualTo(2);
        return document;
    }

    private static JsonNode receipt(HttpResponse<byte[]> response,Path output,String step) throws Exception {
        Files.write(output.resolve(step+".json"),response.body());
        var body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("%s HTTP response: %s",step,body).isEqualTo(200);
        return body.path("businessDecision");
    }
}
