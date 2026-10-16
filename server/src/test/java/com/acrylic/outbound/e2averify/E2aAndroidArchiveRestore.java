package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.TemporaryArchiveDriver;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Direct isolated driver: no enclosing Gradle while the Android Gradle consumer runs. */
public final class E2aAndroidArchiveRestore {
    public static void main(String[] ignored) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc);E2aCrossSourceHttpSetup.signing(profile.attempt);
        source(profile);
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);var target=copier.restore();
        var before=copier.snapshot(target,copier.restored.resolve("storage"));
        Files.writeString(profile.attempt.resolve("restored-before-http.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
        restored(profile,copier);
        Files.writeString(profile.attempt.resolve("restored-after-http.json"),
            copier.snapshot(target,copier.restored.resolve("storage")).toPrettyString(),StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android native Room freeze -> real HTTP -> full SQL/files restore -> original Room HTTP/signature consumer\n",
            StandardOpenOption.CREATE_NEW);
    }

    private static void source(E2aCrossSourceProfile profile) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            var web=(WebApplicationContext)context;
            E2aAndroidPreparation.write(web,profile.attempt.resolve("private"));
            var input=SubmissionJson.parse(Files.readAllBytes(profile.attempt.resolve("private/android-http-input.json")));
            new TemporaryArchiveDriver(web).grantTestConfirmation(input.path("accessToken").asText());
            try(var adapter=new TemporaryArchiveHttpAdapter(web,profile.attempt)) {
                E2aAndroidArchiveClient.run(profile.attempt,"source");
            }
        }
    }

    private static void restored(E2aCrossSourceProfile profile,E2aArchiveSnapshotRestore copier) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--spring.datasource.url="+copier.url,"--outbound.storage="+copier.restored.resolve("storage"),
            "--outbound.signing.private-key="+copier.restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+copier.restored.resolve("config/server-public.der"))) {
            try(var adapter=new TemporaryArchiveHttpAdapter((WebApplicationContext)context,profile.attempt)) {
                E2aAndroidArchiveClient.run(profile.attempt,"restored");
                E2aAndroidArchiveClient.run(profile.attempt,"lost-success");
            }
        }
    }
}
