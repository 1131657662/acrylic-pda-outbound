package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {"server.address=127.0.0.1", "server.port=19434"})
class TemporaryRejectionIT {
    @Autowired WebApplicationContext context;

    @Test
    void rejectionIsRecordedWithoutReleasingReservationsAndReplaysOriginalReceipt() throws Exception {
        var fixture = new TemporaryConfirmationFixture(context, false);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='55' AND success=1",
            Integer.class)).isZero();
        SessionUser supervisor = fixture.supervisor();
        var command = fixture.command("REJECT", supervisor);
        byte[] original = fixture.execute(command, supervisor);
        var receipt = SubmissionJson.parse(original);

        assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(receipt.at("/businessDecision/kind").asText()).isEqualTo("REJECT");
        assertThat(fixture.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class,
            fixture.source.id())).isEqualTo("REJECTED");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='REVIEW_RESERVED'",
            Integer.class, fixture.source.id())).isGreaterThan(0);
        assertThat(fixture.execute(command, supervisor)).containsExactly(original);
        assertThat(fixture.read(command, supervisor).currentEffect().path("evaluation").asText()).isEqualTo("REJECTED");
        assertThat(UUID.fromString(receipt.path("receiptId").asText())).isNotNull();
    }

    @Test
    void intakeWithOnlyStockConflictsCannotBeRejectedAndKeepsOriginalIssues() throws Exception {
        var fixture = new TemporaryConfirmationFixture(context, false, source -> source.f.body.path("scans").forEach(scan ->
            source.f.jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)",
                scan.path("baleId").asText())));
        var storedBefore = fixture.jdbc.queryForObject("SELECT issues_json FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class, fixture.source.id());
        var issues = SubmissionJson.parse(storedBefore);
        assertThat(issues.findValuesAsText("code")).contains("DISPATCH_CYCLE_CONFLICT");
        assertThat(issues.findValuesAsText("code").stream().filter("DISPATCH_CYCLE_CONFLICT"::equals)).hasSize(2);
        SessionUser supervisor = fixture.supervisor();
        var command = fixture.command("REJECT", supervisor);

        assertThatThrownBy(() -> fixture.execute(command, supervisor))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("SUBMISSION_STILL_CONFLICTED"));

        assertThat(fixture.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class,
            fixture.source.id())).isEqualTo("REVIEW_REQUIRED");
        assertThat(fixture.jdbc.queryForObject("SELECT issues_json FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class, fixture.source.id())).isEqualTo(storedBefore);
    }
}
