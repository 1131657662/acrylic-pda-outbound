package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Explicit trusted historical public keys; no signing material or keys obtained from evidence. */
public final class RecoveryReceiptKeys {
    private final Map<String, PublicKey> keys;

    public RecoveryReceiptKeys(List<byte[]> independentPublicDer) {
        require(independentPublicDer != null && !independentPublicDer.isEmpty() && independentPublicDer.size() <= 64,
            "RECOVERY_RECEIPT_KEYS_REQUIRED");
        var parsed = new HashMap<String, PublicKey>();
        for (byte[] bytes : independentPublicDer) {
            var key = parse(bytes);
            String id = "offline-" + DeviceCrypto.sha(Base64.getEncoder().encodeToString(key.getEncoded())).substring(0, 32);
            require(parsed.putIfAbsent(id, key) == null, "RECOVERY_RECEIPT_KEY_DUPLICATE");
        }
        keys = Map.copyOf(parsed);
    }

    PublicKey requireKey(String id) {
        var key = keys.get(id);
        require(key != null, "RECOVERY_RECEIPT_KEY_UNKNOWN");
        return key;
    }

    private PublicKey parse(byte[] bytes) {
        require(bytes != null && bytes.length > 0 && bytes.length <= 16384, "RECOVERY_RECEIPT_KEY_INVALID");
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes.clone()));
            require(key instanceof RSAPublicKey, "RECOVERY_RECEIPT_KEY_INVALID");
            var rsa = (RSAPublicKey) key;
            require(rsa.getModulus().bitLength() >= 2048 && rsa.getModulus().bitLength() <= 8192
                && rsa.getPublicExponent().compareTo(java.math.BigInteger.ONE) > 0 && rsa.getPublicExponent().testBit(0)
                && Arrays.equals(bytes, key.getEncoded()), "RECOVERY_RECEIPT_KEY_INVALID");
            return key;
        } catch (GeneralSecurityException invalid) { throw new RecoveryControlException("RECOVERY_RECEIPT_KEY_INVALID", invalid); }
    }
}
