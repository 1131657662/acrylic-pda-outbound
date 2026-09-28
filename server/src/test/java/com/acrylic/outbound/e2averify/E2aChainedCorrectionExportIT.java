package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.inventory.ChainedCorrectionExportHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Same package corrected twice: the second adjustment must start at the first one's target. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aChainedCorrectionExportIT {
    @Test void continuousCorrectionsKeepTheCompleteExportChain() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"48");E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture=new E2aCorrectionChainIT();var order=fixture.confirmedOrder(profile,jdbc);
        String token=fixture.mintSession(jdbc,order);fixture.grant(jdbc,order.operatorId(),"outbound:correct");
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.corrections.commands.enabled=true")) {
            correct(fixture,jdbc,order,token,1,"300000");
            correct(fixture,jdbc,order,token,2,"299500");
            var scope=new ExportModels.Scope(order.operatorId(),UUID.fromString(order.warehouseId()),UUID.fromString(order.orderId()));
            ChainedCorrectionExportHttpChecks.verify((WebApplicationContext)app,scope,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"continuous v3 correction HTTP export passed\n");
    }

    private static void correct(E2aCorrectionChainIT fixture,JdbcTemplate jdbc,E2aCorrectionChainIT.Order order,
            String token,int version,String weight) throws Exception {
        var command=fixture.lineBody(jdbc,order,"WEIGHT_CORRECTION",Integer.toString(version),
                List.of(order.dispatchIds().getFirst()),"1",weight,false);
        command.put("reason","边界补测：同包连续更正至版本"+(version+1));
        var response=fixture.post(token,command);JsonNode body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("CREATE: %s",body).isEqualTo(200);
        var document=body.path("businessDecision");
        var approved=fixture.post(token,fixture.approveBody(order,document.path("documentId").asText(),
                document.path("documentSha256").asText(),"SUBMITTED",Integer.toString(version)));
        var result=SubmissionJson.parse(approved.body());
        assertThat(approved.statusCode()).as("APPROVE: %s",result).isEqualTo(200);
        assertThat(result.at("/businessDecision/resultVersion").asInt()).isEqualTo(version+1);
    }
}
