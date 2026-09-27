package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CommandSignature {
    private final OfflineSigner signer;
    public CommandSignature(OfflineSigner signer) { this.signer=signer; }
    public void verify(String token,String storedKid,JsonNode unsigned) {
        try { verify(token,storedKid,unsigned,signer.publicKeyFor(storedKid)); }
        catch(GeneralSecurityException | RuntimeException failure) { throw CommandErrors.corrupt(failure); }
    }
    public static void verify(String token,String storedKid,JsonNode unsigned,OfflineSigner.PublicKeyView key) throws GeneralSecurityException {
        String[] parts=token.split("\\.",-1);CommandErrors.integrity(parts.length==3,"jwt has three parts: " + parts.length);
        byte[] h=decode(parts[0]),claims=decode(parts[1]),signature=decode(parts[2]);
        JsonNode header=SubmissionJson.parse(TemporaryProtocol.canonical(h));
        CommandErrors.integrity(header.size()==3 && header.path("alg").asText().equals("RS256")
            && header.path("typ").asText().equals("JWT") && header.path("kid").asText().equals(storedKid)
            && storedKid.equals(key.keyId()) && key.algorithm().equals("RS256"),
            "header/alg/kid: header=" + header + " storedKid=" + storedKid + " keyId=" + key.keyId()
                + " alg=" + header.path("alg").asText() + " keyAlgorithm=" + key.algorithm());
        var rsa=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
        CommandErrors.integrity(rsa instanceof java.security.interfaces.RSAPublicKey publicKey && publicKey.getModulus().bitLength()>=2048,
            "rsa modulus bits: " + (rsa instanceof java.security.interfaces.RSAPublicKey p ? p.getModulus().bitLength() : -1));
        var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(rsa);
        verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        CommandErrors.integrity(verifier.verify(signature),"signature does not verify: kid=" + storedKid + " tokenLength=" + token.length()
            + " unsignedLength=" + unsigned.toString().length());
        var payload=SubmissionJson.parse(TemporaryProtocol.canonical(claims));
        CommandErrors.integrity(payload.size()==3 && payload.path("iss").asText().equals(TemporaryProtocol.ISSUER)
            && payload.path("aud").asText().equals(TemporaryProtocol.RECEIPT_AUDIENCE) && unsigned.equals(payload.get("receipt")),
            "payload claims: size=" + payload.size() + " iss=" + payload.path("iss").asText() + " aud=" + payload.path("aud").asText()
                + " receiptEqual=" + unsigned.equals(payload.get("receipt")));
    }
    private static byte[] decode(String part) {
        CommandErrors.integrity(part.matches("[A-Za-z0-9_-]+"),"jwt part not base64url: length=" + part.length());byte[] bytes=Base64.getUrlDecoder().decode(part);
        CommandErrors.integrity(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(part),"jwt part not canonical base64url");return bytes;
    }
}
