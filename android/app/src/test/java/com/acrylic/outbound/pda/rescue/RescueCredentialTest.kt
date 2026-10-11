package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescueCredentialTest {
    private val identity = RescueReadIdentity("synthetic-device", "synthetic-operator", "synthetic-warehouse")
    private val password get() = "synthetic-read-credential-only".toCharArray()
    private class Storage : RescueCredentialStorage {
        var text: String? = null
        override fun read(): JSONObject? = text?.let(::JSONObject)
        override fun write(state: JSONObject) { text = state.toString() }
    }
    private class Fixture {
        val storage = Storage()
        var time = TimeSample(1000, 1000, 1)
        var binding = "synthetic-device" to "synthetic-key-digest"
        fun store() = RescueCredentialStore(storage, { time }, { binding })
    }
    @Test fun ownGrantIsScopedAcrossReopenWithoutRetainingPasswordOrSessionToken() {
        val f = Fixture(); f.store().provision(identity, password)
        val grant = f.store().unlock(identity.operatorId, password)
        val order = JSONObject().put("deviceId", identity.deviceId).put("operatorId", identity.operatorId)
            .put("warehouseId", identity.warehouseId).put("localOrderId", "synthetic-order")
        grant.check(order)
        for (field in listOf("deviceId", "operatorId", "warehouseId")) {
            assertThrows(IllegalStateException::class.java) { grant.check(JSONObject(order.toString()).put(field, "another")) }
        }
        assertFalse(checkNotNull(f.storage.text).contains(String(password)))
        assertFalse(checkNotNull(f.storage.text).contains("accessToken"))
        assertEquals(listOf(identity), f.store().identities())
    }
    @Test fun repeatedWrongAttemptsPersistAndRebootCannotErasePenalty() {
        val f = Fixture(); f.store().provision(identity, password)
        repeat(5) { assertThrows(IllegalStateException::class.java) { f.store().unlock(identity.operatorId, "synthetic-wrong-password".toCharArray()) } }
        assertThrows(IllegalStateException::class.java) { f.store().unlock(identity.operatorId, password) }
        f.time = TimeSample(10, 900_000_000, 2)
        assertThrows(IllegalStateException::class.java) { f.store().unlock(identity.operatorId, password) }
        f.time = f.time.copy(elapsed = 900_009)
        assertThrows(IllegalStateException::class.java) { f.store().unlock(identity.operatorId, password) }
        f.time = f.time.copy(elapsed = 900_010)
        f.store().unlock(identity.operatorId, password).recheck()
        assertEquals(0, f.storage.read()!!.getJSONObject("entries").getJSONObject(identity.operatorId).getInt("failures"))
    }
    @Test fun revokedOrReplacedCredentialInvalidatesAlreadyUnlockedGrant() {
        val f = Fixture(); val store = f.store(); store.provision(identity, password)
        val grant = store.unlock(identity.operatorId, password)
        store.provision(identity, password)
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        val replacement = store.unlock(identity.operatorId, password)
        store.revoke(identity.operatorId)
        assertThrows(IllegalStateException::class.java) { replacement.recheck() }
        assertTrue(store.identities().isEmpty())
    }
    @Test fun deviceKeyChangeAndUnknownBootFailClosed() {
        val f = Fixture(); val store = f.store(); store.provision(identity, password)
        val grant = store.unlock(identity.operatorId, password)
        f.binding = "synthetic-device" to "changed-key"
        assertThrows(IllegalStateException::class.java) { grant.recheck() }
        f.binding = "synthetic-device" to "synthetic-key-digest"
        repeat(5) { assertThrows(IllegalStateException::class.java) { store.unlock(identity.operatorId, "synthetic-wrong-password".toCharArray()) } }
        f.time = TimeSample(1_000_000, 1_000_000, -1)
        assertThrows(IllegalStateException::class.java) { store.unlock(identity.operatorId, password) }
    }
}
