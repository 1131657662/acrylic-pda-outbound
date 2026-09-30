package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.evidence.LocalDraftStopProof
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Ephemeral fixture key, never a real device/Keystore identity. Recovery has public material only. */
internal class R06A11StopSigner(existingPublicKey: String? = null) {
    private val pair = if (existingPublicKey == null) KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair() else null
    val publicKey = existingPublicKey ?: Base64.getEncoder().encodeToString(checkNotNull(pair).public.encoded)
    var calls = 0
        private set

    fun sign(stop: LocalDraftStop): LocalDraftStopProof.Signed {
        val key = checkNotNull(pair) { "Recovery must not re-sign; original private key is absent" }
        calls++
        return LocalDraftStopProof.sign(stop, publicKey) { text ->
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(key.private)
            signature.update(text.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signature.sign())
        }
    }
}
