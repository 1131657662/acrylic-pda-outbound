package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * The initial-authority identity shape (design row 6), version aware:
 *
 * <ul>
 *   <li><b>version 1</b> — the legacy control-history shape: {@code formatVersion, installationId,
 *       restoreRoots, initialCommandSha256}. No kind is carried, so the loading purpose decides
 *       whether that is acceptable.</li>
 *   <li><b>version 2</b> — the initial normal installation: the same fields plus an explicit
 *       {@code authorityKind}, the {@code leaseRoot} the P5 layer is bound to and the
 *       {@code closedAuthority} path. This is the shape the P5 provisioning and verification
 *       demand.</li>
 * </ul>
 *
 * <p>The field set stays exact in both versions: an unknown or missing field is refused rather than
 * ignored, and a version nobody implements is refused instead of being read as v1. Which kind may be
 * loaded is decided by {@link RecoveryControlPurpose}, never here.
 */
final class RecoveryInitialAuthority {
    static final String INITIAL_NORMAL_INSTALL = "INITIAL_NORMAL_INSTALL";
    private static final String VERSION_1 = "formatVersion installationId restoreRoots initialCommandSha256";
    private static final String VERSION_2 =
        "formatVersion authorityKind installationId restoreRoots closedAuthority leaseRoot initialCommandSha256";

    private RecoveryInitialAuthority() { }

    /** Shape, installation, bootstrap hash and restore set; the caller's purpose checked the kind. */
    static void validate(JsonNode value, RecoveryControlLocation location) {
        Objects.requireNonNull(location, "location");
        var version = value == null ? null : value.path("formatVersion");
        require(value != null && value.isObject() && version.isIntegralNumber() && version.canConvertToInt(),
            "CONTROL_FIELDS");
        switch (version.intValue()) {
            case 1 -> {
                fields(value, VERSION_1);
                version(value);
            }
            case 2 -> {
                fields(value, VERSION_2);
                text(value, "authorityKind");
                var lease = requiredPath(value, "leaseRoot");
                var closed = requiredPath(value, "closedAuthority");
                require(!lease.equals(closed), "CONTROL_FIELDS");
            }
            default -> throw new RecoveryControlException("CONTROL_FORMAT_UNSUPPORTED", null);
        }
        require(location.installation().equals(uuid(value, "installationId")), "CONTROL_INSTALLATION_MISMATCH");
        hashField(value, "initialCommandSha256");
        var roots = value.path("restoreRoots");
        require(roots.isArray() && roots.size() == location.restoreRoots().size(), "CONTROL_RESTORE_SET_MISMATCH");
        for (int i = 0; i < roots.size(); i++) require(roots.get(i).isTextual()
            && location.restoreRoots().get(i).toString().equals(roots.get(i).textValue()), "CONTROL_RESTORE_SET_MISMATCH");
    }

    /** True when the identity carries the v2 shape the P5 binding needs. */
    static boolean initialNormalShape(JsonNode value) {
        return value != null && value.path("formatVersion").asInt() == 2
            && INITIAL_NORMAL_INSTALL.equals(value.path("authorityKind").asText(""));
    }

    private static String requiredPath(JsonNode value, String key) {
        var text = text(value, key);
        require(!text.isBlank() && java.nio.file.Path.of(text).isAbsolute(), "CONTROL_FIELDS");
        return text;
    }
}
