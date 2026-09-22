package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Pure E2a wire primitives; it has no persistence or HTTP side effects. */
public final class E2aProtocol {
    public static final String ROUTE_SCOPE="r06.e2a.evidence.v1", ISSUER="acrylic-outbound";
    public static final String AUDIENCE="acrylic-r06-e2a-intake-v1";
    private E2aProtocol() { }
    public static byte[] canonical(byte[] bytes) { return ProtocolJson.canonical(ProtocolJson.parse(bytes)); }
    public static String pageHash(ObjectNode core) { return digest("PAGE", core); }
    public static Hashes manifestHashes(ObjectNode core) {
        require(core.path("setHash").isMissingNode() && core.path("manifestBytesHash").isMissingNode(),"DERIVED_IN_CORE");
        byte[] bytes=ProtocolJson.canonical(core);
        return new Hashes(hash("MANIFEST",bytes),hash("SET",bytes));
    }
    public static ObjectNode signStop(ObjectNode core, PrivateKey key, String keyId) {
        byte[] input=signingBytes("STOP",core);String signature;
        try { var signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key);signer.update(input);signature=Base64.getEncoder().encodeToString(signer.sign()); }
        catch(GeneralSecurityException error) { throw new IllegalArgumentException("STOP_SIGN_FAILED",error); }
        ObjectNode envelope=SubmissionJson.object().put("schemaVersion",1).put("algorithm","ECDSA_P256_SHA256_DER").put("keyId",keyId);
        envelope.set("core",core.deepCopy()); envelope.put("signature",signature); return envelope;
    }
    public static boolean verifyStop(JsonNode envelope, PublicKey key) {
        try {
            require(envelope.path("algorithm").asText().equals("ECDSA_P256_SHA256_DER"),"STOP_ALGORITHM");
            byte[] signature=Base64.getDecoder().decode(envelope.path("signature").asText());
            var verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);
            verifier.update(signingBytes("STOP",(ObjectNode)envelope.path("core")));
            return verifier.verify(signature);
        } catch(GeneralSecurityException | RuntimeException error) { return false; }
    }
    public static String verifyRsaReceipt(JsonNode receipt, OfflineSigner.PublicKeyView key) {
        try {
            String[] parts=receipt.path("signedReceipt").asText().split("\\.",-1);if(parts.length!=3)return "INVALID_SIGNATURE";
            var decoder=Base64.getUrlDecoder();JsonNode header=ProtocolJson.parse(decoder.decode(parts[0]));
            if(!header.path("alg").asText().equals("RS256") || !header.path("typ").asText().equals("JWT"))return "INVALID_SIGNATURE";
            if(!header.path("kid").asText().equals(key.keyId()))return "UNVERIFIABLE_KEY";
            var publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(publicKey);verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            if(!verifier.verify(decoder.decode(parts[2])))return "INVALID_SIGNATURE";
            JsonNode claims=ProtocolJson.parse(decoder.decode(parts[1]));ObjectNode unsigned=((ObjectNode)receipt).deepCopy();unsigned.remove("signedReceipt");
            return ISSUER.equals(claims.path("iss").asText()) && AUDIENCE.equals(claims.path("aud").asText()) && claims.path("receipt").equals(unsigned)?"VERIFIED":"INVALID_SIGNATURE";
        } catch(GeneralSecurityException | RuntimeException error) { return "INVALID_SIGNATURE"; }
    }
    private static String digest(String domain,ObjectNode value) { return hash(domain,ProtocolJson.canonical(value)); }
    private static byte[] signingBytes(String domain,ObjectNode value) { byte[] body=ProtocolJson.canonical(value);byte[] prefix=("ACRYLIC-R06/E2A/"+domain+"/1\n").getBytes(StandardCharsets.UTF_8);byte[] result=new byte[prefix.length+body.length];System.arraycopy(prefix,0,result,0,prefix.length);System.arraycopy(body,0,result,prefix.length,body.length);return result; }
    private static String hash(String domain,byte[] body) { return com.acrylic.outbound.inventory.ImportFiles.sha256(join(("ACRYLIC-R06/E2A/"+domain+"/1\n").getBytes(StandardCharsets.UTF_8),body)); }
    private static byte[] join(byte[] a,byte[] b) { byte[] result=new byte[a.length+b.length];System.arraycopy(a,0,result,0,a.length);System.arraycopy(b,0,result,a.length,b.length);return result; }
    public record Hashes(String manifestBytesHash,String setHash) { }
}
