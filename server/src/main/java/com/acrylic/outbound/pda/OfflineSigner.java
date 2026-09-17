package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfflineSigner {
    private final String privatePath;private final String publicPath;private final JsonText json;
    private volatile Keys keys;
    public OfflineSigner(@Value("${outbound.signing.private-key:}") String privatePath,
        @Value("${outbound.signing.public-key:}") String publicPath,JsonText json) { this.privatePath=privatePath;this.publicPath=publicPath;this.json=json; }
    public Signed sign(Object claims) {
        var key=keys();String header=encode(json.encode(Map.of("alg","RS256","typ","JWT","kid",key.id())));
        String payload=header+"."+encode(json.encode(claims));
        try {
            var signer=Signature.getInstance("SHA256withRSA");signer.initSign(key.privateKey());signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            return new Signed(key.id(),payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        } catch(GeneralSecurityException error) { throw new IllegalStateException("Offline signature failed",error); }
    }
    public PublicKeyView publicKey() { var key=keys();return new PublicKeyView(key.id(),"RS256",Base64.getEncoder().encodeToString(key.publicKey().getEncoded())); }
    private synchronized Keys keys() {
        if(keys!=null) return keys;
        if(privatePath.isBlank() || publicPath.isBlank()) throw new BusinessException(503,"SIGNING_KEY_UNAVAILABLE","离线签名密钥未配置，暂不能领取订单");
        try {
            var factory=KeyFactory.getInstance("RSA");var privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(privatePath))));
            var publicKey=factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(publicPath))));
            if(!(publicKey instanceof java.security.interfaces.RSAPublicKey rsa) || rsa.getModulus().bitLength()<2048) throw new InvalidKeyException();
            var signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey);signature.update(new byte[]{1});byte[] proof=signature.sign();
            signature.initVerify(publicKey);signature.update(new byte[]{1});if(!signature.verify(proof)) throw new InvalidKeyException();
            keys=new Keys("offline-"+DeviceCrypto.sha(Base64.getEncoder().encodeToString(publicKey.getEncoded())).substring(0,32),privateKey,publicKey);return keys;
        } catch(java.io.IOException | GeneralSecurityException error) {
            throw new BusinessException(503,"SIGNING_KEY_UNAVAILABLE","离线签名密钥不可用，暂不能领取订单");
        }
    }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private record Keys(String id,PrivateKey privateKey,PublicKey publicKey) { }
    public record Signed(String keyId,String token) { }
    public record PublicKeyView(String keyId,String algorithm,String publicKey) { }
}
