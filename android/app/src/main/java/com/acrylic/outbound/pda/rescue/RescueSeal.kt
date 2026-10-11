package com.acrylic.outbound.pda.rescue

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object RescueSeal {
    fun sign(text: String, signer: RescueSigner): ByteArray {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(signer.publicKey)) as ECPublicKey
        val curve = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        check(key.params.order == curve.order && key.params.curve == curve.curve && key.params.generator == curve.generator)
        val signature = Base64.getDecoder().decode(signer.sign(text))
        check(signature.size in 64..80)
        check(Signature.getInstance("SHA256withECDSA").run {
            initVerify(key); update(text.toByteArray(Charsets.UTF_8)); verify(signature)
        }) { "应急包设备签名回验失败，请保留原资料" }
        return signature
    }
}
