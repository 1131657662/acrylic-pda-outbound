package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Semantic counterexamples consume original Android rows without inventing registration or signatures. */
@EnabledIfEnvironmentVariable(named="E06_CLOSURE_ARCHIVE",matches=".+")
class RelayIndependentCollectionTest {
    private Map<String,byte[]> files;
    private RelayIndependentClosure.Rows rows;
    private JsonNode source;
    @BeforeEach void original() throws Exception {
        files=new HashMap<>();
        try(var zip=new ZipInputStream(Files.newInputStream(Path.of(System.getenv("E06_CLOSURE_ARCHIVE"))))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readAllBytes());
        }
        source=parse(files.get("independent-source.json"));
        rows=new RelayIndependentClosure.Rows(records("captures"),records("scans"),records("events"),records("lines"),records("independent-voids"));
    }
    @Test void originalRowsMatchSkuAndAuthorization() { verify(); }
    @Test void originalWeightCannotBeReplacedByAnotherClaim() {
        ((ObjectNode)rows.scans().getFirst()).put("weightGrams","1");
        assertThatThrownBy(this::verify).hasMessageContaining("SKU字段不符");
    }
    @Test void duplicatePlanCannotInflateACompleteBoundary() {
        rows.lines().add(rows.lines().getFirst().deepCopy());
        assertThatThrownBy(this::verify).hasMessageContaining("ID重复");
    }
    @Test void AuditOutsideOriginalBootCannotCertifyClosure() {
        ((ObjectNode)rows.events().getFirst()).put("boot","999");
        assertThatThrownBy(this::verify).hasMessageContaining("启动代次不可信");
    }
    @Test void savedFactCannotPrecedeOriginalConfirmation() {
        ((ObjectNode)rows.scans().getFirst()).put("savedAt","2000-01-01T00:00:00Z");
        assertThatThrownBy(this::verify).hasMessageContaining("超授权期限");
    }
    @Test void changedRawInputCannotInheritOriginalScan() {
        ((ObjectNode)rows.captures().getFirst()).put("rawInput","ANOTHER-BARCODE");
        assertThatThrownBy(this::verify).hasMessageContaining("原输入不符");
    }
    private void verify() { RelayIndependentCollection.verify(rows,source,files.get("independent-sku.bin")); }
    private List<JsonNode> records(String name) {
        var result=new ArrayList<JsonNode>();
        for(String line:new String(files.get(name+".ndjson"),StandardCharsets.UTF_8).split("\n"))if(!line.isBlank())result.add(parse(line));
        return result;
    }
}
