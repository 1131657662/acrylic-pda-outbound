import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;

/** Offline oracle using the unmodified production parser/signer; no Spring or database. */
public final class TemporaryCommandVectorOracle {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        var input = JSON.readTree(Files.readAllBytes(Path.of(args[1])));
        var output = args[0].equals("canonical") ? canonical(input) : signatures(input);
        System.out.write(JSON.writeValueAsString(output).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode canonical(JsonNode input) {
        var results = JSON.createArrayNode();
        for (var sample : input) {
            var row = results.addObject().put("id", sample.get("id").asText());
            try {
                var parsed = SubmissionJson.parse(sample.get("raw").asText());
                row.put("canonical", SubmissionJson.canonical(parsed));
                row.put("sha256", SubmissionJson.sha(parsed));
            } catch (RuntimeException error) {
                row.put("reject", true).put("exception", error.getClass().getSimpleName());
            }
        }
        return results;
    }

    private static JsonNode signatures(JsonNode input) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var directory = Files.createTempDirectory("acrylic-c0-synthetic-");
        var privateFile = directory.resolve("private.pk8");
        var publicFile = directory.resolve("public.der");
        try {
            Files.write(privateFile, keys.getPrivate().getEncoded());
            Files.write(publicFile, keys.getPublic().getEncoded());
            var signer = new OfflineSigner(privateFile.toString(), publicFile.toString(), new JsonText(JSON));
            return signCases(input, signer);
        } finally {
            Files.deleteIfExists(privateFile);
            Files.deleteIfExists(publicFile);
            Files.deleteIfExists(directory);
        }
    }

    private static JsonNode signCases(JsonNode input, OfflineSigner signer) {
        var result = JSON.createObjectNode().put("syntheticOnly", true);
        result.put("status", "C0_FIXED_PROPOSAL_NOT_IMPLEMENTED");
        result.set("publicKey", JSON.valueToTree(signer.publicKey()));
        var vectors = result.putArray("vectors");
        for (var sample : input.get("signatureCases")) {
            var claims = JSON.createObjectNode().put("iss", "acrylic-outbound");
            claims.put("aud", "acrylic-r06-command-receipt-v1-proposal");
            claims.set("receipt", input.get("originalCommandReceipt").deepCopy());
            if (sample.has("path")) replace(claims, sample.get("path").asText(), sample.get("value"));
            var signed = signer.sign(claims);
            var outer = ((ObjectNode) claims.get("receipt")).deepCopy().put("signedReceipt", signed.token());
            var row = vectors.addObject().put("id", sample.get("id").asText());
            row.set("expectedError", sample.get("expectedError"));
            row.set("originalCommandReceipt", outer);
        }
        return result;
    }

    private static void replace(ObjectNode root, String path, JsonNode value) {
        var parts = path.split("\\.");
        JsonNode parent = root;
        for (int index = 0; index < parts.length - 1; index++) parent = parent.get(parts[index]);
        ((ObjectNode) parent).set(parts[parts.length - 1], value.deepCopy());
    }
}
