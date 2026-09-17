package com.acrylic.outbound.pda.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class DeviceKeys {
    private val alias = "outbound-device-v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun publicKey(): String {
        if (!store().containsAlias(alias)) create()
        return Base64.getEncoder().encodeToString(store().getCertificate(alias).publicKey.encoded)
    }

    fun sign(text: String): String {
        val key = store().getKey(alias, null) ?: error("设备私钥丢失，保留数据并联系管理员，不能冒用旧设备身份")
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(key as java.security.PrivateKey)
        signer.update(text.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun create() {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
    }
}
