package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic installation and real CLI process boundary; retained under the test's D-drive temp. */
record RecoveryRekeyCliFixture(FenceTestSupply supply, byte[] next, Path request) {
    record Output(int status, String output, String error) { }

    static RecoveryRekeyCliFixture create(Path parent) throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority(); authority.validateStartup(); authority.retire();
        byte[] next = new byte[32]; new java.security.SecureRandom().nextBytes(next);
        Path retained = supply.base().resolve("retained-anchor.bin"); Files.write(retained, supply.roots().anchor());
        var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("operation", "REKEY_P5")
            .put("installationId", supply.roots().installation()).put("authorityRoot", supply.roots().authorityRoot().toString())
            .put("leaseRoot", supply.roots().root().toString()).put("closedAuthority", supply.base().resolve("closed").toString())
            .put("identitySha256", supply.roots().identityHash()).put("markerSha256", hash(RecoveryControlHistory.read(supply.roots().root().resolve("lease-root.json"))))
            .put("oldAnchorId", sha256(supply.roots().anchor())).put("newAnchorId", sha256(next))
            .put("retainedAnchorFile", retained.toString());
        value.putArray("restoreRoots").add(supply.base().resolve("business").toRealPath().toString());
        Path request = supply.base().resolve("rekey-request.json"); Files.writeString(request, canonical(value));
        System.out.println("P07_TEST_ROOT=" + supply.base());
        return new RecoveryRekeyCliFixture(supply, next, request);
    }

    byte[] pipe() {
        byte[] pipe = new byte[64]; System.arraycopy(supply.roots().anchor(), 0, pipe, 0, 32);
        System.arraycopy(next, 0, pipe, 32, 32); return pipe;
    }

    Output run(byte[] bytes) {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int status = RecoveryRekeyCli.run(new String[]{"--request", request.toString()},
            new RecoveryRekeyCli.Streams(new ByteArrayInputStream(bytes), new PrintStream(output), new PrintStream(error)));
        return new Output(status, output.toString(java.nio.charset.StandardCharsets.UTF_8),
            error.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    Output child() throws Exception {
        Path logs = Files.createTempDirectory(supply.base(), "cli-");
        var paths = new ObjectMapper().readTree(Files.readString(Path.of("build/recovery-rekey-classpath.json")));
        var classpath = new ArrayList<String>(); paths.forEach(p -> classpath.add(p.asText()));
        String binary = RecoveryControlPaths.WINDOWS ? "java.exe" : "java";
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", binary).toString(),
            "-Dfile.encoding=UTF-8", "-cp", String.join(File.pathSeparator, classpath), RecoveryRekeyCli.class.getName(),
            "--request", request.toString());
        Files.writeString(logs.resolve("command.txt"), String.join(" ", command));
        var process = new ProcessBuilder(command).redirectOutput(logs.resolve("stdout.txt").toFile())
            .redirectError(logs.resolve("stderr.txt").toFile()).start();
        try {
            try (var input = process.getOutputStream()) { input.write(pipe()); }
            assertThat(process.waitFor(25, TimeUnit.SECONDS)).as(logs.toString()).isTrue();
            return new Output(process.exitValue(), Files.readString(logs.resolve("stdout.txt")), Files.readString(logs.resolve("stderr.txt")));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            Files.writeString(logs.resolve("process.txt"), "pid=" + process.pid() + "; alive=" + process.isAlive());
        }
    }

    void change(String field, String value) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) RecoveryControlHistory.read(request);
        body.put(field, value); Files.writeString(request, canonical(body));
    }

    Map<Path, String> facts() throws Exception {
        var facts = new TreeMap<Path, String>();
        for (Path root : List.of(supply.roots().authorityRoot(), supply.roots().root(), supply.base().resolve("business"))) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) facts.put(path, sha256(Files.readAllBytes(path)));
            }
        }
        return facts;
    }

    JsonNode onlyProcess() throws Exception {
        try (var paths = Files.list(supply.roots().root().resolve("processes"))) {
            return RecoveryControlHistory.read(paths.findFirst().orElseThrow());
        }
    }
}
