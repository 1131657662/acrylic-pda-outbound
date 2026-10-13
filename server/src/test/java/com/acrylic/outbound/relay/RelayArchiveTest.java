package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayArchiveTest {
    private KeyPair key;private ObjectNode order,body;
    @BeforeEach void input() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));key=generator.generateKeyPair();
        order=SubmissionJson.object().put("source","FORMAL").put("orderId",UUID.randomUUID().toString())
            .put("localOrderId",UUID.randomUUID().toString()).put("deviceId",UUID.randomUUID().toString())
            .put("operatorId",UUID.randomUUID().toString()).put("warehouseId",UUID.randomUUID().toString()).put("timeTrust","UNCONFIRMED_EXPORT_TIME");
        body=SubmissionJson.object().put("submissionId",UUID.randomUUID().toString()).put("assignmentId",order.path("localOrderId").asText())
            .put("freezeDeviceId",order.path("deviceId").asText()).put("workRound","1").put("sourceRecoveryEpoch","1");
        body.putNull("continuationId");body.putObject("source").put("kind","FORMAL").put("sourceOrderId",order.path("orderId").asText());
    }
    @Test void frozenOriginalRetainsExactIdentityAndArchiveDigest() throws Exception {
        byte[] bytes=RelayBundleFixture.build(RelayBundleFixture.files(order,body),key);var result=RelayArchive.read(bytes);
        assertThat(result.body()).isEqualTo(body);assertThat(result.control().id().toString()).isEqualTo(body.path("submissionId").asText());
        assertThat(result.original().user().toString()).isEqualTo(order.path("operatorId").asText());
        assertThat(result.archiveSha256()).isEqualTo(com.acrylic.outbound.inventory.ImportFiles.sha256(bytes));
    }
    @Test void unfrozenPackageHasNoBusinessControl() throws Exception {
        assertThat(RelayArchive.read(RelayBundleFixture.build(RelayBundleFixture.files(order,null),key)).control()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"../submission.json","unknown.txt","dir/manifest.json"})
    void signedUnknownPathsAreRejected(String name) throws Exception {
        var files=RelayBundleFixture.files(order,body);files.put(name,new byte[0]);byte[] bytes=RelayBundleFixture.build(files,key);
        assertThatThrownBy(()->RelayArchive.read(bytes)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"source","orderId","localOrderId","deviceId"})
    void signedFrozenIdentityMismatchIsRejected(String field) throws Exception {
        order.put(field,UUID.randomUUID().toString());byte[] bytes=RelayBundleFixture.build(RelayBundleFixture.files(order,body),key);
        assertThatThrownBy(()->RelayArchive.read(bytes)).isInstanceOf(BusinessException.class);
    }
    @Test void signedMissingOriginalHashIsRejected() throws Exception {
        var files=RelayBundleFixture.files(order,body);files.remove("submission-info.json");byte[] bytes=RelayBundleFixture.build(files,key);
        assertThatThrownBy(()->RelayArchive.read(bytes)).isInstanceOf(BusinessException.class);
    }
    @Test void correctlySignedInvalidUtf8IsRejected() throws Exception {
        var files=RelayBundleFixture.files(order,body);files.put("order.json",new byte[]{(byte)0xc3,0x28});byte[] bytes=RelayBundleFixture.build(files,key);
        assertThatThrownBy(()->RelayArchive.read(bytes)).isInstanceOf(BusinessException.class);
    }
    @Test void correctlySignedDuplicateJsonPropertyIsRejected() throws Exception {
        var files=RelayBundleFixture.files(order,body);files.put("order.json",RelayBundleFixture.bytes(order.toString().replaceFirst("\\{","{\"source\":\"TEMP\",")));
        byte[] bytes=RelayBundleFixture.build(files,key);assertThatThrownBy(()->RelayArchive.read(bytes)).isInstanceOf(BusinessException.class);
    }
    @Test void truncatedArchiveIsRejected() throws Exception {
        byte[] bytes=RelayBundleFixture.build(RelayBundleFixture.files(order,body),key);byte[] truncated=Arrays.copyOf(bytes,bytes.length-22);
        assertThatThrownBy(()->RelayArchive.read(truncated)).isInstanceOf(BusinessException.class);
    }
    @Test void oversizedArchiveIsRejectedWithoutBusinessParsing() {
        assertThatThrownBy(()->RelayArchive.read(new byte[RelayArchive.MAX_BYTES+1])).isInstanceOf(BusinessException.class);
    }
}
