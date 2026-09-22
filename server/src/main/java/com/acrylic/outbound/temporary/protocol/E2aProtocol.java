package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Pure E2a wire primitives; it has no persistence or HTTP side effects. */
public final class E2aProtocol {
    public static final String ROUTE_SCOPE="r06.e2a.evidence.v1", ISSUER="acrylic-outbound";
    public static final String AUDIENCE="acrylic-r06-e2a-intake-v1";
    private E2aProtocol() { }
    public static byte[] canonical(byte[] bytes) { return ProtocolJson.canonical(ProtocolJson.parse(bytes)); }
    public static String pageHash(ObjectNode core) { validatePageCore(core); return digest("PAGE", core); }
    public static void validatePageCore(ObjectNode core) {
        fields(core, Set.of("manifestId","manifestVersion","snapshotId","pageNo","pageCount","isFinal","itemCount","byteCount","prevPageHash","items"));
        text(core,"manifestId"); positive(core,"manifestVersion"); text(core,"snapshotId");
        positive(core,"pageNo"); positive(core,"pageCount"); positiveOrZero(core,"itemCount"); positiveOrZero(core,"byteCount");
        require(core.path("isFinal").isBoolean(),"PAGE_FINAL_TYPE");
        require(core.path("pageNo").asLong()<=core.path("pageCount").asLong(),"PAGE_NO_RANGE");
        require(core.path("isFinal").asBoolean()==(core.path("pageNo").asLong()==core.path("pageCount").asLong()),"PAGE_FINAL_MISMATCH");
        require(core.path("prevPageHash").isNull() || core.path("prevPageHash").asText().matches("[0-9a-f]{64}"),"PAGE_PREV_HASH");
        require(core.path("items").isArray(),"PAGE_ITEMS_TYPE");
    }
    public static Hashes manifestHashes(ObjectNode core) {
        validateManifestCore(core);
        require(core.path("setHash").isMissingNode() && core.path("manifestBytesHash").isMissingNode(),"DERIVED_IN_CORE");
        byte[] bytes=ProtocolJson.canonical(core);
        return new Hashes(hash("MANIFEST",bytes),hash("SET",bytes));
    }
    public static ObjectNode signStop(ObjectNode core, PrivateKey key, String keyId) {
        require(isP256(key),"STOP_KEY");
        require(keyId != null && keyId.matches("[0-9a-f]{64}"),"STOP_KEY_ID");
        byte[] input=signingBytes("STOP",core);String signature;
        try { var signer=Signature.getInstance("SHA256withECDSA");signer.initSign(key);signer.update(input);signature=Base64.getEncoder().encodeToString(signer.sign()); }
        catch(GeneralSecurityException error) { throw new IllegalArgumentException("STOP_SIGN_FAILED",error); }
        ObjectNode envelope=SubmissionJson.object().put("schemaVersion",1).put("algorithm","ECDSA_P256_SHA256_DER").put("keyId",keyId);
        envelope.set("core",core.deepCopy()); envelope.put("signature",signature); return envelope;
    }
    public static boolean verifyStop(JsonNode envelope, PublicKey key) {
        try {
            fields(envelope,Set.of("schemaVersion","algorithm","keyId","core","signature"));
            require(envelope.path("schemaVersion").asInt()==1,"STOP_SCHEMA");
            require(envelope.path("algorithm").asText().equals("ECDSA_P256_SHA256_DER"),"STOP_ALGORITHM");
            require(key instanceof java.security.interfaces.ECPublicKey ec && isP256(ec),"STOP_KEY");
            String computed=hex(key.getEncoded());require(computed.equals(envelope.path("keyId").asText()),"STOP_KEY_ID");
            require(envelope.path("core").isObject(),"STOP_CORE");
            byte[] signature=decodeStandardBase64(envelope.path("signature"));
            var verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);
            verifier.update(signingBytes("STOP",(ObjectNode)envelope.path("core")));
            return verifier.verify(signature);
        } catch(GeneralSecurityException | RuntimeException error) { return false; }
    }
    public static String verifyRsaReceipt(JsonNode receipt, OfflineSigner.PublicKeyView key) {
        try {
            require(key.algorithm().equals("RS256"),"RSA_ALGORITHM");
            require(receipt != null && receipt.isObject() && receipt.path("signedReceipt").isTextual(),"RSA_RECEIPT");
            String[] parts=receipt.path("signedReceipt").asText().split("\\.",-1);if(parts.length!=3)return "INVALID_SIGNATURE";
            JsonNode header=ProtocolJson.parse(decodeUrlBase64(parts[0]));
            fields(header,Set.of("alg","typ","kid"));
            text(header,"alg"); text(header,"typ"); text(header,"kid");
            if(!header.path("alg").asText().equals("RS256") || !header.path("typ").asText().equals("JWT"))return "INVALID_SIGNATURE";
            if(!header.path("kid").asText().equals(key.keyId()))return "UNVERIFIABLE_KEY";
            byte[] der=decodeStandardBase64(key.publicKey());
            var publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            require(publicKey instanceof java.security.interfaces.RSAPublicKey rsa && rsa.getModulus().bitLength()>=2048 && rsa.getPublicExponent().compareTo(java.math.BigInteger.ONE)>0 && rsa.getPublicExponent().testBit(0),"RSA_KEY");
            var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(publicKey);verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            if(!verifier.verify(decodeUrlBase64(parts[2])))return "INVALID_SIGNATURE";
            JsonNode claims=ProtocolJson.parse(decodeUrlBase64(parts[1]));fields(claims,Set.of("iss","aud","receipt"));text(claims,"iss"); text(claims,"aud"); require(claims.path("receipt").isObject(),"RSA_CLAIMS_RECEIPT");ObjectNode unsigned=((ObjectNode)receipt).deepCopy();unsigned.remove("signedReceipt");
            return ISSUER.equals(claims.path("iss").asText()) && AUDIENCE.equals(claims.path("aud").asText()) && claims.path("receipt").equals(unsigned)?"VERIFIED":"INVALID_SIGNATURE";
        } catch(GeneralSecurityException | RuntimeException error) { return "INVALID_SIGNATURE"; }
    }
    public static String intakeSha256(byte[] canonicalRequest) { return hash("INTAKE", canonical(canonicalRequest)); }
    public static boolean trustedFacts(JsonNode actual, JsonNode expected) { return actual != null && expected != null && actual.isObject() && expected.isObject() && actual.equals(expected); }
    private static void validateManifestCore(ObjectNode core) {
        fields(core,Set.of("schemaVersion","manifestId","manifestVersion","snapshotId","source","workRound","pageCount","entryCount","byteCount","pages","endPageNo"));
        require(core.path("schemaVersion").asInt()==1,"MANIFEST_SCHEMA"); text(core,"manifestId"); positive(core,"manifestVersion"); text(core,"snapshotId"); text(core,"source"); positive(core,"workRound"); positive(core,"pageCount"); positiveOrZero(core,"entryCount"); positiveOrZero(core,"byteCount"); positive(core,"endPageNo"); require(core.path("endPageNo").asLong()==core.path("pageCount").asLong(),"MANIFEST_END"); require(core.path("pages").isArray() && core.path("pages").size()==core.path("pageCount").asInt(),"MANIFEST_PAGES");
        long prior=0; for(JsonNode page:core.path("pages")){ fields(page,Set.of("pageNo","pageHash")); positive(page,"pageNo"); require(page.path("pageNo").asLong()==++prior,"MANIFEST_PAGE_ORDER"); require(page.path("pageHash").asText().matches("[0-9a-f]{64}"),"MANIFEST_PAGE_HASH"); }
    }
    private static void fields(JsonNode value,Set<String> expected){ require(value!=null && value.isObject(),"FIELDS"); Set<String> actual=new java.util.HashSet<>(); value.fieldNames().forEachRemaining(actual::add); require(actual.equals(expected),"FIELDS"); }
    private static void text(JsonNode value,String key){ require(value.path(key).isTextual() && !value.path(key).asText().isBlank(),key+"_TEXT"); }
    private static void positive(JsonNode value,String key){ require(number(value.path(key))>0,key+"_POSITIVE"); }
    private static void positiveOrZero(JsonNode value,String key){ require(number(value.path(key))>=0,key+"_NONNEGATIVE"); }
    private static long number(JsonNode node){ require(node.isIntegralNumber() || node.isTextual(),"NUMBER"); try { return Long.parseLong(node.asText()); } catch(NumberFormatException e){ throw new IllegalArgumentException("NUMBER"); } }
    private static String hex(byte[] bytes){ return com.acrylic.outbound.inventory.ImportFiles.sha256(bytes); }
    private static byte[] decodeStandardBase64(JsonNode value) {
        require(value != null && value.isTextual(),"BASE64_TYPE"); String encoded=value.asText();
        byte[] decoded; try { decoded=Base64.getDecoder().decode(encoded); } catch(IllegalArgumentException e) { throw new IllegalArgumentException("BASE64_INVALID",e); }
        require(Base64.getEncoder().encodeToString(decoded).equals(encoded),"BASE64_NONCANONICAL"); return decoded;
    }
    private static byte[] decodeStandardBase64(String value) {
        require(value != null,"BASE64_TYPE"); byte[] decoded; try { decoded=Base64.getDecoder().decode(value); } catch(IllegalArgumentException e) { throw new IllegalArgumentException("BASE64_INVALID",e); }
        require(Base64.getEncoder().encodeToString(decoded).equals(value),"BASE64_NONCANONICAL"); return decoded;
    }
    private static byte[] decodeUrlBase64(String encoded) {
        require(encoded != null && !encoded.isEmpty() && !encoded.contains("="),"BASE64URL_NONCANONICAL");
        byte[] decoded; try { decoded=Base64.getUrlDecoder().decode(encoded); } catch(IllegalArgumentException e) { throw new IllegalArgumentException("BASE64URL_INVALID",e); }
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encoded),"BASE64URL_NONCANONICAL"); return decoded;
    }
    private static boolean isP256(Key key) {
        if(!(key instanceof java.security.interfaces.ECKey ec)) return false;
        try {
            var parameters=AlgorithmParameters.getInstance("EC"); parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec expected=parameters.getParameterSpec(ECParameterSpec.class), actual=ec.getParams();
            return actual != null && actual.getCurve().equals(expected.getCurve()) && actual.getGenerator().equals(expected.getGenerator()) && actual.getOrder().equals(expected.getOrder()) && actual.getCofactor()==expected.getCofactor();
        } catch(GeneralSecurityException error) { return false; }
    }
    private static String digest(String domain,ObjectNode value) { return hash(domain,ProtocolJson.canonical(value)); }
    private static byte[] signingBytes(String domain,ObjectNode value) { byte[] body=ProtocolJson.canonical(value);byte[] prefix=("ACRYLIC-R06/E2A/"+domain+"/1\n").getBytes(StandardCharsets.UTF_8);byte[] result=new byte[prefix.length+body.length];System.arraycopy(prefix,0,result,0,prefix.length);System.arraycopy(body,0,result,prefix.length,body.length);return result; }
    private static String hash(String domain,byte[] body) { return com.acrylic.outbound.inventory.ImportFiles.sha256(join(("ACRYLIC-R06/E2A/"+domain+"/1\n").getBytes(StandardCharsets.UTF_8),body)); }
    private static byte[] join(byte[] a,byte[] b) { byte[] result=new byte[a.length+b.length];System.arraycopy(a,0,result,0,a.length);System.arraycopy(b,0,result,a.length,b.length);return result; }
    public record Hashes(String manifestBytesHash,String setHash) { }
}
