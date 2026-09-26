package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeDeviceKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftEvidencePreparationTest {
    private final TemporaryStore store=mock(TemporaryStore.class);
    private final SourceEvidenceIntakeDeviceKeys keys=mock(SourceEvidenceIntakeDeviceKeys.class);
    private final OfflineSigner signer=mock(OfflineSigner.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private DraftEvidencePreparation preparation;
    private DraftEvidenceFixture f;
    private ObjectNode metadata;
    private TemporaryStore.Stored stored;
    private OfflineSigner.PublicKeyView trusted;
    @BeforeEach void setup() throws Exception {
        f=new DraftEvidenceFixture();metadata=(ObjectNode)f.vector.path("metadata").deepCopy();
        var receipt=mapper.readValue(metadata.path("preparationReceipt").asText(),TemporaryCommands.Receipt.class);
        var manifest=SubmissionJson.parse(receipt.manifest());var identity=manifest.path("identity");
        trusted=mapper.readValue(metadata.path("preparationSigningKey").asText(),OfflineSigner.PublicKeyView.class);
        stored=new TemporaryStore.Stored(f.expected.authorizationId(),f.actor.warehouseId(),f.actor.deviceId(),f.actor.userId(),
            identity.path("authorizationEpoch").asText(),identity.path("userAuthVersion").asText(),identity.path("deviceVersion").asText(),
            identity.path("serverRecoveryEpoch").asText(),Instant.parse(manifest.path("issuedAt").asText()),Instant.parse(manifest.path("expiresAt").asText()),receipt);
        when(store.get(stored.id())).thenReturn(stored);when(signer.publicKeyFor(trusted.keyId())).thenReturn(trusted);
        when(jdbc.queryForObject(anyString(),eq(String.class),any(byte[].class))).thenReturn(trusted.keyId());
        when(jdbc.queryForList(anyString(),any(byte[].class))).thenReturn(List.of(Map.of("snapshot_id",manifest.path("inventorySnapshotId").asText(),
            "mapping_revision",manifest.path("mappingRevision").asText(),"inventory_version",manifest.path("inventoryVersion").asText(),"policy_version",manifest.path("policyVersion").asText())));
        when(keys.trusted(f.actor.warehouseId(),f.actor.deviceId())).thenReturn(f.expected.registeredDeviceKey());
        preparation=new DraftEvidencePreparation(store,keys,signer,new JsonText(mapper),jdbc);
    }
    @Test void originalStoredPreparationAndHistoricalSignatureBindDeviceContext() {
        assertThat(preparation.require(metadata,f.actor)).isEqualTo(f.expected);
    }
    @Test void expiredOriginalAuthorizationStillAllowsPreservation() {
        // Historical issued/expires values remain signature-bound; wall-clock expiry never grants new business use.
        assertThat(stored.expiresAt()).isBefore(Instant.now());
        assertThat(preparation.require(metadata,f.actor).authorizationId()).isEqualTo(stored.id());
    }
    @Test void unknownHistoryCannotTrustUploadedKey() {
        when(signer.publicKeyFor(trusted.keyId())).thenReturn(null);fails();
    }
    @Test void uploadedReplacementKeyCannotOverrideTrustedHistory() {
        var key=SubmissionJson.parse(metadata.path("preparationSigningKey").asText());((ObjectNode)key).put("keyId","forged");
        metadata.put("preparationSigningKey",SubmissionJson.canonical(key));fails();
    }
    @Test void crossWarehouseActorCannotUseValidOriginal() {
        var other=new PdaSessions.Principal(f.actor.sessionId(),f.actor.userId(),"synthetic",f.actor.deviceId(),UUID.randomUUID(),"1","1");
        assertThatThrownBy(()->preparation.require(metadata,other)).isInstanceOf(BusinessException.class);
    }
    @Test void alteredOriginalReceiptCannotReplaceServerRecord() {
        var receipt=(ObjectNode)SubmissionJson.parse(metadata.path("preparationReceipt").asText());receipt.put("bundleId",UUID.randomUUID().toString());
        metadata.put("preparationReceipt",SubmissionJson.canonical(receipt));fails();
    }
    @Test void baselineMissingOrDifferentCannotPass() {
        when(jdbc.queryForList(anyString(),any(byte[].class))).thenReturn(List.of());fails();
    }
    @Test void originalRequestCannotSwitchSnapshot() {
        var request=(ObjectNode)SubmissionJson.parse(metadata.path("preparationRequest").asText());request.put("inventorySnapshotId",UUID.randomUUID().toString());
        metadata.put("preparationRequest",SubmissionJson.canonical(request));fails();
    }
    @Test void databaseAuthorizationEpochMustMatchSignedIdentity() {
        when(store.get(stored.id())).thenReturn(new TemporaryStore.Stored(stored.id(),stored.warehouseId(),stored.deviceId(),stored.userId(),
            "999",stored.userVersion(),stored.deviceVersion(),stored.recoveryEpoch(),stored.issuedAt(),stored.expiresAt(),stored.receipt()));fails();
    }
    private void fails() { assertThatThrownBy(()->preparation.require(metadata,f.actor)).isInstanceOf(BusinessException.class); }
}
