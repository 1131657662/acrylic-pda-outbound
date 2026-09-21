import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.command.CommandReceiptCodec;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;

/** Serialize adapter output while preserving the production response byte array. */
public final class LedgerWireOutput {
    private LedgerWireOutput() { }

    public static JsonNode receipt(String id, CommandReceiptCodec.Receipt receipt) {
        return LedgerProtocolDriver.JSON.createObjectNode().put("id", id)
            .put("originalResponseUtf8", new String(receipt.original(), StandardCharsets.UTF_8))
            .put("originalResponseSha256", receipt.originalSha256())
            .put("unsignedReceiptUtf8", new String(receipt.unsigned(), StandardCharsets.UTF_8))
            .put("unsignedReceiptSha256", receipt.unsignedSha256())
            .put("keyId", receipt.keyId()).put("signedReceipt", receipt.token());
    }

    public static JsonNode forged(JsonNode sample, OfflineSigner signer) throws Exception {
        var signed = signer.sign(sample.get("claims"));
        var unsigned = sample.get("unsigned");
        var outer = ((com.fasterxml.jackson.databind.node.ObjectNode) unsigned.deepCopy())
            .put("signedReceipt", signed.token());
        byte[] raw = TemporaryProtocol.canonical(LedgerProtocolDriver.JSON.writeValueAsBytes(outer));
        byte[] unsignedBytes = TemporaryProtocol.canonical(LedgerProtocolDriver.JSON.writeValueAsBytes(unsigned));
        var receipt = new CommandReceiptCodec.Receipt(raw, TemporaryProtocol.originalResponseSha256(raw),
            unsignedBytes, TemporaryProtocol.unsignedReceiptSha256(unsignedBytes), signed.keyId(), signed.token());
        return receipt(sample.path("id").asText(), receipt);
    }
}
