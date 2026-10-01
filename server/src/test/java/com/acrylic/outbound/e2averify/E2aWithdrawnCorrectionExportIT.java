package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.WithdrawnCorrectionExportHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Withdrawal restores the original result and independently reverses actual accounting. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aWithdrawnCorrectionExportIT {
    @Test void withdrawnCorrectionCanExportItsRestoredSnapshot() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture=new E2aCorrectionChainIT();var order=fixture.confirmedOrder(profile,jdbc);
        String maker=fixture.mintSession(jdbc,order);fixture.grant(jdbc,order.operatorId(),"outbound:correct");
        UUID checkerId=supervisor(jdbc,order);
        // This regression enables PDA login only for its isolated supervisor.
        fixture.grant(jdbc,checkerId,"pda:login");
        String checker=fixture.mintSessionFor(jdbc,order,checkerId);
        Path output=profile.attempt.resolve("storage/withdrawn-correction");Files.createDirectories(output);
        // The isolated loopback server uses HTTP; production keeps secure cookies.
        try(var app=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
                "--outbound.corrections.commands.enabled=true")) {
            verifyWithdrawal(new Scenario(fixture,jdbc,order,maker,checker,output),(WebApplicationContext)app,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"withdrawn correction HTTP export passed\n");
    }

    private static void verifyWithdrawal(Scenario s,WebApplicationContext app,Path attempt) throws Exception {
        var document=correct(s);
        var scope=new ExportModels.Scope(s.order.operatorId(),UUID.fromString(s.order.warehouseId()),UUID.fromString(s.order.orderId()));
        var before=app.getBean(ExportSnapshotReader.class).read(scope,ExportModels.Format.XLSX,java.time.Instant.now(),2);
        Files.writeString(s.output.resolve("pre-withdraw-v2.json"),app.getBean(JsonText.class).encode(before));
        var initiated=receipt(s.fixture.post(s.checker,s.fixture.withdrawBody(s.order,document.path("documentId").asText(),
                document.path("documentSha256").asText(),"EFFECTIVE")),s.output,"withdraw");
        assertThat(initiated.path("status").asText()).isEqualTo("WITHDRAW_PENDING");
        var withdrawn=receipt(s.fixture.post(s.maker,s.fixture.withdrawConfirmBody(s.order,document.path("documentId").asText(),
                document.path("documentSha256").asText())),s.output,"withdraw-confirm");
        assertThat(withdrawn.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.path("resultVersion").asInt()).isEqualTo(3);
        assertThat(withdrawn.path("effectiveCount").asText()).isEqualTo(s.order.resultCount());
        assertThat(withdrawn.path("effectiveWeightG").asText()).isEqualTo(s.order.resultWeight());
        WithdrawnCorrectionExportHttpChecks.verify(app,scope,attempt);
    }

    private static UUID supervisor(JdbcTemplate jdbc,E2aCorrectionChainIT.Order order) {
        UUID id=Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at)"
            +" VALUES(?,?,?,'x',UTC_TIMESTAMP(3))",Ids.bytes(id),"export-checker-"+id,"隔离导出审批人");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))",Ids.bytes(id),order.warehouseId());
        assertThat(jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(id))).isEqualTo(1);
        return id;
    }

    private static JsonNode correct(Scenario s) throws Exception {
        var command=s.fixture.lineBody(s.jdbc,s.order,"WEIGHT_CORRECTION","1",List.of(s.order.dispatchIds().getFirst()),"0","0",false);
        command.put("reason","边界补测：更正第一包后撤销，还原原确认净值");
        var document=receipt(s.fixture.post(s.maker,command),s.output,"create-correction");
        var approved=receipt(s.fixture.post(s.checker,s.fixture.approveBody(s.order,document.path("documentId").asText(),
                document.path("documentSha256").asText(),"SUBMITTED","1")),s.output,"approve");
        assertThat(approved.path("resultVersion").asInt()).isEqualTo(2);
        return document;
    }

    private static JsonNode receipt(HttpResponse<byte[]> response,Path output,String step) throws Exception {
        Files.write(output.resolve(step+".json"),response.body());
        var body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("%s HTTP response: %s",step,body).isEqualTo(200);
        return body.path("businessDecision");
    }

    private record Scenario(E2aCorrectionChainIT fixture,JdbcTemplate jdbc,E2aCorrectionChainIT.Order order,
        String maker,String checker,Path output) { }
}
