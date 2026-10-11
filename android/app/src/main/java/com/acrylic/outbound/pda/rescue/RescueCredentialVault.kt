package com.acrylic.outbound.pda.rescue

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate from the login vault: logout preserves only the scoped, read-only rescue verifier. */
internal class RescueCredentialVault(context: Context) : RescueCredentialStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "rescue-credentials.enc"))
    private val alias = "outbound-readonly-rescue-v1"
    override fun read(): JSONObject? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.readFully(); check(bytes.size in 29..128 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
    }
    override fun write(state: JSONObject) {
        val plain = state.toString().toByteArray(Charsets.UTF_8); check(plain.size <= 120 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val bytes = cipher.iv + cipher.doFinal(plain)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        check(file.readFully().contentEquals(bytes)) { "救援凭据未完整保存" }
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            check(create && !file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) { "救援核验密钥不可用，请保留原资料" }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
        return store.getKey(alias, null) as SecretKey
    }
}
