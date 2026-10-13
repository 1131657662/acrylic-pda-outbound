package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Uses actual saved Android public originals; mutations are signed synthetic counterexamples. */
@EnabledIfEnvironmentVariable(named="E06_REVIEW_ORIGINALS",matches=".+")
class RelayIndependentOriginalTest {
    private Map<String,byte[]> files;private ObjectNode order,source;
    private final Path input=Path.of(System.getenv().getOrDefault("E06_REVIEW_ORIGINALS","../.local/goal-20261013/e06-export-computer-03/verified-original"));
    @BeforeEach void original() throws Exception {
        files=new LinkedHashMap<>();
        for(String name:RelayIndependentOriginal.FILES)files.put(name,Files.readAllBytes(input.resolve(name)));
        order=(ObjectNode)SubmissionJson.parse(Files.readAllBytes(input.resolve("order.json")));
        source=(ObjectNode)SubmissionJson.parse(files.get("independent-source.json"));
    }
    @Test void actualPublicOriginalRetainsPendingIdentityAndSkuBytes() {
        var result=read();assertThat(result).isEqualTo(source);
        assertThat(result.path("task").path("id").asText()).isEqualTo(order.path("localOrderId").asText());
    }
    @ParameterizedTest @ValueSource(strings={"submission.json","submission-info.json","receipt.json","source-evidence.ndjson"})
    void independentArchiveCannotManufactureFormalEvidence(String member) {
        files.put(member,RelayBundleFixture.bytes("{}"));assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"accessToken","password","signedCredential","arbitrary"})
    void addedPublicCredentialFieldIsRejected(String field) {
        ((ObjectNode)source.path("credential")).put(field,"secret");assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void reusableMaterialInTaskIsRejected() {
        ((ObjectNode)source.path("task")).put("token","secret");assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void alteredOriginalSkuIsRejected() {
        files.put("independent-sku.bin",RelayBundleFixture.bytes("changed"));assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void independentMembersInFormalArchiveAreRejected() {
        order.put("source","FORMAL");assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void missingIndependentMemberIsRejected() {
        files.remove("independent-state.json");assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void actualSignedAndroidArchiveIsAcceptedWithoutFrozenControl() throws Exception {
        Path archive=Path.of(System.getenv("E06_REVIEW_ARCHIVE"));var result=RelayArchive.read(Files.readAllBytes(archive));
        assertThat(result.independent()).isEqualTo(source);assertThat(result.control()).isNull();
        assertThat(result.original().source()).isEqualTo("INDEPENDENT");
    }
    private JsonNode read() {
        files.put("independent-source.json",RelayBundleFixture.bytes(source.toString()));
        return RelayIndependentOriginal.read(files,order,source.path("credential").path("deviceKeySha256").asText());
    }
}
