package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.inventory.HeaderCorrectionExportHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Reuses the existing scalar orderNo header patch with zero dispatch lines. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHeaderCorrectionExportIT {
    @Test void headerOnlyCorrectionExportsTheApprovedVersion() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"48");E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture=new E2aCorrectionChainIT();var order=fixture.confirmedOrder(profile,jdbc);
        String token=fixture.mintSession(jdbc,order);fixture.grant(jdbc,order.operatorId(),"outbound:correct");
        Path output=profile.attempt.resolve("storage/header-correction");Files.createDirectories(output);
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.corrections.commands.enabled=true")) {
            correctHeader(fixture,jdbc,order,token,output);
            var scope=new ExportModels.Scope(order.operatorId(),UUID.fromString(order.warehouseId()),UUID.fromString(order.orderId()));
            HeaderCorrectionExportHttpChecks.verify((WebApplicationContext)app,scope,profile.attempt,"CORR-HEADER-EXPORT-1");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"header correction HTTP export passed\n");
    }

    private static void correctHeader(E2aCorrectionChainIT fixture,JdbcTemplate jdbc,E2aCorrectionChainIT.Order order,
            String token,Path output) throws Exception {
        var command=fixture.headerBody(jdbc,order,"1","orderNo","CORR-HEADER-EXPORT-1");
        Files.writeString(output.resolve("command-create.json"),command.toPrettyString());
        var created=receipt(fixture.post(token,command),output,"create-correction");
        assertThat(created.path("lineCount").asInt()).isZero();
        var approved=receipt(fixture.post(token,fixture.approveBody(order,created.path("documentId").asText(),
                created.path("documentSha256").asText(),"SUBMITTED","1")),output,"approve");
        assertThat(approved.path("resultVersion").asInt()).isEqualTo(2);
    }

    private static JsonNode receipt(HttpResponse<byte[]> response,Path output,String step) throws Exception {
        Files.write(output.resolve(step+".json"),response.body());
        var body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("%s HTTP response: %s",step,body).isEqualTo(200);
        return body.path("businessDecision");
    }
}
