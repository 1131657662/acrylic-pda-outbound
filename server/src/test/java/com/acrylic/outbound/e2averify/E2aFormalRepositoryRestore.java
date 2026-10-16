package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.FormalAndroidPreparation;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual product repository observes a lost committed success before full SQL/files/Room recovery. */
public final class E2aFormalRepositoryRestore {
    public static void main(String[] ignored) throws Exception {
        assertThat(System.getenv("E2A_CROSS_VERIFY")).isEqualTo("true");
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc);E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            FormalAndroidPreparation.write((WebApplicationContext)context,profile.attempt.resolve("private"));
            input(profile.attempt);
            try(var proxy=new E2aFormalRepositoryProxy(profile.attempt,"source",true)) {
                E2aFormalRepositoryAndroid.run(profile.attempt,"source");
            }
        }
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);var target=copier.restore();
        var before=copier.snapshot(target,copier.restored.resolve("storage"));
        Files.writeString(profile.attempt.resolve("restored-before-http.json"),before.toPrettyString(),StandardOpenOption.CREATE_NEW);
        restored(profile,copier);
        var after=copier.snapshot(target,copier.restored.resolve("storage"));assertThat(after).isEqualTo(before);
        Files.writeString(profile.attempt.resolve("restored-after-http.json"),after.toPrettyString(),StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"FORMAL product session/repository, original unknown Room, full SQL/files recovery\n",StandardOpenOption.CREATE_NEW);
    }

    private static void input(Path attempt) throws Exception {
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(Files.readString(attempt.resolve("private/formal-http-input.json")));
        value.put("baseUrl","http://127.0.0.1:"+E2aTestPorts.http()+"/api/v1/");
        value.put("proxyUrl","http://127.0.0.1:"+(E2aTestPorts.http()+1)+"/api/v1/");
        Files.writeString(attempt.resolve("private/formal-repository-input.json"),SubmissionJson.canonical(value),StandardOpenOption.CREATE_NEW);
    }

    private static void restored(E2aCrossSourceProfile profile,E2aArchiveSnapshotRestore copier) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--spring.datasource.url="+copier.url,"--outbound.storage="+copier.restored.resolve("storage"),
            "--outbound.signing.private-key="+copier.restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+copier.restored.resolve("config/server-public.der"));
            var proxy=new E2aFormalRepositoryProxy(profile.attempt,"restored",false)) {
            E2aFormalRepositoryAndroid.run(profile.attempt,"restored");
        }
    }
}
