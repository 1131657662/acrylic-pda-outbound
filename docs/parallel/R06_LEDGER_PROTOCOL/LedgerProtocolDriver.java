import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Offline adapter to actual C1b codec/signature; no database or service substitute. */
public final class LedgerProtocolDriver {
    static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        JsonNode input = JSON.readTree(Files.readAllBytes(Path.of(args[1])));
        JsonNode result = switch (args[0]) {
            case "generate" -> LedgerFixtureSigner.generate(input);
            case "verify" -> verify(input);
            case "shape" -> shapes(input);
            default -> throw new IllegalArgumentException("Unknown offline mode");
        };
        System.out.println(JSON.writeValueAsString(result));
    }

    static CommandWrite write(JsonNode sample) throws Exception {
        return new CommandWrite(UUID.fromString(sample.path("requestId").asText()),
            UUID.fromString(sample.path("receiptId").asText()),
            UUID.fromString(sample.path("eventId").asText()),
            java.time.Instant.parse(sample.path("recordedAt").asText()),
            CommandInput.parse(JSON.writeValueAsBytes(sample.get("command"))),
            new CommandContext(sample.get("context")), sample.get("actor"));
    }

    private static JsonNode verify(JsonNode input) throws Exception {
        var key = JSON.treeToValue(input.get("publicKey"), OfflineSigner.PublicKeyView.class);
        var output = JSON.createArrayNode();
        for (JsonNode sample : input.get("cases")) {
            var row = output.addObject().put("id", sample.path("id").asText());
            try {
                CommandSignature.verify(sample.path("token").asText(), sample.path("keyId").asText(),
                    sample.get("unsigned"), key);
                row.put("accepted", true);
            } catch (Exception rejected) {
                row.put("accepted", false).put("error", rejected.getClass().getSimpleName());
            }
        }
        return output;
    }

    private static JsonNode shapes(JsonNode input) {
        var output = JSON.createArrayNode();
        for (JsonNode sample : input) {
            var row = output.addObject().put("id", sample.path("id").asText());
            try {
                CommandReceiptShape.validate(sample.get("unsigned"));
                row.put("accepted", true);
            } catch (RuntimeException rejected) {
                row.put("accepted", false).put("error", rejected.getClass().getSimpleName());
            }
        }
        return output;
    }
}
