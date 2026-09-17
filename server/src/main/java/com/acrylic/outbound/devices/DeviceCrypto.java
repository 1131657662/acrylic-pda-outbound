package com.acrylic.outbound.devices;

import com.acrylic.outbound.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;

public final class DeviceCrypto {
    private static final SecureRandom RANDOM=new SecureRandom();
    private DeviceCrypto() { }
    public static String secret() { byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException error) { throw new IllegalStateException(error); }
    }
    public static ECPublicKey publicKey(String encoded) {
        try {
            var key=(ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            var parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
            var p=parameters.getParameterSpec(ECParameterSpec.class);var actual=key.getParams();
            if(!actual.getCurve().equals(p.getCurve()) || !actual.getGenerator().equals(p.getGenerator()) ||
                !actual.getOrder().equals(p.getOrder()) || actual.getCofactor()!=p.getCofactor()) throw new InvalidKeyException();
            if(!Base64.getEncoder().encodeToString(key.getEncoded()).equals(encoded)) throw new InvalidKeyException();
            return key;
        } catch(GeneralSecurityException | IllegalArgumentException | ClassCastException error) {
            throw new BusinessException(400,"INVALID_DEVICE_KEY","设备公钥必须是P-256的标准X509 DER Base64");
        }
    }
    public static void verify(String publicKey,String content,String signature) {
        try {
            var verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(publicKey(publicKey));
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            if(verifier.verify(Base64.getDecoder().decode(signature))) return;
        } catch(GeneralSecurityException | IllegalArgumentException ignored) { }
        throw new BusinessException(401,"INVALID_DEVICE_PROOF","设备签名无效");
    }
}
