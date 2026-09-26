package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryArchiveRecoveryFixture;
import com.acrylic.outbound.inventory.FormalArchiveRecoveryFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryArchiveRecoveryIT {
    @Test void fullSnapshotRestoresTemporaryArchiveFilesAndOriginalApprovalClosure() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        List<TemporaryArchiveRecoveryFixture.Original> originals;
        FormalArchiveRecoveryFixture.Original formal;
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            originals=new TemporaryArchiveRecoveryFixture((WebApplicationContext)context).create(profile.attempt);
            formal=new FormalArchiveRecoveryFixture().create((WebApplicationContext)context);
            var proofs=(com.fasterxml.jackson.databind.node.ArrayNode)SubmissionJson.parse(Files.readAllBytes(profile.attempt.resolve("archive-proofs.json")));
            proofs.add(formal.proof());Files.writeString(profile.attempt.resolve("archive-proofs.json"),SubmissionJson.canonical(proofs));
        }
        E2aArchiveRecoveryPython.inspect(profile.attempt,"source");
        var copier=new E2aArchiveSnapshotRestore(profile,jdbc);var restored=copier.restore();
        E2aArchiveRecoveryPython.inspect(profile.attempt,"restore");
        var before=copier.snapshot(restored,copier.restored.resolve("storage"));
        E2aArchiveRecoveryPython.faults(profile.attempt);
        assertThat(copier.snapshot(restored,copier.restored.resolve("storage"))).isEqualTo(before);
        restoredHttp(profile,copier,originals,formal);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Full SQL/files snapshot restored; TEMP V2 and FORMAL V1 archives, original approval closure, faults and real HTTP; no disaster reconstruction\n");
    }

    private void restoredHttp(E2aCrossSourceProfile profile,E2aArchiveSnapshotRestore copier,List<TemporaryArchiveRecoveryFixture.Original> originals,
            FormalArchiveRecoveryFixture.Original formal) throws Exception {
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--spring.datasource.url="+copier.url,"--outbound.storage="+copier.restored.resolve("storage"),
            "--outbound.signing.private-key="+copier.restored.resolve("config/server-private.der"),
            "--outbound.signing.public-key="+copier.restored.resolve("config/server-public.der"))) {
            var fixture=new TemporaryArchiveRecoveryFixture((WebApplicationContext)context);
            for(var original:originals)fixture.check(original);
            new FormalArchiveRecoveryFixture().check(formal);
        }
    }
}
