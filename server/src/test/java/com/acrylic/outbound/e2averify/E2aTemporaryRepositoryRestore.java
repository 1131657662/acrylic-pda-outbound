package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.TemporaryArchiveDriver;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Bounded TEMP product session recovery; existing source setup supplies actual pending Room. */
public final class E2aTemporaryRepositoryRestore {
    public static void main(String[] ignored) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc);E2aCrossSourceHttpSetup.signing(profile.attempt);
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            prepare((WebApplicationContext)context,profile.attempt);
            var before=copier.snapshot(jdbc,profile.attempt.resolve("storage"));
            Files.writeString(profile.attempt.resolve("source-before-product.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
            try(var proxy=new E2aTemporaryRepositoryProxy(profile.attempt,"source")) {
                E2aTemporaryRepositoryAndroid.run(profile.attempt,"source");
            }
            var after=copier.snapshot(jdbc,profile.attempt.resolve("storage"));assertThat(after).isEqualTo(before);
            Files.writeString(profile.attempt.resolve("source-after-product.json"),after.toPrettyString(),StandardOpenOption.CREATE_NEW);
        }
        var target=copier.restore();var before=copier.snapshot(target,copier.restored.resolve("storage"));
        Files.writeString(profile.attempt.resolve("restored-before-http.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
        restored(profile,copier);
        var after=copier.snapshot(target,copier.restored.resolve("storage"));assertThat(after).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("restored-after-http.json"),after.toPrettyString(),StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"TEMP product session/repository original success recovery\n",StandardOpenOption.CREATE_NEW);
    }

    private static void prepare(WebApplicationContext web,Path attempt) throws Exception {
        E2aAndroidPreparation.write(web,attempt.resolve("private"));
        var file=attempt.resolve("private/android-http-input.json");
        var input=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(Files.readAllBytes(file));
        input.put("baseUrl","http://127.0.0.1:"+E2aTestPorts.http()+"/api/v1/");
        Files.writeString(file,SubmissionJson.canonical(input));
        new TemporaryArchiveDriver(web).grantTestConfirmation(input.path("accessToken").asText());
        try(var adapter=new TemporaryArchiveHttpAdapter(web,attempt)) { E2aAndroidArchiveClient.run(attempt,"source"); }
        input.put("proxyUrl","http://127.0.0.1:"+(E2aTestPorts.http()+1)+"/api/v1/");
        Files.writeString(attempt.resolve("private/temp-repository-input.json"),SubmissionJson.canonical(input),StandardOpenOption.CREATE_NEW);
    }

    private static void restored(E2aCrossSourceProfile profile,E2aArchiveSnapshotRestore copier) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--spring.datasource.url="+copier.url,"--outbound.storage="+copier.restored.resolve("storage"),
            "--outbound.signing.private-key="+copier.restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+copier.restored.resolve("config/server-public.der"));
            var proxy=new E2aTemporaryRepositoryProxy(profile.attempt,"restored")) {
            E2aTemporaryRepositoryAndroid.run(profile.attempt,"restored");
        }
    }
}
