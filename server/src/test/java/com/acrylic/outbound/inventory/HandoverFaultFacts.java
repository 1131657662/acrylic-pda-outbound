package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Every durable table, including device/session, is compared; only Web session expiry is excluded. */
final class HandoverFaultFacts {
    private final JdbcTemplate jdbc;
    private final Path directory;
    HandoverFaultFacts(JdbcTemplate jdbc, Path directory) throws Exception {
        this.jdbc = jdbc; this.directory = Files.createDirectory(directory);
    }
    ObjectNode snapshot(String label) throws Exception {
        var result = new E2aSnapshotRows(jdbc).capture();
        result.remove(java.util.List.of("spring_session", "spring_session_attributes"));
        Files.writeString(directory.resolve(label + ".json"), result.toString(), StandardOpenOption.CREATE_NEW);
        return result;
    }
}
