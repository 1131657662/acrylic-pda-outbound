package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Non-secret, explicitly pinned offline rotation inputs; never discovered from a writable root. */
record RecoveryRekeyRequest(RecoveryLeaseRoots.Binding binding, Pins pins, Path retainedAnchorFile) {
    record Pins(String markerSha256, String oldAnchorId, String newAnchorId) { }

    static RecoveryRekeyRequest read(Path request) throws IOException {
        Path file = RecoveryControlPaths.file(request);
        JsonNode value = RecoveryControlHistory.read(file);
        fields(value, "formatVersion operation installationId authorityRoot restoreRoots leaseRoot closedAuthority identitySha256 markerSha256 oldAnchorId newAnchorId retainedAnchorFile");
        version(value);
        require(text(value, "operation").equals("REKEY_P5"), "RECOVERY_REKEY_OPERATION");
        for (String name : new String[]{"identitySha256", "markerSha256", "oldAnchorId", "newAnchorId"}) hashField(value, name);
        require(!text(value, "oldAnchorId").equals(text(value, "newAnchorId")), "RECOVERY_REKEY_SAME_ANCHOR");
        var location = location(value);
        var binding = new RecoveryLeaseRoots.Binding(location,
            RecoveryControlPaths.checked(Path.of(text(value, "leaseRoot"))),
            RecoveryControlPaths.checked(Path.of(text(value, "closedAuthority"))), text(value, "identitySha256"));
        Path retained = RecoveryControlPaths.file(Path.of(text(value, "retainedAnchorFile")));
        external(file, binding); external(retained, binding);
        return new RecoveryRekeyRequest(binding, new Pins(text(value, "markerSha256"),
            text(value, "oldAnchorId"), text(value, "newAnchorId")), retained);
    }

    private static RecoveryControlLocation location(JsonNode value) throws IOException {
        require(value.path("restoreRoots").isArray(), "CONTROL_RESTORE_SET_REQUIRED");
        var restored = new ArrayList<Path>();
        for (var item : value.path("restoreRoots")) {
            require(item.isTextual(), "CONTROL_RESTORE_SET_REQUIRED");
            restored.add(Path.of(item.textValue()));
        }
        var location = new RecoveryControlLocation(Path.of(text(value, "authorityRoot")),
            uuid(value, "installationId"), restored).checked();
        require(location.restoreRoots().size() == restored.size(), "CONTROL_RESTORE_SET_MISMATCH");
        return location;
    }

    private static void external(Path file, RecoveryLeaseRoots.Binding binding) {
        var roots = new ArrayList<>(binding.location().restoreRoots());
        roots.add(binding.location().root()); roots.add(binding.leaseRoot()); roots.add(binding.closedAuthority());
        for (Path root : roots) require(!RecoveryControlPaths.overlap(file, root), "RECOVERY_REKEY_EXTERNAL_INPUT_REQUIRED");
    }
}
