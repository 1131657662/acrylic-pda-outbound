package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.Path;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Separate explicit offline entry; no runtime configuration update or automatic service control. */
public final class RecoveryRekeyRestoreCli {
    private RecoveryRekeyRestoreCli() { }

    public static void main(String[] args) {
        int status = run(args, new RecoveryRekeyCli.Streams(System.in, System.out, System.err));
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, RecoveryRekeyCli.Streams streams) {
        try {
            require(args.length == 6 && args[0].equals("--request") && args[2].equals("--backup")
                && args[4].equals("--inventory-sha256") && args[5].matches("[a-f0-9]{64}"), "RECOVERY_RESTORE_USAGE");
            var request = RecoveryRekeyRequest.read(Path.of(args[1]));
            var input = new RecoveryRekeyRestorePlan.Input(request, Path.of(args[3]), args[5]);
            try (var secrets = RecoveryRekeySecrets.read(streams.input(), request)) {
                var result = RecoveryRekeyRestore.restore(input, secrets);
                var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("status", "ORIGINALS_RESTORED")
                    .put("alreadyOriginal", result.alreadyOriginal()).put("markerSha256", result.markerSha256())
                    .put("evidenceDirectory", result.evidenceDirectory()).put("recordCount", result.recordCount())
                    .put("inventorySha256", input.inventoryHash()).put("runtimeUpdated", false)
                    .put("keepWritersStopped", true).put("recoveryAllowed", false).put("productionApproved", false);
                streams.output().writeBytes(canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (streams.output().checkError()) { streams.error().println("RECOVERY_RESTORE_RECEIPT_UNCERTAIN"); return 3; }
                return 0;
            }
        } catch (RecoveryRekeyRestoreFailure uncertain) {
            streams.error().println("RECOVERY_RESTORE_EVIDENCE_RETAINED: " + uncertain.evidence); return 3;
        } catch (RecoveryControlException refused) { streams.error().println(refused.code()); return 2; }
        catch (IOException failure) { streams.error().println("RECOVERY_RESTORE_IO_UNCERTAIN"); return 3; }
        catch (IllegalArgumentException invalid) { streams.error().println("RECOVERY_RESTORE_INPUT"); return 2; }
    }
}
