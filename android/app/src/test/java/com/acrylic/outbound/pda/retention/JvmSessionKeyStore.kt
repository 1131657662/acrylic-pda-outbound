package com.acrylic.outbound.pda.retention

import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.util.*
import javax.crypto.KeyGenerator

/** JVM-only JCA boundary: real vault AES/GCM, synthetic process-local key, no device identity. */
class JvmSessionKeyStore : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = if (engineContainsAlias(alias)) key else null
    override fun engineContainsAlias(alias: String) = alias == ALIAS
    override fun engineIsKeyEntry(alias: String) = engineContainsAlias(alias)
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(listOf(ALIAS))
    override fun engineSize() = 1
    override fun engineGetCreationDate(alias: String): Date? = if (engineContainsAlias(alias)) Date(0) else null
    override fun engineIsCertificateEntry(alias: String) = false
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    override fun engineStore(stream: OutputStream?, password: CharArray?) = error("No key export in test provider")
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) = error("Read-only")
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = error("Read-only")
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) = error("Read-only")
    override fun engineDeleteEntry(alias: String) = error("Read-only")

    companion object {
        private const val ALIAS = "outbound-session-v1"
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        fun install(): Provider {
            check(Security.getProvider("AndroidKeyStore") == null) { "Do not replace a real device provider" }
            val provider = object : Provider("FormalRestoreJvmOnly", 1.0, "Synthetic session AES key only") {}
            provider.put("KeyStore.AndroidKeyStore", JvmSessionKeyStore::class.java.name)
            check(Security.addProvider(provider) > 0)
            return provider
        }
    }
}
