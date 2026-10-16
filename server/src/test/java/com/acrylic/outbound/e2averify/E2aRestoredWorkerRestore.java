package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RestoredExportWorkerChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.Set;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Preserve a genuine pending export across full SQL/files restore before running it in another JVM. */
public final class E2aRestoredWorkerRestore {
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--verify-recorded")) {
            verifyRecorded(Path.of("D:/acrylic-pda-outbound/.local/e2a-cross-20260925-670"));return;
        }
        assertThat(args).isEmpty();
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();
        assertThat(profile.attempt).isEqualTo(Path.of("D:/acrylic-pda-outbound/.local/e2a-cross-20260925-670"));
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        E2aRestoredWorkerProcess.identity(jdbc,profile.attempt,profile.database,profile.user);
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);
        var original=copier.snapshot(jdbc,profile.attempt.resolve("storage"));
        var expected=SubmissionJson.parse(Files.readAllBytes(profile.attempt.resolve("private/original667-facts.json")));
        assertThat(original).isEqualTo(expected);
        save(profile.attempt,"original-imported.json",original);Files.createFile(profile.attempt.resolve("test-started.marker"));
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            RestoredExportWorkerChecks.prepare((WebApplicationContext)context,profile.attempt);
        }
        var target=copier.restore();var pending=copier.snapshot(target,copier.restored.resolve("storage"));
        save(profile.attempt,"pending-restored-before.json",pending);
        E2aRestoredWorkerProcess.run(profile.attempt);
        var complete=copier.snapshot(target,copier.restored.resolve("storage"));
        save(profile.attempt,"restored-worker-after.json",complete);
        conserved(pending,complete);
        assertThat(copier.snapshot(jdbc,profile.attempt.resolve("storage"))).isEqualTo(pending);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"pending HTTP task -> SQL/files restore -> separate worker JVM -> HTTP XLSX\n",
            StandardOpenOption.CREATE_NEW);
    }

    private static void conserved(ObjectNode before,ObjectNode after) {
        Set<String> mutable=Set.of("background_job","operation_event","spring_session","spring_session_attributes");
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(entry->{
            if(!mutable.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(stableSchema(after)).isEqualTo(stableSchema(before));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        before.path("files").fields().forEachRemaining(entry->assertThat(after.path("files").path(entry.getKey())).isEqualTo(entry.getValue()));
    }

    private static ObjectNode stableSchema(ObjectNode facts) {
        var schema=(ObjectNode)facts.path("schema").deepCopy();
        // Audit INSERTs advance this counter; the DDL, constraints and every other schema value stay exact.
        schema.put("operation_event",schema.path("operation_event").asText().replaceAll(" AUTO_INCREMENT=\\d+",""));
        return schema;
    }

    private static void verifyRecorded(Path attempt) throws Exception {
        assertThat(Files.readString(attempt.resolve("restored-worker-completed.marker"))).contains("separate JVM");
        var before=(ObjectNode)SubmissionJson.parse(Files.readAllBytes(attempt.resolve("pending-restored-before.json")));
        var after=(ObjectNode)SubmissionJson.parse(Files.readAllBytes(attempt.resolve("restored-worker-after.json")));
        conserved(before,after);
        assertThat(Files.readAllBytes(attempt.resolve("pending-snapshot.json")))
            .containsExactly(Files.readAllBytes(attempt.resolve("restored-snapshot.json")));
        System.out.println("RECORDED_CONSERVATION_PASS: no database connection or business rerun");
    }

    private static void save(Path attempt,String name,ObjectNode value) throws Exception {
        Files.writeString(attempt.resolve(name),value.toPrettyString(),StandardOpenOption.CREATE_NEW);
    }
}
