package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;

/** Real process harness; test source only, no application endpoint or fault flag. */
public final class RecoveryControlProcess {
    public static void main(String[] args) throws Exception {
        try { execute(args); }
        catch (RecoveryControlException error) { System.out.println(error.code()); System.exit(2); }
    }

    private static void execute(String[] args) throws Exception {
        if (args[0].equals("read-config")) { readConfigured(Path.of(args[1])); return; }
        String mode = args[0]; Path root = Path.of(args[1]);
        if (mode.equals("hold") || mode.equals("probe")) {
            RecoveryControlLock.locked(root, () -> {
                System.out.println("LOCKED"); System.out.flush();
                if (mode.equals("hold")) System.in.read();
                return null;
            });
            return;
        }
        if (mode.equals("nested") || mode.equals("threaded")) {
            RecoveryControlLock.locked(root, () -> {
                if (mode.equals("threaded")) threaded(root); else nested(root);
                System.out.println("LOCKED"); System.out.flush(); System.in.read(); return null;
            });
            return;
        }
        UUID installation = UUID.fromString(args[2]); Path checkpoint = Path.of(args[3]);
        var restoreRoots = Arrays.stream(args).skip(4).map(Path::of).toList();
        var snapshot = new RecoveryControlReader(new RecoveryControlLocation(root, installation, restoreRoots)).read(checkpoint);
        if (snapshot.ordinaryWritesAllowed() || snapshot.localDeletionAllowed()) throw new AssertionError("Unexpected authority");
        describe(snapshot);
    }

    private static void nested(Path root) throws java.io.IOException {
        try { RecoveryControlLock.locked(root, () -> null); throw new AssertionError("Nested lock admitted"); }
        catch (RecoveryControlException busy) {
            if (!busy.code().equals("CONTROL_COORDINATOR_BUSY")) throw busy;
        }
    }

    private static void threaded(Path root) throws java.io.IOException {
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            try { pool.submit(() -> { nested(root); return null; }).get(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception error) { throw new java.io.IOException("Threaded lock failed", error); }
        }
    }

    private static void readConfigured(Path path) throws Exception {
        var config = RecoveryControlFormat.readCanonical(Files.readAllBytes(path));
        var roots = new ArrayList<Path>();
        config.path("restoreRoots").forEach(value -> roots.add(Path.of(value.textValue())));
        var location = new RecoveryControlLocation(Path.of(config.path("root").textValue()),
            UUID.fromString(config.path("installationId").textValue()), roots);
        describe(new RecoveryControlReader(location).read(Path.of(config.path("checkpoint").textValue())));
    }

    private static void describe(RecoveryControlSnapshot value) {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        json.put("installationId", value.installationId().toString());
        json.put("revision", value.revision()); json.put("phase", value.phase());
        json.put("recoverySessionId", value.recoverySessionId().toString());
        json.put("epochId", value.epochId().toString());
        json.put("snapshotId", value.snapshotId()); json.put("manifestSha256", value.manifestSha256());
        json.put("recordSha256", value.recordSha256()); json.put("evidenceSha256", value.evidenceSha256());
        var warehouses = json.putArray("affectedWarehouseIds");
        value.affectedWarehouseIds().forEach(warehouses::add);
        json.put("possibleLossFrom", value.possibleLossFrom().toString());
        json.put("possibleLossThrough", value.possibleLossThrough().toString());
        json.put("ordinaryWritesAllowed", value.ordinaryWritesAllowed());
        json.put("localDeletionAllowed", value.localDeletionAllowed());
        System.out.print(RecoveryControlFormat.canonical(json));
    }
}
