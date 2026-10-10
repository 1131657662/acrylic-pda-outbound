package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Historical no-change windows require independent original pins and a constrained writer profile; ordinary admission stays closed. */
public final class RecoveryPermissionContinuity {
    private static final String FIELDS = "formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt "
        + "userId deviceId effective binlogFile binlogPosition binlogPrefixSha256";
    private RecoveryPermissionContinuity() { }

    public static Proof verify(Request request) {
        return verify(request, false);
    }

    /** Establishes a historical window only from independently pinned original boundaries with constrained accounts. */
    public static Proof verifyControlled(Request request) {
        return verify(request, true);
    }

    private static Proof verify(Request request, boolean controlled) {
        require(request != null, "RECOVERY_PERMISSION_CONTINUITY_REQUEST");
        var begin = boundary(new Boundary(request.begin(), request.beginSha256(), request.expected()), controlled);
        var end = boundary(new Boundary(request.end(), request.endSha256(), request.expected()), controlled);
        if (controlled) require(begin.path("writerProfile").equals(end.path("writerProfile")), "RECOVERY_PERMISSION_WRITER_CHANGED");
        require(text(begin, "binlogFile").equals(text(end, "binlogFile")), "RECOVERY_PERMISSION_ROTATION_UNSUPPORTED");
        require(begin.path("effective").equals(end.path("effective")), "RECOVERY_PERMISSION_BOUNDARY_CHANGED");
        Instant from = instant(begin, "observedAt"), through = instant(end, "observedAt");
        require(from.isBefore(through), "RECOVERY_PERMISSION_TIME");
        long start = revision(begin, "binlogPosition"), finish = revision(end, "binlogPosition"); byte[] log = request.binlog();
        require(start <= finish && finish == log.length, "RECOVERY_PERMISSION_LOG_RANGE");
        pin(log, request.binlogSha256(), RecoveryBinlogFile.MAX_BYTES);
        require(text(end, "binlogPrefixSha256").equals(request.binlogSha256())
            && text(begin, "binlogPrefixSha256").equals(sha256(Arrays.copyOf(log, Math.toIntExact(start)))),
            "RECOVERY_PERMISSION_LOG_PREFIX_CHANGED");
        var events = RecoveryBinlogFile.read(log);
        var interval = new RecoveryBinlogTransactions.Interval(request.expected().database().schema(), Math.toIntExact(start),
            Math.toIntExact(finish), from, through);
        var summary = RecoveryBinlogTransactions.verify(events, interval);
        return new Proof(request, begin.path("effective"), new Verification(from, through, summary, controlled));
    }

    private static JsonNode boundary(Boundary input, boolean controlled) {
        pin(input.bytes(), input.sha256(), RecoveryControlJson.MAX_BYTES); var value = readCanonical(input.bytes());
        fields(value, FIELDS + (controlled ? " writerProfile" : "")); version(value); var expected = input.expected();
        String kind = controlled ? "ORIGINAL_CONTROLLED_PERMISSION_BOUNDARY" : "ORIGINAL_PERMISSION_BOUNDARY";
        require(text(value, "observationKind").equals(kind), "RECOVERY_PERMISSION_KIND");
        require(uuid(value, "mysqlServerUuid").equals(expected.database().mysqlServerUuid())
            && text(value, "databaseSchema").equals(expected.database().schema())
            && text(value, "sourceRecoveryEpoch").equals(expected.database().sourceRecoveryEpoch()), "RECOVERY_PERMISSION_DATABASE");
        require(uuid(value, "userId").equals(expected.operatorId()) && uuid(value, "deviceId").equals(expected.deviceId()),
            "RECOVERY_PERMISSION_IDENTITY");
        require(text(value, "binlogFile").matches("[A-Za-z0-9_-]{1,100}\\.[0-9]{6}") && revision(value, "binlogPosition") > 4,
            "RECOVERY_PERMISSION_POSITION");
        hashField(value, "binlogPrefixSha256");
        if (controlled) RecoveryPermissionWriterProfile.verify(value.path("writerProfile"), expected.database().schema());
        effective(value.path("effective")); return value;
    }

    private static void effective(JsonNode value) {
        fields(value, "userActive userAuthVersion warehouseId deviceStatus deviceVersion deviceTemporaryOrderEnabled permissions warehouseIds");
        require(value.path("userActive").isBoolean() && value.path("deviceTemporaryOrderEnabled").isBoolean()
            && Set.of("ACTIVE", "DISABLED").contains(text(value, "deviceStatus")), "RECOVERY_PERMISSION_STATE");
        require(revision(value, "userAuthVersion") > 0 && revision(value, "deviceVersion") > 0, "RECOVERY_PERMISSION_VERSION");
        uuid(value, "warehouseId"); var permissions = value.path("permissions"); var warehouses = value.path("warehouseIds");
        require(permissions.isArray() && warehouses.isArray() && permissions.size() <= 512 && warehouses.size() <= 512,
            "RECOVERY_PERMISSION_SCOPE");
        var seen = new HashSet<String>();
        for (var permission : permissions)
            require(permission.isTextual() && permission.textValue().matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")
                && seen.add(permission.textValue()), "RECOVERY_PERMISSION_SCOPE");
        seen.clear();
        for (var warehouse : warehouses) {
            var wrapper = com.acrylic.outbound.submissions.SubmissionJson.object(); wrapper.set("id", warehouse);
            require(seen.add(uuid(wrapper, "id").toString()), "RECOVERY_PERMISSION_SCOPE");
        }
    }

    private static void pin(byte[] bytes, String hash, int limit) {
        require(bytes != null && bytes.length > 0 && bytes.length <= limit && hash != null && hash.matches("[a-f0-9]{64}")
            && !hash.equals(ZERO_HASH), "RECOVERY_PERMISSION_PIN");
        require(sha256(bytes).equals(hash), "RECOVERY_PERMISSION_PIN_MISMATCH");
    }

    public record Request(RecoveryPermissionBoundary.Request expected, byte[] begin, String beginSha256,
            byte[] end, String endSha256, byte[] binlog, String binlogSha256) {
        public Request {
            require(expected != null && begin != null && begin.length <= RecoveryControlJson.MAX_BYTES
                && end != null && end.length <= RecoveryControlJson.MAX_BYTES && binlog != null && binlog.length <= RecoveryBinlogFile.MAX_BYTES,
                "RECOVERY_PERMISSION_CONTINUITY_REQUEST");
            begin = begin.clone(); end = end.clone(); binlog = binlog.clone();
        }
        @Override public byte[] begin() { return begin.clone(); }
        @Override public byte[] end() { return end.clone(); }
        @Override public byte[] binlog() { return binlog.clone(); }
    }
    public static final class Proof {
        private final RecoveryPermissionBoundary.Request identity;
        private final String beginSha256, endSha256, binlogSha256;
        private final Instant from, through;
        private final JsonNode effective;
        private final long committedTransactions, rowEvents;
        private final boolean historicalAuthorityEstablished;

        private Proof(Request input, JsonNode effective, Verification verified) {
            identity = input.expected(); beginSha256 = input.beginSha256(); endSha256 = input.endSha256(); binlogSha256 = input.binlogSha256();
            from = verified.from(); through = verified.through(); this.effective = effective.deepCopy();
            committedTransactions = verified.summary().commits(); rowEvents = verified.summary().rowEvents();
            historicalAuthorityEstablished = verified.controlled();
        }
        public RecoveryPermissionBoundary.Request identity() { return identity; }
        public Instant from() { return from; }
        public Instant through() { return through; }
        public JsonNode effective() { return effective.deepCopy(); }
        public String beginSha256() { return beginSha256; }
        public String endSha256() { return endSha256; }
        public String binlogSha256() { return binlogSha256; }
        public long committedTransactions() { return committedTransactions; }
        public long rowEvents() { return rowEvents; }
        public boolean historicalAuthorityEstablished() { return historicalAuthorityEstablished; }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
    private record Verification(Instant from, Instant through, RecoveryBinlogTransactions.Summary summary, boolean controlled) { }
    private record Boundary(byte[] bytes, String sha256, RecoveryPermissionBoundary.Request expected) { }
}
