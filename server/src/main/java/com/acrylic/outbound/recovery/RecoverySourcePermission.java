package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Historical permission windows never renew an expired or revoked identity. */
public final class RecoverySourcePermission {
    private static final String WINDOW_FIELDS = "from through userId deviceId warehouseId userAuthVersion deviceVersion "
        + "permissions warehouseIds userActive deviceStatus deviceTemporaryOrderEnabled";
    private final RecoverySourceAuthority credentials;

    public RecoverySourcePermission(RecoveryReceiptKeys keys) { credentials = new RecoverySourceAuthority(keys); }

    public Proof verify(RecoveryPinnedSource source) {
        require(source != null, "RECOVERY_SOURCE_IDENTITY");
        try {
            JsonNode claims = credentials.verify(source);
            JsonNode value = SubmissionJson.parse(source.original("permission"));
            fields(value, "formatVersion source payloadSha256 credentialSha256 windows"); version(value);
            require(source.identity().equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_SOURCE_PERMISSION_SOURCE");
            JsonNode payload = SubmissionJson.parse(source.original("payload"));
            requireInitial(payload);
            require(text(value, "payloadSha256").equals(SubmissionJson.sha(payload)), "RECOVERY_SOURCE_PERMISSION_PAYLOAD");
            require(text(value, "credentialSha256").equals(sha256(source.original("origin"))), "RECOVERY_SOURCE_PERMISSION_CREDENTIAL");
            var windows = readWindows(value, source.identity());
            var proof = new Proof(source.identity(), text(value, "payloadSha256"), text(value, "credentialSha256"), windows);
            checkPayload(proof, payload, claims);
            return proof;
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_PERMISSION_INVALID", invalid); }
    }

    private List<Window> readWindows(JsonNode value, RecoverySourceIdentity source) {
        var windows = new ArrayList<Window>(); Instant previous = null;
        for (JsonNode row : SubmissionJson.array(value, "windows")) {
            Window window = readWindow(row, source);
            require(previous == null || !window.from().isBefore(previous), "RECOVERY_SOURCE_PERMISSION_WINDOW_ORDER");
            require(previous == null || window.from().equals(previous), "RECOVERY_SOURCE_PERMISSION_WINDOW_GAP");
            previous = window.through(); windows.add(window);
        }
        require(!windows.isEmpty(), "RECOVERY_SOURCE_PERMISSION_WINDOWS");
        return List.copyOf(windows);
    }

    private void requireInitial(JsonNode payload) {
        for (String field : List.of("continuationId", "handoverSessionId", "recoveryPackageId", "handoverId"))
            require(!payload.hasNonNull(field), "RECOVERY_SOURCE_PERMISSION_CHAIN_UNSUPPORTED");
        require(!payload.has("dispositions") || (payload.path("dispositions").isArray() && payload.path("dispositions").isEmpty()),
            "RECOVERY_SOURCE_PERMISSION_CHAIN_UNSUPPORTED");
    }

    private Window readWindow(JsonNode row, RecoverySourceIdentity source) {
        fields(row, WINDOW_FIELDS);
        Instant from = instant(row, "from"), through = instant(row, "through");
        require(from.isBefore(through), "RECOVERY_SOURCE_PERMISSION_WINDOW");
        require(uuid(row, "userId").equals(source.operatorId()) && uuid(row, "deviceId").equals(source.deviceId())
            && uuid(row, "warehouseId").equals(source.warehouseId()), "RECOVERY_SOURCE_PERMISSION_IDENTITY");
        require(revision(row, "userAuthVersion") > 0 && revision(row, "deviceVersion") > 0, "RECOVERY_SOURCE_PERMISSION_VERSION");
        require(row.path("userActive").isBoolean() && row.path("deviceTemporaryOrderEnabled").isBoolean()
            && Set.of("ACTIVE", "DISABLED").contains(text(row, "deviceStatus")), "RECOVERY_SOURCE_PERMISSION_DEVICE");
        return new Window(from, through, text(row, "userAuthVersion"), text(row, "deviceVersion"),
            strings(row, "permissions"), warehouses(row), row.path("userActive").booleanValue(), text(row, "deviceStatus"),
            row.path("deviceTemporaryOrderEnabled").booleanValue());
    }

    private void checkPayload(Proof proof, JsonNode payload, JsonNode claims) {
        JsonNode versions = proof.identity().sourceKind().equals("TEMP") ? claims.path("identity") : claims;
        require(revision(versions, "userAuthVersion") > 0 && revision(versions, "deviceVersion") > 0, "RECOVERY_SOURCE_PERMISSION_VERSION");
        var limits = RecoverySourceSavedTimes.window(claims);
        for (var capture : SubmissionJson.array(payload, "captures"))
            if (capture.hasNonNull("confirmedAt")) checkEvent(proof, capture.path("confirmedAt"), new Credential(versions, limits));
        for (var scan : SubmissionJson.array(payload, "scans")) checkEvent(proof, scan.path("savedAt"), new Credential(versions, limits));
    }

    private void checkEvent(Proof proof, JsonNode time, Credential credential) {
        require(time.isTextual(), "RECOVERY_SOURCE_PERMISSION_TIME");
        Instant saved = Instant.parse(time.textValue());
        require(!saved.isBefore(credential.limits().issued()) && saved.isBefore(credential.limits().expires()),
            "RECOVERY_SOURCE_PERMISSION_CREDENTIAL_WINDOW");
        checkTime(proof, saved, credential.versions());
    }

    private void checkTime(Proof proof, Instant saved, JsonNode versions) {
        Window window = proof.windows().stream().filter(item -> !saved.isBefore(item.from()) && saved.isBefore(item.through()))
            .findFirst().orElseThrow(() -> new RecoveryControlException("RECOVERY_SOURCE_PERMISSION_WINDOW"));
        require(window.userActive() && window.deviceStatus().equals("ACTIVE"), "RECOVERY_SOURCE_PERMISSION_REVOKED");
        require(window.userAuthVersion().equals(text(versions, "userAuthVersion"))
            && window.deviceVersion().equals(text(versions, "deviceVersion")), "RECOVERY_SOURCE_PERMISSION_VERSION");
        require(window.warehouses().contains(proof.identity().warehouseId())
            && window.permissions().containsAll(Set.of("pda:login", "pda:scan")), "RECOVERY_SOURCE_PERMISSION_SCOPE");
        if (proof.identity().sourceKind().equals("TEMP"))
            require(window.deviceTemporaryOrderEnabled() && window.permissions().containsAll(Set.of("temporary_order:create", "pda:submit")),
                "RECOVERY_SOURCE_PERMISSION_SCOPE");
    }

    private Set<String> strings(JsonNode value, String key) {
        var result = new HashSet<String>();
        for (JsonNode item : SubmissionJson.array(value, key)) {
            require(item.isTextual() && item.textValue().matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")
                && result.add(item.textValue()), "RECOVERY_SOURCE_PERMISSION_SCOPE");
        }
        return Set.copyOf(result);
    }

    private Set<UUID> warehouses(JsonNode value) {
        var result = new HashSet<UUID>();
        for (JsonNode item : SubmissionJson.array(value, "warehouseIds")) {
            var wrapper = SubmissionJson.object(); wrapper.set("id", item);
            require(result.add(uuid(wrapper, "id")), "RECOVERY_SOURCE_PERMISSION_SCOPE");
        }
        return Set.copyOf(result);
    }

    public record Window(Instant from, Instant through, String userAuthVersion, String deviceVersion, Set<String> permissions,
            Set<UUID> warehouses, boolean userActive, String deviceStatus, boolean deviceTemporaryOrderEnabled) { }
    public record Proof(RecoverySourceIdentity identity, String payloadSha256, String credentialSha256, List<Window> windows) { }
    private record Credential(JsonNode versions, RecoverySourceSavedTimes.Window limits) { }
}
