package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Verifies the local-object SQL/file branch with synthetic source references, not original STOP intake. */
@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundLocalRegistrationIT {
    @Test void registersFromLocalFrozenBytesWithoutRestoringTheOldBusinessPayload() throws Exception {
        try(var f=new TemporaryRoundRegistrationFixture(true)) {
            var stock=f.http.database.stock();
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Integer.class,
                Ids.bytes(f.http.database.id("mappedSubmissionId")))).isZero();
            var registered=f.register(f.request);
            assertThat(f.register(f.request)).isEqualTo(registered);
            assertThat(f.count("temporary_source_continuation_submission")).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Integer.class,
                Ids.bytes(f.http.database.id("mappedSubmissionId")))).isZero();
            assertThat(f.http.database.stock()).isEqualTo(stock);
            assertThat(f.jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,
                Ids.bytes(f.http.database.id("mappedSubmissionId")))).isEqualTo("ABORTED");
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),
                "Synthetic local frozen object; actual HTTP ticket and activation replay across nonempty upgrade; "
                    +"real file/SQL registration; predecessor payload remains absent. Not STOP or round intake/accounting proof.\n");
        }
    }
}
