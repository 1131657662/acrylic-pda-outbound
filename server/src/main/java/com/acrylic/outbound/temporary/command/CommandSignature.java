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
        catch(GeneralSecurityException | RuntimeException failure) { throw CommandErrors.corrupt(); }
    }
    public static void verify(String token,String storedKid,JsonNode unsigned,OfflineSigner.PublicKeyView key) throws GeneralSecurityException {
        String[] parts=token.split("\\.",-1);CommandErrors.integrity(parts.length==3);
        byte[] h=decode(parts[0]),claims=decode(parts[1]),signature=decode(parts[2]);
        JsonNode header=SubmissionJson.parse(TemporaryProtocol.canonical(h));
        CommandErrors.integrity(header.size()==3 && header.path("alg").asText().equals("RS256")
            && header.path("typ").asText().equals("JWT") && header.path("kid").asText().equals(storedKid)
            && storedKid.equals(key.keyId()) && key.algorithm().equals("RS256"));
        var rsa=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
        CommandErrors.integrity(rsa instanceof java.security.interfaces.RSAPublicKey publicKey && publicKey.getModulus().bitLength()>=2048);
        var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(rsa);
        verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        CommandErrors.integrity(verifier.verify(signature));
        var payload=SubmissionJson.parse(TemporaryProtocol.canonical(claims));
        CommandErrors.integrity(payload.size()==3 && payload.path("iss").asText().equals(TemporaryProtocol.ISSUER)
            && payload.path("aud").asText().equals(TemporaryProtocol.RECEIPT_AUDIENCE) && unsigned.equals(payload.get("receipt")));
    }
    private static byte[] decode(String part) {
        CommandErrors.integrity(part.matches("[A-Za-z0-9_-]+"));byte[] bytes=Base64.getUrlDecoder().decode(part);
        CommandErrors.integrity(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(part));return bytes;
    }
}
