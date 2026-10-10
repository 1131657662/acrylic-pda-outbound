package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Corrupt only fresh evidence copies; every refusal must precede any target SQL mutation. */
public final class RecoveryReceivedCheckpointRefusalChecks {
    private RecoveryReceivedCheckpointRefusalChecks() { }

    public static void verify(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        empty(jdbc,attempt);
        Path dump=copy(attempt,original,"dump");
        Files.writeString(dump.resolve("checkpoint.sql"),"untrusted checkpoint\n");
        assertThatThrownBy(() -> RecoveryReceivedCheckpointTargetChecks.verify(jdbc,attempt,dump)).isInstanceOf(AssertionError.class);
        empty(jdbc,attempt);
        Path log=copy(attempt,original,"checksum"); corruptLog(log);
        assertThatThrownBy(() -> RecoveryReceivedCheckpointTargetChecks.verify(jdbc,attempt,log)).hasMessage("RECOVERY_BINLOG_CHECKSUM");
        empty(jdbc,attempt);
        Path permissions=copy(attempt,original,"permissions"); changedPermission(permissions);
        assertThatThrownBy(() -> RecoveryReceivedCheckpointTargetChecks.verify(jdbc,attempt,permissions)).hasMessage("RECOVERY_PERMISSION_BOUNDARY_CHANGED");
        empty(jdbc,attempt);
        Files.writeString(attempt.resolve("checkpoint-refusals.json"),"{\"cases\":[\"dumpPin\",\"repinnedBadCrc\",\"changedPermission\"],\"targetUnchanged\":true}\n");
    }

    private static void empty(JdbcTemplate jdbc,Path attempt) {
        assertThat(jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME")).isEmpty();
        assertThat(Files.exists(attempt.resolve("checkpoint-import.out"))).isFalse();
    }

    private static Path copy(Path attempt,Path original,String kind) throws Exception {
        Path copy=Files.createDirectory(attempt.getParent().resolve(attempt.getFileName()+"-rejected-"+kind));
        for(String name:List.of("test-completed.marker","checkpoint-transfer.json","checkpoint.sql","checkpoint.sha256",
                "evidence-permission-begin.json","evidence-permission-end.json","evidence-permission-binlog.bin"))
            Files.copy(original.resolve(name),copy.resolve(name));
        return copy;
    }

    private static void corruptLog(Path copy) throws Exception {
        Path file=copy.resolve("evidence-permission-binlog.bin"); byte[] log=Files.readAllBytes(file); log[log.length-1]^=1; Files.write(file,log);
        var transfer=(ObjectNode)readCanonical(Files.readAllBytes(copy.resolve("checkpoint-transfer.json")));
        var end=(ObjectNode)readCanonical(Files.readAllBytes(copy.resolve("evidence-permission-end.json")));
        transfer.put("binlogPin",sha256(log)); end.put("binlogPrefixSha256",sha256(log)); repinEnd(copy,transfer,end);
    }

    private static void changedPermission(Path copy) throws Exception {
        var transfer=(ObjectNode)readCanonical(Files.readAllBytes(copy.resolve("checkpoint-transfer.json")));
        var end=(ObjectNode)readCanonical(Files.readAllBytes(copy.resolve("evidence-permission-end.json")));
        ((ObjectNode)end.path("effective")).put("deviceStatus","DISABLED"); repinEnd(copy,transfer,end);
    }

    private static void repinEnd(Path copy,ObjectNode transfer,ObjectNode end) throws Exception {
        byte[] bytes=RecoveryControlJson.encode(end); transfer.put("endPin",sha256(bytes));
        Files.write(copy.resolve("evidence-permission-end.json"),bytes);
        Files.write(copy.resolve("checkpoint-transfer.json"),RecoveryControlJson.encode(transfer));
    }
}
