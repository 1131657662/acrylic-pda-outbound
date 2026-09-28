package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.inventory.PartialCorrectionExportHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Reuses the verified two-package fixture; only the first package is corrected to zero. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aPartialCorrectionExportIT {
    @Test void partialCorrectionPreservesTheOtherPackageInExportedContent() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"48");E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture=new E2aCorrectionChainIT();var order=fixture.confirmedOrder(profile,jdbc);
        assertThat(order.dispatchIds()).hasSize(2);
        String token=fixture.mintSession(jdbc,order);fixture.grant(jdbc,order.operatorId(),"outbound:correct");
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.corrections.commands.enabled=true")) {
            var create=fixture.lineBody(jdbc,order,"WEIGHT_CORRECTION","1",
                    List.of(order.dispatchIds().getFirst()),"0","0",false);
            create.put("reason","边界补测：仅更正第一包，保留第二包");
            var created=decision(fixture.post(token,create));
            assertThat(created.path("lineCount").asInt()).isEqualTo(1);
            var approve=fixture.approveBody(order,created.path("documentId").asText(),
                    created.path("documentSha256").asText(),"SUBMITTED","1");
            var approved=decision(fixture.post(token,approve));
            assertThat(approved.path("resultVersion").asText()).isEqualTo("2");
            var scope=new ExportModels.Scope(order.operatorId(),UUID.fromString(order.warehouseId()),UUID.fromString(order.orderId()));
            PartialCorrectionExportHttpChecks.verify((WebApplicationContext)app,scope,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"partial correction HTTP export passed\n");
    }

    private static JsonNode decision(HttpResponse<byte[]> response) {
        var body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("correction HTTP response: %s",body).isEqualTo(200);
        return body.path("businessDecision");
    }
}
