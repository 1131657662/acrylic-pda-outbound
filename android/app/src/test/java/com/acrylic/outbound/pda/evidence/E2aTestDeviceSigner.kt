package com.acrylic.outbound.pda.evidence

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Only replaces the Android Keystore boundary in JVM tests. */
class E2aTestDeviceSigner {
    private val key = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    var calls = 0
    var unavailable = false
    var beforeSign: () -> Unit = {}
    fun publicKey(): String = Base64.getEncoder().encodeToString(key.public.encoded)

    fun sign(stop: LocalDraftStop): LocalDraftStopProof.Signed {
        beforeSign(); check(!unavailable) { "test missing original key" }; calls++
        return LocalDraftStopProof.sign(stop, publicKey()) { text ->
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(key.private); signature.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signature.sign())
        }
    }

    fun sign(facts: E2aStopProof.Facts): E2aStopProof.Signed {
        beforeSign()
        check(!unavailable) { "test missing original key" }
        calls++
        val publicKey = Base64.getEncoder().encodeToString(key.public.encoded)
        return E2aStopProof.Signed(publicKey, E2aStopProof.sign(facts, publicKey) { text ->
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(key.private)
            signature.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signature.sign())
        })
    }
}
