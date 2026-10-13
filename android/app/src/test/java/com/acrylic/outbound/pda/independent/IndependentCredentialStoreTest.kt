package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class IndependentCredentialStoreTest {
    @Test fun lostResponseReusesDurableRequestAcrossNewStore() {
        val f = IndependentCredentialFixture(); val original = f.store.prepare(f.context())
        val retried = f.store.prepare(f.context())
        assertEquals(original.key, retried.key); assertEquals(original.body.toString(), retried.body.toString())
        assertFalse(f.storage.bytes!!.contains("bearer")); assertFalse(f.storage.bytes!!.contains("refreshToken"))
    }
    @Test fun credentialUnlockSurvivesNewStoreWithoutExtendingOriginalDeadline() {
        val f = IndependentCredentialFixture(); f.provision(); val open = f.store.unlock(f.password, f.context())
        f.advance(47*3600*1000L)
        f.session.put("timeAnchor", TrustedTime.anchor(f.issued.plusSeconds(47*3600).toString(), f.sample))
        assertEquals(f.issued.plusSeconds(48*3600), f.store.validate(open, f.context()).expires)
        f.advance(3600*1000L)
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
    }
    @Test fun sessionGenerationIdentityVersionEpochAndKeyChangesInvalidateOpenedGrant() {
        val f = IndependentCredentialFixture(); f.provision(); val open = f.store.unlock(f.password, f.context())
        val original = f.session.toString()
        listOf("userId", "deviceId", "warehouseId", "localSessionId", "userAuthVersion", "deviceVersion", "serverRecoveryEpoch").forEach {
            f.session = JSONObject(original).put(it, "changed")
            assertTrue(it, runCatching { f.store.validate(open, f.context()) }.isFailure)
        }
        f.session = JSONObject(original); f.binding = f.device to "b".repeat(64)
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
        f.binding = f.device to "a".repeat(64); f.session.put("blockedReason", "permission revoked")
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
    }
    @Test fun rebootClockShiftAndLocalRevocationPreserveOriginalEvidence() {
        val f = IndependentCredentialFixture(); f.provision(); val open = f.store.unlock(f.password, f.context())
        val sample = f.sample; val bytes = f.storage.bytes
        f.sample = sample.copy(boot = sample.boot + 1)
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
        f.sample = sample.copy(wall = sample.wall - 120001)
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
        assertEquals(bytes, f.storage.bytes)
        f.sample = sample; f.store.revoke()
        assertTrue(runCatching { f.store.validate(open, f.context()) }.isFailure)
        val old = JSONObject(bytes); val active = old.getString("active")
        assertEquals(old.getJSONObject("entries").getJSONObject(active).getString("receipt"),
            JSONObject(f.storage.bytes).getJSONObject("entries").getJSONObject(active).getString("receipt"))
    }
    @Test fun wrongPasswordPenaltySurvivesNewStoreAndReboot() {
        val f = IndependentCredentialFixture(); f.provision()
        repeat(5) { assertTrue(runCatching { f.store.unlock("synthetic-wrong-independent-secret".toCharArray(), f.context()) }.isFailure) }
        assertTrue(runCatching { f.store.unlock(f.password, f.context()) }.isFailure)
        f.sample = f.sample.copy(boot = f.sample.boot + 1, elapsed = 10)
        assertTrue(runCatching { f.store.unlock(f.password, f.context()) }.isFailure)
        f.advance(15*60*1000L); f.session.put("timeAnchor", TrustedTime.anchor(f.issued.toString(), f.sample))
        // Cooldown expires, but reboot must still invalidate the original collection time anchor.
        assertTrue(runCatching { f.store.unlock(f.password, f.context()) }.isFailure)
        assertEquals(0, JSONObject(f.storage.bytes).getJSONObject("entries").getJSONObject(JSONObject(f.storage.bytes).getString("active")).getInt("failures"))
    }
    @Test fun diskFailureDoesNotReplaceActiveCredentialOrLosePendingKey() {
        val f = IndependentCredentialFixture(); f.provision(); val old = f.storage.bytes
        val request = f.store.prepare(f.context()); val pending = f.storage.bytes
        f.storage.failWrite = true
        assertTrue(runCatching { f.store.receive(IndependentCredentialIncoming(request, f.receipt(), f.context()), f.password) }.isFailure)
        assertEquals(pending, f.storage.bytes)
        f.storage.failWrite = false; assertEquals(request.key, f.store.prepare(f.context()).key)
        assertEquals(JSONObject(old).getString("active"), JSONObject(f.storage.bytes).getString("active"))
    }
    @Test fun lateOriginalRetryCannotRevertNewActivePointer() {
        val f = IndependentCredentialFixture(); val first = f.store.prepare(f.context()); val receipt = f.receipt()
        f.store.receive(IndependentCredentialIncoming(first, receipt, f.context()), f.password)
        val second = f.provision(); val open = f.store.unlock(f.password, f.context())
        f.store.receive(IndependentCredentialIncoming(first, receipt, f.context()), f.password)
        assertEquals(second.key, open.requestKey); assertEquals(second.key, JSONObject(f.storage.bytes).getString("active"))
        f.store.validate(open, f.context())
    }
}
