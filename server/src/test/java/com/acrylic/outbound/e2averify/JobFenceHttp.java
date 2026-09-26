package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class JobFenceHttp {
    private final String token = DeviceCrypto.secret();
    private final Map<String, String> seed;
    private final Path evidence;
    private final JdbcTemplate jdbc;

    JobFenceHttp(JdbcTemplate jdbc, Map<String, String> seed, Path evidence) {
        this.jdbc = jdbc; this.seed = seed; this.evidence = evidence;
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='PDA_OPERATOR'", seed.get("operatorId"));
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), seed.get("deviceId"), seed.get("operatorId"),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
    }

    void verify(JobFenceFixture f) throws Exception {
        var client = HttpClient.newHttpClient(); String epoch = f.state.current().epoch();
        var read = request("devices/me").GET().build();
        assertThat(client.send(read, HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(200);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment", Integer.class);
        f.state(epoch, "RECOVERY_PENDING");
        try {
            var write = request("orders/" + seed.get("orderId") + "/assignments")
                .header("X-Server-Recovery-Epoch", epoch).header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":\"1\",\"areaNote\":\"synthetic\"}")).build();
            var blocked = client.send(write, HttpResponse.BodyHandlers.ofByteArray());
            Files.write(evidence.resolve("ordinary-write-blocked.json"), blocked.body(), StandardOpenOption.CREATE_NEW);
            assertThat(blocked.statusCode()).isEqualTo(503);
            assertThat(SubmissionJson.parse(blocked.body()).path("code").asText()).isEqualTo("SERVER_RECOVERY_REQUIRED");
            assertThat(client.send(read, HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment", Integer.class)).isEqualTo(before);
        } finally { f.state(epoch, "NORMAL"); }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/" + path))
            .timeout(java.time.Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");
    }
}
