package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/** Only the owned worker pool uses the loopback transport; direct target authentication stays intact. */
final class E2aWireCommitOptions {
    static void configure(ArrayList<String> options, Path home, E2aArchiveRestoreTarget target) throws Exception {
        assertThat(target).isNotNull();
        var proxy = SubmissionJson.parse(Files.readString(Path.of(System.getenv("E2A_WIRE_PROXY_CONFIG"))));
        assertThat(proxy.path("host").asText()).isEqualTo("127.0.0.1");
        assertThat(proxy.path("targetPort").asInt()).isEqualTo(Integer.parseInt(System.getenv("E2A_COPY_PORT")));
        assertThat(Path.of(proxy.path("home").asText())).isEqualTo(home);
        int port = proxy.path("port").asInt();
        assertThat(port).isBetween(1024, 65535).isNotEqualTo(proxy.path("targetPort").asInt());
        String jdbc = target.url.replace(":" + proxy.path("targetPort").asInt() + "/", ":" + port + "/");
        assertThat(jdbc).isNotEqualTo(target.url).startsWith("jdbc:mysql://127.0.0.1:" + port + "/");
        options.add("--spring.datasource.hikari.jdbc-url=" + jdbc + "&socketTimeout=180000&autoReconnect=false");
        options.add("--outbound.test.wire-commit-home=" + home);
    }
}
