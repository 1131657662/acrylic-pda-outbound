package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit offline command. No Spring context, HTTP route, bootstrap, service control or auto-retry. */
public final class RecoveryRekeyCli {
    record Streams(InputStream input, PrintStream output, PrintStream error) { }
    private RecoveryRekeyCli() { }

    public static void main(String[] args) {
        int status = run(args, new Streams(System.in, System.out, System.err));
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, Streams streams) {
        try {
            require(args.length == 2 && args[0].equals("--request"), "RECOVERY_REKEY_USAGE");
            var request = RecoveryRekeyRequest.read(Path.of(args[1]));
            try (var secrets = RecoveryRekeySecrets.read(streams.input(), request)) {
                var roots = new RecoveryLeaseRoots(request.binding(), request.pins().markerSha256(), secrets.oldAnchor);
                var result = RecoveryLeaseRekey.rekey(roots, secrets.newAnchor);
                streams.output().writeBytes(receipt(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (streams.output().checkError()) {
                    streams.error().println("RECOVERY_REKEY_RECEIPT_UNCERTAIN"); return 3;
                }
                return 0;
            }
        } catch (RecoveryControlException refused) {
            streams.error().println(refused.code()); return 2;
        } catch (IOException failure) {
            streams.error().println("RECOVERY_REKEY_IO_UNCERTAIN"); return 3;
        } catch (IllegalArgumentException failure) {
            streams.error().println("RECOVERY_REKEY_INPUT"); return 2;
        }
    }

    private static String receipt(RecoveryLeaseRekey.Result result) {
        var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("status", "REKEYED")
            .put("oldAnchorId", result.oldAnchorId()).put("newAnchorId", result.newAnchorId())
            .put("markerSha256", result.markerSha256()).put("processRecords", result.processRecords())
            .put("leaseRecords", result.leaseRecords()).put("evidenceDirectory", result.evidenceDirectory())
            .put("inventorySha256", result.inventorySha256())
            .put("runtimeUpdated", false);
        return canonical(value);
    }
}
