package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.ContinuationDatabaseCommand;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic storage proof for independent TEMP authorization and ticket activation. */
@EnabledIfEnvironmentVariable(named = "B4_RETENTION_VERIFY", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContinuationActivationStorageIT {
    private RetentionDatabaseFixture fixture;

    @BeforeAll void provision() throws Exception { fixture = new RetentionDatabaseFixture(); }

    @Test void activationRollsBackAtomicallyThenCommitsWithImmutablePredecessor() throws Exception {
        var before = fixture.stock();
        var authorizationCount = fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization", Integer.class);
        var originCount = fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_work_origin", Integer.class);
        fixture.tx.executeWithoutResult(status -> {
            fixture.apply(fixture.verified(), Collections.nCopies(3, "RETAIN"));
            try {
                var command = new ContinuationDatabaseCommand(fixture.jdbc, fixture.seed,
                    fixture.profile.attempt.resolve("private/activation-rollback"));
                var ticket = ticket(command.execute().unsigned());
                command.activate(ticket, fixture.profile.attempt.resolve("private/activation-rollback"));
                assertActivated(ticket, before);
                status.setRollbackOnly();
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation", Integer.class)).isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization", Integer.class)).isEqualTo(authorizationCount);
        assertThat(fixture.stock()).isEqualTo(before);

        fixture.tx.executeWithoutResult(status -> {
            fixture.apply(fixture.verified(), Collections.nCopies(3, "RETAIN"));
            try {
                var command = new ContinuationDatabaseCommand(fixture.jdbc, fixture.seed,
                    fixture.profile.attempt.resolve("private/activation-commit"));
                var ticket = ticket(command.execute().unsigned());
                command.activate(ticket, fixture.profile.attempt.resolve("private/activation-commit"));
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        var ticket = fixture.jdbc.queryForObject("SELECT id FROM temporary_source_continuation", (rs, n) -> uuid(rs.getBytes(1)));
        assertActivated(ticket, before);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation WHERE ticket_id=?",
            Integer.class, com.acrylic.outbound.common.Ids.bytes(ticket))).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_work_origin", Integer.class)).isEqualTo(originCount + 1);
    }

    private void assertActivated(UUID ticket, java.util.List<java.util.Map<String, Object>> before) {
        assertThat(fixture.jdbc.queryForObject("SELECT status FROM temporary_source_continuation WHERE id=?", String.class,
            com.acrylic.outbound.common.Ids.bytes(ticket))).isEqualTo("ACTIVATED");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation WHERE ticket_id=?",
            Integer.class, com.acrylic.outbound.common.Ids.bytes(ticket))).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE temporary_continuation_id=? AND state='TEMP_CONTINUATION_RESERVED'",
            Integer.class, com.acrylic.outbound.common.Ids.bytes(ticket))).isEqualTo(3);
        assertThat(fixture.stock()).allMatch(row -> "TEMP_CONTINUATION_RESERVED".equals(row.get("state")));
        assertThat(fixture.stock()).isNotEqualTo(before);
    }

    private static UUID ticket(byte[] receipt) {
        return UUID.fromString(SubmissionJson.parse(receipt).path("businessDecision").path("ticketId").asText());
    }

    private static UUID uuid(byte[] bytes) {
        var buffer = java.nio.ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong());
    }
}
