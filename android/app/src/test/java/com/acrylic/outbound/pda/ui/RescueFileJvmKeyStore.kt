package com.acrylic.outbound.pda.ui

import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import java.util.*
import javax.crypto.KeyGenerator

/** JVM JCA boundary only. Real AES/GCM vault and signatures run with process-local synthetic keys. */
class RescueFileJvmKeyStore : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
    override fun engineContainsAlias(alias: String) = alias in keys
    override fun engineIsKeyEntry(alias: String) = alias in keys
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys)
    override fun engineSize() = keys.size
    override fun engineGetCreationDate(alias: String): Date? = if (alias in keys) Date(0) else null
    override fun engineIsCertificateEntry(alias: String) = false
    override fun engineGetCertificate(alias: String): Certificate? = if (alias == DEVICE) certificate() else null
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = engineGetCertificate(alias)?.let { arrayOf(it) }
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    override fun engineStore(stream: OutputStream?, password: CharArray?) = error("No synthetic key export")
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) = error("Read-only")
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = error("Read-only")
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) = error("Read-only")
    override fun engineDeleteEntry(alias: String) = error("Read-only")

    private fun certificate() = object : Certificate("X.509") {
        override fun getEncoded(): ByteArray = device.public.encoded
        override fun getPublicKey(): PublicKey = device.public
        override fun verify(key: PublicKey) = error("Synthetic certificate is not a trust root")
        override fun verify(key: PublicKey, sigProvider: String) = error("Synthetic certificate is not a trust root")
        override fun toString() = "Synthetic JVM device public key"
    }

    companion object {
        private const val DEVICE = "outbound-device-v1"
        private lateinit var device: KeyPair
        private val keys = mutableMapOf<String, Key>()

        fun install(): Provider {
            check(runCatching { KeyStore.getInstance("AndroidKeyStore") }.isFailure) { "Never replace an existing device provider" }
            device = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            keys.clear(); keys[DEVICE] = device.private
            for (alias in listOf("outbound-session-v1", "outbound-supervisor-rescue-v1", "outbound-readonly-rescue-v1"))
                keys[alias] = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val provider = object : Provider("RescueFileJvmOnly", 1.0, "Synthetic process-local rescue keys") {}
            provider.put("KeyStore.AndroidKeyStore", RescueFileJvmKeyStore::class.java.name)
            check(Security.addProvider(provider) > 0)
            return provider
        }

        fun remove(provider: Provider) { Security.removeProvider(provider.name); keys.clear() }
    }
}
