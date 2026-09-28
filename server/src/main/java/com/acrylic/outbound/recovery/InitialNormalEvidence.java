package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original, independently trusted initial registration request. Never invents identity or mapping. */
public final class InitialNormalEvidence {
    private final JsonNode value;

    private InitialNormalEvidence(JsonNode value) { this.value = value; }

    public static InitialNormalEvidence parse(byte[] original) {
        JsonNode value = readCanonical(original);
        fields(value, "formatVersion kind installationId epochId numericEpoch mysqlServerUuid databaseSchema "
            + "commandId sourceEvidenceSha256 operatorId requestedAt");
        version(value);
        require("INITIAL_NORMAL".equals(text(value, "kind")), "INITIAL_NORMAL_KIND");
        for (String key : new String[]{"installationId", "epochId", "mysqlServerUuid", "commandId"}) uuid(value, key);
        require(revision(value, "numericEpoch") > 0, "INITIAL_NORMAL_EPOCH");
        require(text(value, "databaseSchema").matches("[A-Za-z0-9_]{1,64}"), "INITIAL_NORMAL_SCHEMA");
        identifier(value, "operatorId");
        require(text(value, "operatorId").length() <= 64, "INITIAL_NORMAL_OPERATOR");
        hashField(value, "sourceEvidenceSha256");
        require(!ZERO_HASH.equals(text(value, "sourceEvidenceSha256")), "INITIAL_NORMAL_SOURCE_REQUIRED");
        instant(value, "requestedAt");
        return new InitialNormalEvidence(value);
    }

    public byte[] originalBytes() { return RecoveryControlJson.encode(value); }
    public String sha256() { return hash(value); }
    public UUID installation() { return uuid(value, "installationId"); }
    public UUID epoch() { return uuid(value, "epochId"); }
    public UUID serverUuid() { return uuid(value, "mysqlServerUuid"); }
    public UUID command() { return uuid(value, "commandId"); }
    public long numericEpoch() { return revision(value, "numericEpoch"); }
    public String schema() { return text(value, "databaseSchema"); }
    public String operator() { return text(value, "operatorId"); }
}
