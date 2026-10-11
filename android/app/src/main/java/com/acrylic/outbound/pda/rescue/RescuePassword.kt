package com.acrylic.outbound.pda.rescue

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONObject

internal object RescuePassword {
    fun create(password: CharArray): JSONObject {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val digest = derive(password, salt)
        return try { JSONObject().put("salt", Base64.getEncoder().encodeToString(salt))
            .put("digest", Base64.getEncoder().encodeToString(digest)) } finally { digest.fill(0) }
    }
    fun matches(password: CharArray, stored: JSONObject): Boolean {
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(stored.getString("salt")); val expected = decoder.decode(stored.getString("digest"))
        check(salt.size == 16 && expected.size == 32)
        val actual = derive(password, salt)
        return try { MessageDigest.isEqual(expected, actual) } finally { actual.fill(0) }
    }
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 16..128 && password.all { it.code in 33..126 }) { "救援凭据须为16至128位无空格ASCII字符" }
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
