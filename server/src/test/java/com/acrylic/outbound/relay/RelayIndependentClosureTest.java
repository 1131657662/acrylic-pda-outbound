package com.acrylic.outbound.relay;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Cross-runtime consumption of a signed Android closure; altered facts are only semantic counterexamples. */
@EnabledIfEnvironmentVariable(named="E06_CLOSURE_ARCHIVE",matches=".+")
class RelayIndependentClosureTest {
    private Map<String,byte[]> files;
    @BeforeEach void original() throws Exception {
        files=new LinkedHashMap<>();
        try(var zip=new ZipInputStream(Files.newInputStream(Path.of(System.getenv("E06_CLOSURE_ARCHIVE"))))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readAllBytes());
        }
    }
    @Test void actualAndroidClosureBindsItsFullCollectionPrefix() throws Exception {
        var result=RelayArchive.read(Files.readAllBytes(Path.of(System.getenv("E06_CLOSURE_ARCHIVE"))));
        assertThat(result.closure()).isNotNull();assertThat(result.closure().path("inputHighWater").asText()).isEqualTo("2");
        assertThat(result.closure().path("void").asText()).isEqualTo("1");assertThat(result.body()).isNull();
    }
    @Test void changedPlanCannotInheritCompleteBoundary() {
        String lines=new String(files.get("lines.ndjson"),java.nio.charset.StandardCharsets.UTF_8);
        var line=(ObjectNode)SubmissionJson.parse(lines.trim());line.put("plannedCount","999");
        files.put("lines.ndjson",(line+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(this::read).hasMessageContaining("完整结束原事实不符");
    }
    @Test void missingAuditPrefixCannotInheritSignedClosure() {
        String events=new String(files.get("events.ndjson"),java.nio.charset.StandardCharsets.UTF_8);
        files.put("events.ndjson",events.substring(events.indexOf('\n')+1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(this::read).hasMessageContaining("前缀缺失");
    }
    private void read() {
        RelayIndependentClosure.read(files,SubmissionJson.parse(files.get("independent-source.json")),SubmissionJson.parse(files.get("order.json")),
            Base64.getEncoder().encodeToString(files.get("device-public-key.der")));
    }
}
