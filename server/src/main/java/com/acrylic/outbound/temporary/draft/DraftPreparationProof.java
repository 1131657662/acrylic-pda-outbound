package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Verifies original issuer signatures against a configured trusted key, never the uploaded key alone. */
public final class DraftPreparationProof {
    private DraftPreparationProof() { }
    public static JsonNode verify(String token,OfflineSigner.PublicKeyView key,String audience) {
        try {
            DraftEvidenceWire.require(token.length()<=131072 && key!=null && "RS256".equals(key.algorithm()));
            String[] parts=token.split("\\.",-1); DraftEvidenceWire.require(parts.length==3);
            JsonNode header=SubmissionJson.parse(E2aProtocol.canonical(decode(parts[0])));
            DraftEvidenceWire.require(header.size()==3 && "RS256".equals(header.path("alg").textValue())
                && "JWT".equals(header.path("typ").textValue()) && key.keyId().equals(header.path("kid").textValue()));
            byte[] der=Base64.getDecoder().decode(key.publicKey());
            DraftEvidenceWire.require(Base64.getEncoder().encodeToString(der).equals(key.publicKey()));
            var rsa=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            DraftEvidenceWire.require(rsa instanceof java.security.interfaces.RSAPublicKey pub && pub.getModulus().bitLength()>=2048);
            var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(rsa);
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            DraftEvidenceWire.require(verifier.verify(decode(parts[2])));
            var claims=SubmissionJson.parse(E2aProtocol.canonical(decode(parts[1])));
            DraftEvidenceWire.require("acrylic-outbound".equals(claims.path("iss").textValue()) && audience.equals(claims.path("aud").textValue()));
            return claims;
        } catch(GeneralSecurityException | RuntimeException invalid) { throw DraftEvidenceWire.invalid(); }
    }
    private static byte[] decode(String value) {
        DraftEvidenceWire.require(value.matches("[A-Za-z0-9_-]+"));byte[] bytes=Base64.getUrlDecoder().decode(value);
        DraftEvidenceWire.require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value));return bytes;
    }
}
