import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Adapter only: all canonical/shape/hash decisions execute actual production code. */
public final class FoundationProtocolDriver {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] arguments) throws Exception {
        var samples = JSON.readTree(Files.readAllBytes(Path.of(arguments[1])));
        var output = arguments[0].equals("sign")
            ? SyntheticReceiptDriver.sign(samples) : primitives(samples);
        System.out.write(JSON.writeValueAsBytes(output));
    }

    private static JsonNode primitives(JsonNode samples) {
        var results = JSON.createArrayNode();
        for (var sample : samples) {
            var row = results.addObject().put("id", sample.get("id").asText());
            try {
                row.put("value", evaluate(sample));
            } catch (RuntimeException error) {
                row.put("reject", true).put("exception", error.getClass().getSimpleName());
            }
        }
        return results;
    }

    private static String evaluate(JsonNode sample) {
        byte[] raw = sample.has("rawBase64")
            ? Base64.getDecoder().decode(sample.get("rawBase64").asText())
            : sample.get("raw").asText().getBytes(StandardCharsets.UTF_8);
        return switch (sample.get("mode").asText()) {
            case "canonical" -> new String(TemporaryProtocol.canonical(raw), StandardCharsets.UTF_8);
            case "command" -> new String(TemporaryProtocol.command(raw), StandardCharsets.UTF_8);
            case "commandHash" -> TemporaryProtocol.commandSha256(raw);
            case "receiptHash" -> TemporaryProtocol.unsignedReceiptSha256(raw);
            case "controlHash" -> TemporaryProtocol.derivedControlSha256(raw);
            case "rawHash" -> TemporaryProtocol.originalResponseSha256(raw);
            case "routeScope" -> TemporaryProtocol.ROUTE_SCOPE;
            case "receiptAudience" -> TemporaryProtocol.RECEIPT_AUDIENCE;
            case "issuer" -> TemporaryProtocol.ISSUER;
            case "versions" -> TemporaryProtocol.COMMAND_VERSION + ":" + TemporaryProtocol.RECEIPT_VERSION;
            default -> throw new IllegalArgumentException("Unknown driver mode");
        };
    }
}
