package com.acrylic.outbound.pda.rescue

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** External copies are encrypted; the password is never part of the archive. */
object RescueEnvelope {
    private val magic = "PDARESCUE1\n".toByteArray(Charsets.US_ASCII)
    const val MAX_BYTES = 64 * 1024 * 1024
    private const val ITERATIONS = 600_000

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size in 1..MAX_BYTES) { "应急包超过单包上限" }
        val random = SecureRandom()
        val header = magic + ByteArray(16).also(random::nextBytes) + ByteArray(12).also(random::nextBytes)
        return header + crypt(Cipher.ENCRYPT_MODE, plain, password, header)
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        require(encrypted.size in magic.size + 28 + 17..MAX_BYTES + magic.size + 44)
        require(encrypted.copyOfRange(0, magic.size).contentEquals(magic)) { "未知应急包格式" }
        val headerSize = magic.size + 28
        return crypt(Cipher.DECRYPT_MODE, encrypted.copyOfRange(headerSize, encrypted.size),
            password, encrypted.copyOfRange(0, headerSize))
    }

    private fun crypt(mode: Int, input: ByteArray, password: CharArray, header: ByteArray): ByteArray {
        require(password.size in 16..128 && password.all { it.code in 33..126 }) { "请使用16至128位无空格ASCII应急包口令" }
        val salt = header.copyOfRange(magic.size, magic.size + 16)
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(magic.size + 16, header.size)))
            cipher.updateAAD(header)
            cipher.doFinal(input)
        } finally { key.fill(0) }
    }
}
