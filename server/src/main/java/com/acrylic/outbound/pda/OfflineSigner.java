package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfflineSigner {
    private final String privatePath;private final String publicPath;private final String historicalPublicKeys;private final JsonText json;
    private volatile Keys keys;
    private volatile Map<String,PublicKeyView> history;
    @Autowired
    public OfflineSigner(@Value("${outbound.signing.private-key:}") String privatePath,
        @Value("${outbound.signing.public-key:}") String publicPath,
        @Value("${outbound.signing.historical-public-keys:}") String historicalPublicKeys,JsonText json) {
        this.privatePath=privatePath;this.publicPath=publicPath;this.historicalPublicKeys=historicalPublicKeys;this.json=json;
    }
    public OfflineSigner(String privatePath,String publicPath,JsonText json) { this(privatePath,publicPath,"",json); }
    public Signed sign(Object claims) {
        var key=keys();String header=encode(json.encode(Map.of("alg","RS256","typ","JWT","kid",key.id())));
        String payload=header+"."+encode(json.encode(claims));
        try {
            var signer=Signature.getInstance("SHA256withRSA");signer.initSign(key.privateKey());signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            return new Signed(key.id(),payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        } catch(GeneralSecurityException error) { throw new IllegalStateException("Offline signature failed",error); }
    }
    public PublicKeyView publicKey() { var key=keys();return new PublicKeyView(key.id(),"RS256",Base64.getEncoder().encodeToString(key.publicKey().getEncoded())); }
    public PublicKeyView publicKeyFor(String keyId) {
        var current=publicKey();
        if(current.keyId().equals(keyId)) return current;
        return historical().get(keyId);
    }
    private synchronized Map<String,PublicKeyView> historical() {
        if(history!=null) return history;
        var loaded=new HashMap<String,PublicKeyView>();
        try {
            for(String entry:historicalPublicKeys.split(",")) {
                if(entry.isBlank()) continue;
                byte[] der=Files.readAllBytes(Path.of(entry.trim()));
                var key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
                if(!(key instanceof java.security.interfaces.RSAPublicKey rsa) || rsa.getModulus().bitLength()<2048
                    || rsa.getPublicExponent().compareTo(java.math.BigInteger.ONE)<=0 || !rsa.getPublicExponent().testBit(0))
                    throw new InvalidKeyException();
                String encoded=Base64.getEncoder().encodeToString(key.getEncoded());
                String id="offline-"+DeviceCrypto.sha(encoded).substring(0,32);
                if(loaded.putIfAbsent(id,new PublicKeyView(id,"RS256",encoded))!=null) throw new InvalidKeyException();
            }
        } catch(java.io.IOException | GeneralSecurityException | RuntimeException error) {
            throw new BusinessException(503,"SIGNING_KEY_UNAVAILABLE","历史签名公钥配置不可用");
        }
        history=Map.copyOf(loaded);return history;
    }
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
