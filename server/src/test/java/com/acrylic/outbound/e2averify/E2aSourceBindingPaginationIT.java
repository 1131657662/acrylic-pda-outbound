package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic identities exercise real HTTP paging; no pairing or field identity claim. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aSourceBindingPaginationIT {
    private static final String BASE="http://127.0.0.1:19434/api/v1/pda/temporary-source-bindings?";
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Path evidence;
    private String token;
    private int requestNo;

    @Test void allDeviceSourcesAreReachableAcrossBindingPages() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); evidence=profile.attempt;
        Files.createFile(evidence.resolve("test-started.marker"));
        profile.migrate(jdbc,"34"); var seed=E2aV34Seed.seed(jdbc,evidence.resolve("storage"));
        for(int i=0;i<51;i++) insertSource(jdbc,seed);
        var expected=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM submission_scope WHERE creator_device_id=? ORDER BY id DESC",
            String.class,Ids.bytes(UUID.fromString(seed.get("deviceId"))));
        assertThat(expected).hasSize(55); Files.write(evidence.resolve("expected-source-ids.txt"),expected);
        profile.migrate(jdbc,"53"); E2aCrossSourceHttpSetup.signing(evidence);
        token=installSession(jdbc,seed); String other=otherDeviceSession(jdbc,seed);
        var snapshots=new CorrectionAttachmentSqlChecks(jdbc,evidence); var tables=snapshots.tables();
        var before=snapshots.snapshot(tables,"bindings-before");
        try(var ignored=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true")) {
            assertThat(readPages(expected)).containsExactlyElementsOf(expected);
            boundaryPages(expected); identityAndInputDenials(other);
        }
        assertThat(snapshots.snapshot(tables,"bindings-after")).isEqualTo(before);
        Files.writeString(evidence.resolve("test-completed.marker"),
            "55 sources across cursors; empty/exact/multi pages; device/input boundaries; typed tables unchanged");
    }

    private List<String> readPages(List<String> expected) throws Exception {
        List<String> seen=new ArrayList<>(); String cursor="";
        for(int index=0;index<5;index++) {
            var data=read(token,"limit=50&cursor="+cursor,200);
            for(JsonNode row:data.path("items")) {
                String scope=row.path("scopeId").asText();
                assertThat(seen).doesNotContain(scope); seen.add(scope);
            }
            boolean remaining=seen.size()<expected.size();
            assertThat(data.path("hasMore").asBoolean()).isEqualTo(remaining);
            assertThat(data.path("nextCursor").isNull()).isEqualTo(!remaining);
            if(!remaining) return seen;
            cursor=data.path("nextCursor").asText();
        }
        throw new AssertionError("Pagination did not terminate");
    }

    private void boundaryPages(List<String> expected) throws Exception {
        var full=read(token,"limit=55",200);
        assertThat(full.path("items").findValuesAsText("scopeId")).containsExactlyElementsOf(expected);
        assertThat(full.path("hasMore").asBoolean()).isFalse();
        assertThat(full.path("nextCursor").isNull()).isTrue();
        var empty=read(token,"limit=50&cursor="+expected.getLast(),200);
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("hasMore").asBoolean()).isFalse();
        assertThat(empty.path("nextCursor").isNull()).isTrue();
        var one=read(token,"limit=1",200);
        assertThat(one.path("items").findValuesAsText("scopeId")).containsExactly(expected.getFirst());
        assertThat(one.path("hasMore").asBoolean()).isTrue();
        assertThat(one.path("nextCursor").asText()).isEqualTo(expected.getFirst());
    }

    private void identityAndInputDenials(String other) throws Exception {
        var empty=read(other,"limit=50",200);
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("hasMore").asBoolean()).isFalse();
        read("","limit=50",401);
        read(token,"limit=0",400); read(token,"limit=201",400);
        read(token,"limit=50&cursor=not-a-uuid",400);
    }

    private JsonNode read(String bearer,String query,int expectedStatus) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(BASE+query)).timeout(Duration.ofSeconds(20))
            .header("X-Server-Recovery-Epoch","1");
        if(!bearer.isEmpty()) request.header("Authorization","Bearer "+bearer);
        var response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve("http-"+(++requestNo)+"-response.json"),response.body());
        Files.writeString(evidence.resolve("http-"+requestNo+"-status.txt"),Integer.toString(response.statusCode()));
        assertThat(response.statusCode()).as(new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(expectedStatus);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private void insertSource(JdbcTemplate jdbc,Map<String,String> seed) {
        String scope=UUID.randomUUID().toString(),client=UUID.randomUUID().toString();
        assertThat(jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) "
            +"VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",seed.get("deviceId"),client,seed.get("authorizationId"))).isEqualTo(1);
        assertThat(jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,creator_device_id,"
            +"client_order_id,canonical_order_id,mapping_version,current_submission_id) VALUES(UUID_TO_BIN(?),"
            +"UUID_TO_BIN(?),NULL,1,UUID_TO_BIN(?),UUID_TO_BIN(?),NULL,0,NULL)",scope,seed.get("warehouseId"),seed.get("deviceId"),client)).isEqualTo(1);
        assertThat(jdbc.update("INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,"
            +"canonical_order_id,origin,observed_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),0,NULL,"
            +"'SOURCE_REGISTERED',UTC_TIMESTAMP(3))",UUID.randomUUID().toString(),seed.get("warehouseId"),scope)).isEqualTo(1);
    }

    private String installSession(JdbcTemplate jdbc,Map<String,String> seed) {
        assertThat(jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='PDA_OPERATOR'",
            seed.get("operatorId"))).isEqualTo(1);
        return session(jdbc,seed.get("deviceId"),seed.get("operatorId"));
    }

    private String otherDeviceSession(JdbcTemplate jdbc,Map<String,String> seed) throws Exception {
        String other=UUID.randomUUID().toString();
        var generator=java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        String publicKey=Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        DeviceCrypto.publicKey(publicKey);
        assertThat(jdbc.update("INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at) "
            +"SELECT UUID_TO_BIN(?),warehouse_id,?,'Other synthetic device',?,?,'ACTIVE',1,created_by,UTC_TIMESTAMP(3) "
            +"FROM device WHERE id=UUID_TO_BIN(?)",other,"PAGE-"+other.substring(0,8),publicKey,DeviceCrypto.sha(publicKey),seed.get("deviceId"))).isEqualTo(1);
        return session(jdbc,other,seed.get("operatorId"));
    }

    private String session(JdbcTemplate jdbc,String device,String operator) {
        String secret=DeviceCrypto.secret();
        assertThat(jdbc.update("INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,"
            +"access_expires_at,expires_at,created_at) VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),"
            +"DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),
            Ids.bytes(UUID.fromString(device)),Ids.bytes(UUID.fromString(operator)),
            DeviceCrypto.sha(secret),DeviceCrypto.sha(DeviceCrypto.secret()))).isEqualTo(1);
        return secret;
    }
}
