package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.FormalAndroidPreparation;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Native FORMAL scan/freeze, real public HTTP, full SQL/files restore and original Room replay. */
public final class E2aFormalArchiveRestore {
    public static void main(String[] ignored) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc);E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            FormalAndroidPreparation.write((WebApplicationContext)context,profile.attempt.resolve("private"));
            E2aFormalAndroidClient.run(profile.attempt,"source");
        }
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);var target=copier.restore();
        var before=copier.snapshot(target,copier.restored.resolve("storage"));
        Files.writeString(profile.attempt.resolve("restored-before-http.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
        restored(profile,copier);
        var after=copier.snapshot(target,copier.restored.resolve("storage"));assertThat(after).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("restored-after-http.json"),after.toPrettyString(),StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"FORMAL original Room/guards and SQL/files preserved\n",StandardOpenOption.CREATE_NEW);
    }

    private static void restored(E2aCrossSourceProfile profile,E2aArchiveSnapshotRestore copier) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--spring.datasource.url="+copier.url,"--outbound.storage="+copier.restored.resolve("storage"),
            "--outbound.signing.private-key="+copier.restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+copier.restored.resolve("config/server-public.der"))) {
            E2aFormalAndroidClient.run(profile.attempt,"restored");
        }
    }
}
