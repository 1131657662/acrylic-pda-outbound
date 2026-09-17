package com.acrylic.m71demo.domain

import com.acrylic.m71demo.data.DemoAuthorization
import javax.inject.Inject

class AuthorizationService @Inject constructor(private val clock: AppClock, private val signer: DemoSigner) {
    // Cache only one immutable, fully verified token for this process. Never cache its time validity.
    @Volatile private var verified: DemoAuthorization? = null

    fun issue(deviceId: String): DemoAuthorization {
        val durationMs = 48L * 60 * 60 * 1000
        val auth = DemoAuthorization(java.util.UUID.randomUUID().toString(), deviceId,
            clock.wallMs(), clock.wallMs() + durationMs, clock.bootId(),
            clock.elapsedNs() + durationMs * 1_000_000, "")
        return auth.copy(signature = signer.sign(claim(auth)))
    }

    fun error(auth: DemoAuthorization?): String? {
        if (auth == null || !validSignature(auth)) return "AUTH_INVALID"
        if (auth.bootSessionId != clock.bootId()) return "AUTH_REBOOT_REVIEW"
        if (clock.elapsedNs() > auth.elapsedExpiresNs) return "AUTH_EXPIRED"
        if (clock.wallMs() < auth.issuedAt - 60_000 || clock.wallMs() > auth.expiresAt) return "AUTH_CLOCK_CHANGED"
        return null
    }

    private fun validSignature(auth: DemoAuthorization): Boolean {
        if (verified == auth) return true
        if (signer.sign(claim(auth)) != auth.signature) return false
        verified = auth
        return true
    }

    private fun claim(a: DemoAuthorization) = listOf(a.id, a.deviceId, a.issuedAt, a.expiresAt,
        a.bootSessionId, a.elapsedExpiresNs).joinToString("|")
}
