package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Public signed evidence only; the enclosing archive owns integrity and closure verification. */
public record RelayIndependentFacts(List<JsonNode> lines,List<JsonNode> scans) {
    static RelayIndependentFacts read(Map<String,byte[]> files,JsonNode source) {
        return source==null?null:new RelayIndependentFacts(rows(files.get("lines.ndjson")),rows(files.get("scans.ndjson")));
    }
    private static List<JsonNode> rows(byte[] bytes) {
        return new String(bytes,StandardCharsets.UTF_8).lines().filter(line->!line.isBlank()).map(line->parse(line)).toList();
    }
    public List<JsonNode> active() { return scans.stream().filter(row->text(row,"status").equals("ACTIVE")).toList(); }
}
