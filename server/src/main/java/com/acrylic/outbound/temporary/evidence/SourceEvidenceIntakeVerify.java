package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SourceEvidenceIntakeVerify {
    private static final Set<String> FIELDS = Set.of("operation", "openOriginalKey", "stopOriginalKey",
        "manifestId", "manifestVersion", "setHash", "verificationVersion", "algorithmVersion");

    private SourceEvidenceIntakeVerify() { }

    public static Parsed parse(byte[] bytes, UUID expectedOpenKey, UUID expectedStopKey,
        SourceEvidenceIntakeOpen.Parsed open, SourceEvidenceIntakeStop.Parsed stop) {
        var request = SourceEvidenceIntakeReplay.canonicalize(bytes);
        var body = SubmissionJson.parse(request.bytes());
        require(body.isObject() && fields(body).equals(FIELDS), "VERIFY_FIELDS");
        require("VERIFY".equals(body.path("operation").textValue()), "VERIFY_OPERATION");
        require(uuid(body, "openOriginalKey").equals(expectedOpenKey), "VERIFY_OPEN_KEY");
        require(uuid(body, "stopOriginalKey").equals(expectedStopKey), "VERIFY_STOP_KEY");
        require(open.manifestId().equals(body.path("manifestId").textValue())
            && open.manifestVersion() == positive(body, "manifestVersion"), "VERIFY_MANIFEST_CONTEXT");
        require(stop.setHash().equals(body.path("setHash").textValue()), "VERIFY_SET_HASH");
        long version = positive(body, "verificationVersion");
        long algorithm = positive(body, "algorithmVersion");
        return new Parsed(request.bytes(), request.sha256(), expectedOpenKey, expectedStopKey,
            body.path("manifestId").textValue(), version, algorithm, stop.setHash());
    }

    private static Set<String> fields(JsonNode body) {
        var fields = new HashSet<String>(); body.fieldNames().forEachRemaining(fields::add); return fields;
    }

    private static UUID uuid(JsonNode body, String name) {
        var value = body.path(name);
        require(value.isTextual(), "VERIFY_UUID");
        try { var parsed = UUID.fromString(value.textValue()); require(parsed.toString().equals(value.textValue()), "VERIFY_UUID"); return parsed; }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("VERIFY_UUID", invalid); }
    }

    private static long positive(JsonNode body, String name) {
        var value = body.path(name);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0, "VERIFY_NUMBER");
        require(value.longValue() <= 9007199254740991L, "VERIFY_NUMBER"); return value.longValue();
    }

    private static void require(boolean valid, String code) { if (!valid) throw new IllegalArgumentException(code); }

    public record Parsed(byte[] canonicalBytes, String requestSha256, UUID openOriginalKey, UUID stopOriginalKey,
        String manifestId, long verificationVersion, long algorithmVersion, String setHash) {
        public Parsed { canonicalBytes = canonicalBytes.clone(); }
        @Override public byte[] canonicalBytes() { return canonicalBytes.clone(); }
    }
}
