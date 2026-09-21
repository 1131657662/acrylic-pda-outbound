import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.CommandReceiptCodec;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;

/** Fresh test key material only; the private key never leaves the temporary directory. */
public final class LedgerFixtureSigner {
    private LedgerFixtureSigner() { }

    public static JsonNode generate(JsonNode input) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path directory = Files.createTempDirectory("acrylic-ledger-vectors-");
        Path privateFile = directory.resolve("synthetic.pk8"), publicFile = directory.resolve("synthetic.der");
        try {
            Files.write(privateFile, pair.getPrivate().getEncoded());
            Files.write(publicFile, pair.getPublic().getEncoded());
            var signer = new OfflineSigner(privateFile.toString(), publicFile.toString(),
                new JsonText(LedgerProtocolDriver.JSON));
            return samples(input, signer);
        } finally {
            Files.deleteIfExists(privateFile);
            Files.deleteIfExists(publicFile);
            Files.deleteIfExists(directory);
        }
    }

    private static JsonNode samples(JsonNode input, OfflineSigner signer) throws Exception {
        var output = LedgerProtocolDriver.JSON.createObjectNode().put("syntheticFactsOnly", true);
        output.set("publicKey", LedgerProtocolDriver.JSON.valueToTree(signer.publicKey()));
        var codec = new CommandReceiptCodec(signer);
        var results = output.putArray("codec");
        for (var sample : input.get("codec")) {
            var receipt = codec.sign(LedgerProtocolDriver.write(sample));
            results.add(LedgerWireOutput.receipt(sample.path("id").asText(), receipt));
        }
        var forged = output.putArray("forged");
        for (var sample : input.get("forged")) forged.add(LedgerWireOutput.forged(sample, signer));
        return output;
    }
}
