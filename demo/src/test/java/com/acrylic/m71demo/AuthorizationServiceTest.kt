package com.acrylic.m71demo

import com.acrylic.m71demo.domain.AuthorizationService
import com.acrylic.m71demo.domain.DemoSigner
import org.junit.Assert.*
import org.junit.Test

class AuthorizationServiceTest {
    private val clock = FakeClock()
    private val signer = CountingSigner()
    private val service = AuthorizationService(clock, signer)
    private val auth = service.issue("device-A")

    @Test fun unchangedVerifiedTokenAvoidsRepeatedKeystoreWork() {
        assertNull(service.error(auth))
        val operations = signer.operations
        repeat(20) { assertNull(service.error(auth.copy())) }
        assertEquals(operations, signer.operations)
    }

    @Test fun changedClaimsCannotBorrowCachedSignatureVerification() {
        assertNull(service.error(auth))
        val changed = listOf(auth.copy(id = "another"), auth.copy(deviceId = "device-B"),
            auth.copy(issuedAt = auth.issuedAt + 1), auth.copy(expiresAt = auth.expiresAt + 1),
            auth.copy(bootSessionId = "another"), auth.copy(elapsedExpiresNs = auth.elapsedExpiresNs + 1),
            auth.copy(signature = "forged"))
        changed.forEach { assertEquals("AUTH_INVALID", service.error(it)) }
        assertNull(service.error(auth))
    }

    @Test fun cachedTokenStillExpiresByMonotonicClock() {
        assertNull(service.error(auth))
        clock.elapsed = auth.elapsedExpiresNs + 1
        assertEquals("AUTH_EXPIRED", service.error(auth))
    }

    @Test fun cachedTokenStillRequiresSameBoot() {
        assertNull(service.error(auth))
        clock.boot = "new-boot"
        assertEquals("AUTH_REBOOT_REVIEW", service.error(auth))
    }

    @Test fun cachedTokenStillChecksWallClockInBothDirections() {
        assertNull(service.error(auth))
        clock.wall = auth.issuedAt - 60_001
        assertEquals("AUTH_CLOCK_CHANGED", service.error(auth))
        clock.wall = auth.expiresAt + 1
        assertEquals("AUTH_CLOCK_CHANGED", service.error(auth))
    }

    @Test fun newServiceMustVerifyAgainAndInvalidTokenIsNeverTrusted() {
        assertNull(service.error(auth))
        val rejecting = AuthorizationService(clock, object : DemoSigner {
            override fun sign(claim: String) = "different-key"
        })
        repeat(2) { assertEquals("AUTH_INVALID", rejecting.error(auth)) }
        assertEquals("AUTH_INVALID", rejecting.error(null))
    }

    @Test fun replacingCachedTokenVerifiesThePreviousTokenAgain() {
        assertNull(service.error(auth))
        val other = service.issue("device-B")
        assertNull(service.error(other))
        val operations = signer.operations
        assertNull(service.error(auth))
        assertEquals(operations + 1, signer.operations)
    }

    private class CountingSigner : DemoSigner {
        var operations = 0
        override fun sign(claim: String): String {
            operations++
            return java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(claim.toByteArray()))
        }
    }
}
