package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.SubmissionAccess;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.submissions.SubmissionArchiveStore;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.command.CommandReceiptIntegrity;
import com.acrylic.outbound.temporary.command.CommandStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Production assembly for TEMP archive confirmation. It is copied verbatim from the test-only assembly in
 * {@code server/src/test/java/com/acrylic/outbound/inventory/TemporaryArchiveDriver.java:23-28} — every dependency
 * there is resolved from the context, so nothing test-specific has to be moved.
 *
 * <p>The service keeps owning its outer commit boundary (it refuses to run inside an active transaction); this class
 * only makes it injectable. Registering the bean does not by itself open any production route: the TEMP branch of
 * {@code SubmissionArchiveService.confirm} stays gated by {@code outbound.temporary.archive.enabled}, which is off by
 * default (and the temporary command switch does not cover the archive route).
 */
@Configuration
public class TemporaryArchiveConfiguration {
    @Bean
    TemporaryArchiveService temporaryArchiveService(SubmissionAccess access, SubmissionStore submissions,
        SubmissionArchiveStore archives, SubmissionFiles files, JdbcTemplate jdbc, CommandStore commands,
        CommandReceiptIntegrity integrity, SubmissionArchiveProofs proofs, PlatformTransactionManager manager) {
        var evidence = new TemporaryArchiveEvidence(files, submissions, jdbc, commands, integrity);
        return new TemporaryArchiveService(new TemporaryArchiveService.Dependencies(
            access, submissions, archives, evidence, proofs), manager);
    }
}
