package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporaryContinuationRetainedScansTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;SubmissionFiles submissions;SourceEvidenceIntakeObjectFiles files;
    TemporaryContinuationRetainedScans reader;PhysicalDispositionVerification.Verified proof;ObjectNode body;
    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);submissions=new SubmissionFiles(directory.toString());
        files=new SourceEvidenceIntakeObjectFiles(directory.toString());reader=new TemporaryContinuationRetainedScans(jdbc,submissions,files);
        proof=new PhysicalDispositionVerification.Verified(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,
            UUID.randomUUID(),UUID.randomUUID(),"ABORTED","a".repeat(64),1,1,List.of());
        body=object().put("submissionId",proof.submission().toString()).put("workRound","2")
            .put("freezeDeviceId",UUID.randomUUID().toString()).put("authorizationId",UUID.randomUUID().toString()).put("sourceRecoveryEpoch","1");
        body.putArray("scans").addObject().put("baleId",UUID.randomUUID().toString()).put("expectedDispatchCycle","7").put("status","ACTIVE");
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
    }
    @Test void readsFullOriginalScanHashWithoutConvertingItToAnOwnerChecksum() {
        accepted(canonical(body));var scan=body.path("scans").get(0);
        assertThat(reader.read(proof)).containsEntry(uuid(scan,"baleId"),new TemporaryContinuationRetainedScans.Scan(7,sha(scan)));
    }
    @Test void readsBoundLocalFrozenPayloadWithoutAnAcceptedSubmission() throws Exception {
        var raw=canonical(body).getBytes(StandardCharsets.UTF_8);var stored=files.store(new ByteArrayInputStream(raw),raw.length,sha(body));
        local(List.of(stored));assertThat(reader.read(proof)).hasSize(1);
    }
    @Test void absentLocalObjectFailsClosed() {
        local(List.of());fails();
    }
    @Test void ambiguousLocalObjectsFailClosed() throws Exception {
        var raw=canonical(body).getBytes(StandardCharsets.UTF_8);var stored=files.store(new ByteArrayInputStream(raw),raw.length,sha(body));
        local(List.of(stored,stored));fails();
    }
    @Test void missingAcceptedFileCannotFallbackToLocal() {
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of("b".repeat(64)));fails();
        verify(jdbc,never()).query(anyString(),any(RowMapper.class),any(Object[].class));
    }
    @Test void nonCanonicalOriginalIsRejected() {
        accepted(canonical(body)+"\n");fails();
    }
    @Test void wrongOriginalSubmissionIsRejected() {
        body.put("submissionId",UUID.randomUUID().toString());accepted(canonical(body));fails();
    }
    @Test void stoppedDatabaseIdentityMustMatchTheOriginal() {
        accepted(canonical(body));when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);fails();
    }
    @Test void duplicateActiveBaleCannotBecomeAFullRetainedSet() {
        body.withArray("scans").add(body.path("scans").get(0).deepCopy());accepted(canonical(body));fails();
    }
    private void accepted(String raw) {
        String digest=submissions.store(raw.getBytes(StandardCharsets.UTF_8));
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of(digest));
    }
    @SuppressWarnings("unchecked") private void local(List<SourceEvidenceIntakeObjectFiles.Stored> stored) {
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(stored);
    }
    private void fails() {
        assertThatThrownBy(()->reader.read(proof)).isInstanceOfSatisfying(BusinessException.class,
            failure->assertThat(failure.code()).isEqualTo("CONTINUATION_RETAINED_SOURCE_INVALID"));
    }
}
