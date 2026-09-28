package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** A durable witness of a confirmed database commit, not a database proof or a write capability. */
record InitialNormalCompletion(String requestSha256, String databaseRequestSha256) {
    static InitialNormalCompletion parse(byte[] bytes) {
        var value = readCanonical(bytes);
        fields(value, "formatVersion kind requestSha256 databaseRequestSha256");
        version(value);
        require("INITIAL_NORMAL_COMMITTED".equals(text(value, "kind")), "INITIAL_NORMAL_COMPLETION_KIND");
        hashField(value, "requestSha256"); hashField(value, "databaseRequestSha256");
        return new InitialNormalCompletion(text(value, "requestSha256"), text(value, "databaseRequestSha256"));
    }

    byte[] bytes() {
        var value = JsonNodeFactory.instance.objectNode();
        value.put("formatVersion", 1).put("kind", "INITIAL_NORMAL_COMMITTED")
            .put("requestSha256", requestSha256).put("databaseRequestSha256", databaseRequestSha256);
        return RecoveryControlJson.encode(value);
    }

    void requireMatches(InitialNormalEvidence request) {
        require(request.sha256().equals(requestSha256) && requestSha256.equals(databaseRequestSha256),
            "INITIAL_NORMAL_COMPLETION_CONFLICT");
    }
}
