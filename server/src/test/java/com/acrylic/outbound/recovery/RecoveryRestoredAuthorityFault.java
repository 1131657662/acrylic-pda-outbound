package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Run-owned synthetic corruption, after preserving exact authority input bytes. */
public final class RecoveryRestoredAuthorityFault {
    public static String expected() {
        return switch (kind()) {
            case "restore-roots" -> "CONTROL_RESTORE_SET_MISMATCH";
            case "checkpoint" -> "CONTROL_CHECKPOINT_ROLLBACK";
            default -> throw new AssertionError("Unknown closed fault kind");
        };
    }

    private static String kind() {
        String kind = System.getenv("E2A_AUTHORITY_FAULT");
        assertThat(kind).isIn("restore-roots", "checkpoint");
        return kind;
    }

    public static void apply(Path attempt) throws Exception {
        Path home = attempt.resolve("private/p5-stale-authority");
        var config = RecoveryControlHistory.read(home.resolve("config.json"));
        Path target = kind().equals("restore-roots") ? Path.of(config.path("root").asText()).resolve("identity.json")
            : Path.of(config.path("checkpoint").asText());
        assertThat(target.toRealPath().startsWith(home.toRealPath())).isTrue();
        byte[] original = Files.readAllBytes(target);
        Files.write(home.resolve("fault-original.bin"), original, StandardOpenOption.CREATE_NEW);
        ObjectNode changed = (ObjectNode) RecoveryControlFormat.readCanonical(original);
        if (kind().equals("restore-roots")) {
            Path wrong = Files.createDirectory(home.resolve("wrong-restore-root")).toRealPath();
            changed.putArray("restoreRoots").add(wrong.toString());
        } else changed.put("revision", "2");
        RecoveryControlFixture.write(target, changed);
        var receipt = RecoveryControlFixture.JSON.createObjectNode().put("kind", kind()).put("code", expected())
            .put("target", target.toString()).put("producerPid", ProcessHandle.current().pid())
            .put("originalSha256", RecoveryControlFormat.sha256(original))
            .put("changedSha256", RecoveryControlFormat.sha256(Files.readAllBytes(target)))
            .put("originalPreserved", true).put("controlAdvanced", false);
        Files.writeString(home.resolve("fault.json"), receipt.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
