package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescueSupervisorStoreTest {
    @Test fun provisionedGrantSurvivesStoreReopenWithoutPasswordOrLoginSecrets() {
        val f = RescueSupervisorFixture(); f.store().provision(f.provision(f.token(f.claims())), f.password)
        val store = f.store(); val choice = store.choices().single()
        val grant = store.unlock(choice.id.removePrefix("supervisor:"), f.password); grant.check(f.selected())
        assertEquals(f.operator, choice.identity.operatorId)
        assertFalse(checkNotNull(f.storage.bytes).contains(String(f.password)))
        assertFalse(checkNotNull(f.storage.bytes).contains("accessToken"))
        for (field in listOf("source", "localOrderId", "operatorId", "warehouseId", "deviceId"))
            assertThrows(IllegalStateException::class.java) { grant.check(f.selected().put(field, "another")) }
    }
    @Test fun anotherAuthenticatedActorCannotProvisionTheSignedScope() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims())
        for (field in listOf("userId", "deviceId", "warehouseId"))
            assertThrows(IllegalStateException::class.java) { f.store().provision(f.provision(token, f.actor().put(field, f.operator)), f.password) }
        assertNull(f.storage.bytes)
    }
    @Test fun signedRevocationInvalidatesUnlockedAccessAcrossReopenAndCannotBeReimportedAsAuthority() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims()); val store = f.store()
        store.provision(f.provision(token), f.password); val grant = store.unlock(f.grantId, f.password)
        val revoked = f.token(f.revocation(token)); assertEquals(f.grantId, store.importRevocation(revoked))
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        assertThrows(IllegalStateException::class.java) { f.store().unlock(f.grantId, f.password) }
        assertThrows(IllegalStateException::class.java) { f.store().provision(f.provision(token), f.password) }
        assertTrue(f.store().choices().isEmpty()); assertEquals(f.grantId, f.store().importRevocation(revoked))
        val saved = f.storage.read()!!.getJSONObject("entries").getJSONObject(f.grantId)
        assertEquals(token, saved.getString("authorization")); assertEquals(revoked, saved.getString("revocation"))
    }
    @Test fun localRevocationIsTerminalAcrossReopen() {
        val f = RescueSupervisorFixture(); val token = f.token(f.claims()); val store = f.store()
        store.provision(f.provision(token), f.password); val grant = store.unlock(f.grantId, f.password)
        store.revokeLocal(f.grantId)
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        assertThrows(IllegalStateException::class.java) { f.store().provision(f.provision(token), f.password) }
    }
    @Test fun finiteAuthorizationRejectsRebootDriftExpiryButPermanentReadStillWorks() {
        val f = RescueSupervisorFixture(); val store = f.store()
        store.provision(f.provision(f.token(f.claims(f.now.plusSeconds(60)))), f.password)
        val grant = store.unlock(f.grantId, f.password); val original = f.sample
        for (changed in listOf(original.copy(boot = 2), original.copy(wall = original.wall + 120_001),
            original.copy(elapsed = original.elapsed + 60_000, wall = original.wall + 60_000))) {
            f.sample = changed; assertThrows(IllegalStateException::class.java) { grant.recheck() }
        }
        val permanent = RescueSupervisorFixture(); permanent.store().provision(permanent.provision(permanent.token(permanent.claims())), permanent.password)
        permanent.sample = permanent.sample.copy(boot = -1)
        permanent.store().unlock(permanent.grantId, permanent.password).check(permanent.selected())
    }
    @Test fun wrongPasswordPenaltyPersistsAcrossReopenAndReboot() {
        val f = RescueSupervisorFixture(); val store = f.store(); store.provision(f.provision(f.token(f.claims())), f.password)
        repeat(5) { assertThrows(IllegalStateException::class.java) { f.store().unlock(f.grantId, "wrong-synthetic-password".toCharArray()) } }
        assertThrows(IllegalStateException::class.java) { store.unlock(f.grantId, f.password) }
        f.sample = f.sample.copy(elapsed = 10, boot = 2)
        assertThrows(IllegalStateException::class.java) { f.store().unlock(f.grantId, f.password) }
        f.sample = f.sample.copy(elapsed = 900_010)
        f.store().unlock(f.grantId, f.password).recheck()
    }
    @Test fun wrongDeviceKeyNeverReadsPersistedAuthority() {
        val f = RescueSupervisorFixture(); val store = f.store(); store.provision(f.provision(f.token(f.claims())), f.password)
        val grant = store.unlock(f.grantId, f.password); f.binding = f.device to "b".repeat(64)
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        assertThrows(IllegalStateException::class.java) { f.store().unlock(f.grantId, f.password) }
    }
    @Test fun failedRevocationWriteReportsFailureAndBlocksAlreadyObservedGrantInMemory() {
        val f = RescueSupervisorFixture(); val store = f.store(); val token = f.token(f.claims())
        store.provision(f.provision(token), f.password); val grant = store.unlock(f.grantId, f.password)
        f.storage.failWrite = true
        assertThrows(IllegalStateException::class.java) { store.importRevocation(f.token(f.revocation(token))) }
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        assertFalse(f.storage.read()!!.getJSONObject("entries").getJSONObject(f.grantId).has("revocation"))
    }
    @Test fun unknownOrAlteredRevocationDoesNotDamageAValidGrant() {
        val f = RescueSupervisorFixture(); val store = f.store(); val token = f.token(f.claims())
        store.provision(f.provision(token), f.password); val grant = store.unlock(f.grantId, f.password)
        assertThrows(IllegalStateException::class.java) { store.importRevocation(f.token(f.revocation(token).put("authorizationSha256", "b".repeat(64)))) }
        grant.check(f.selected())
    }
}
