package com.acrylic.m71demo.device

import android.security.keystore.*
import com.acrylic.m71demo.domain.DemoSigner
import java.security.KeyStore
import javax.crypto.*
import javax.inject.Inject

class KeystoreSigner @Inject constructor() : DemoSigner {
    private val alias = "m71-demo-local-test-auth"

    override fun sign(claim: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        return android.util.Base64.encodeToString(mac.doFinal(claim.toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKey()
        }
    }
}
