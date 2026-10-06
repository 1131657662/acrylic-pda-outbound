package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class TemporaryRoundPredecessorTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;SubmissionFiles submissions;SourceEvidenceIntakeObjectFiles files;
    TemporaryRoundPredecessor reader;ObjectNode original;TemporaryRoundFacts.Source source;ObjectNode request;

    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);submissions=new SubmissionFiles(directory.toString());
        files=new SourceEvidenceIntakeObjectFiles(directory.toString());reader=new TemporaryRoundPredecessor(jdbc,submissions,files);
        request=TemporaryRoundControlTest.body();original=request.deepCopy().put("workRound","1");
        original.remove("temporaryContinuation");
        original.put("submissionId",uuid(request.path("temporaryContinuation"),"predecessorSubmissionId").toString());
        original.put("authorizationId",UUID.randomUUID().toString());
        source=new TemporaryRoundFacts.Source(UUID.randomUUID(),UUID.randomUUID(),uuid(original,"submissionId"),
            uuid(original,"authorizationId"),1,2,"1",UUID.randomUUID(),1,"b".repeat(64),"ACTIVATED",
            uuid(request,"authorizationId"),uuid(request.path("source"),"clientOrderId"),uuid(request,"freezeDeviceId"),UUID.randomUUID());
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),any(),any())).thenReturn(1);
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of());
    }
    @Test void readsOriginalAcceptedBytesWithoutRewritingIdentity() {
        accepted(canonical(original));
        assertThat(reader.read(control(),source)).isEqualTo(original);
        assertThat(reader.read(control(),source).path("authorizationId")).isNotEqualTo(request.path("authorizationId"));
    }
    @Test void doesNotFallbackToLocalWhenAcceptedDigestDiffers() {
        accepted(canonical(original));reference().put("predecessorPayloadSha256","f".repeat(64));
        fails("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
        verify(jdbc,never()).query(anyString(),any(RowMapper.class),any(Object[].class));
    }
    @Test void acceptedArchiveMissingFailsClosed() {
        reference().put("predecessorPayloadSha256","f".repeat(64));
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of("f".repeat(64)));
        fails("TEMP_CONTINUATION_PREDECESSOR_UNAVAILABLE");
    }
    @Test void rejectsNonCanonicalBytesEvenWhenTheirStoredDigestMatches() {
        accepted(" "+canonical(original));
        fails("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
    }
    @Test void rejectsAlteredSourceIdentityUnderAValidArchiveHash() {
        original.put("authorizationId",UUID.randomUUID().toString());accepted(canonical(original));
        fails("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
    }
    @Test void rejectsOriginalClientFromAnotherScope() {
        accepted(canonical(original));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(),any(),any())).thenReturn(0);
        fails("TEMP_CONTINUATION_PREDECESSOR_MISMATCH");
    }
    @Test void readsCanonicalLocalFrozenObjectWhenNoBusinessPayloadWasAccepted() throws Exception {
        byte[] bytes=canonical(original).getBytes(StandardCharsets.UTF_8);
        var stored=files.store(new java.io.ByteArrayInputStream(bytes),bytes.length,sha(original));
        reference().put("predecessorPayloadSha256",sha(original));
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(stored));
        assertThat(reader.read(control(),source)).isEqualTo(original);
    }
    @Test void refusesMissingLocalFrozenObject() {
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
        fails("TEMP_CONTINUATION_PREDECESSOR_UNAVAILABLE");
    }
    private void accepted(String text) {
        String hash=submissions.store(text.getBytes(StandardCharsets.UTF_8));
        reference().put("predecessorPayloadSha256",hash);
        when(jdbc.queryForList(anyString(),eq(String.class),any())).thenReturn(List.of(hash));
    }
    private void fails(String code) {
        assertThatThrownBy(()->reader.read(control(),source)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo(code));
    }
    private ObjectNode reference() { return (ObjectNode)request.get("temporaryContinuation"); }
    private TemporaryRoundControl control() { return TemporaryRoundControl.read(request); }
}
