import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.security.KeyPairGenerator;

/** Synthetic signed content, never a database command or a production receipt writer. */
public final class SyntheticReceiptDriver {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static JsonNode sign(JsonNode samples) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var directory = Files.createTempDirectory("acrylic-foundation-synthetic-");
        var privateFile = directory.resolve("synthetic.pk8");
        var publicFile = directory.resolve("synthetic.der");
        try {
            Files.write(privateFile, pair.getPrivate().getEncoded());
            Files.write(publicFile, pair.getPublic().getEncoded());
            var signer = new OfflineSigner(privateFile.toString(), publicFile.toString(), new JsonText(JSON));
            return cases(samples, signer);
        } finally {
            Files.deleteIfExists(privateFile);
            Files.deleteIfExists(publicFile);
            Files.deleteIfExists(directory);
        }
    }

    private static JsonNode cases(JsonNode samples, OfflineSigner signer) throws Exception {
        var output = JSON.createObjectNode().put("syntheticOnly", true);
        output.set("publicKey", JSON.valueToTree(signer.publicKey()));
        var results = output.putArray("vectors");
        for (var sample : samples) {
            var token = signer.sign(sample.get("claims"));
            var receipt = sample.get("claims").get("receipt").deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).put("signedReceipt", token.token());
            byte[] raw = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt);
            var row = results.addObject().put("id", sample.get("id").asText());
            row.put("originalResponseUtf8", new String(raw, java.nio.charset.StandardCharsets.UTF_8));
            row.put("originalResponseSha256", TemporaryProtocol.originalResponseSha256(raw));
            row.put("unsignedReceiptSha256", TemporaryProtocol.unsignedReceiptSha256(
                JSON.writeValueAsBytes(sample.get("claims").get("receipt"))));
        }
        return output;
    }
}
