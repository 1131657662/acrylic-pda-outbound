package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RescueRequestStoreTest {
    @Test fun lostResponseReopenUsesOriginalKeyTextSignatureAndEpoch() {
        val f = RescueRequestFixture(); val original = f.store().prepare(f.intent())
        val repeated = f.store().prepare(f.intent())
        assertEquals(original, repeated)
        assertEquals(original, f.store().get(original.key))
        val receipt = f.receipt(original); val received = f.store().receipt(original.key, receipt)
        assertEquals(original.body.toString(), received.body.toString())
        assertEquals(received, f.store().receipt(original.key, JSONObject(receipt.toString())))
        assertEquals(listOf(received), f.store().list(f.identity))
        assertFalse(checkNotNull(f.storage.bytes).contains("accessToken"))
    }
    @Test fun pendingDifferentRecipientOrReasonCannotReplaceUnknownOriginal() {
        val f = RescueRequestFixture(); val original = f.store().prepare(f.intent())
        val changed = f.intent(RescueRequestInput(f.identity, f.target, f.rescuer, "changed reason"))
        assertThrows(IllegalStateException::class.java) { f.store().prepare(changed) }
        assertEquals(original, f.store().get(original.key))
    }
    @Test fun failedIntentSaveLeavesNoSendableReplacement() {
        val f = RescueRequestFixture(); f.storage.failWrite = true
        assertThrows(IllegalStateException::class.java) { f.store().prepare(f.intent()) }
        assertTrue(f.store().list(f.identity).isEmpty())
    }
    @Test fun differentPersonWarehouseOrDeviceCannotListOriginalRequest() {
        val f = RescueRequestFixture(); f.store().prepare(f.intent())
        for (other in listOf(f.identity.copy(operatorId = f.rescuer), f.identity.copy(deviceId = f.rescuer), f.identity.copy(warehouseId = f.rescuer)))
            assertTrue(f.store().list(other).isEmpty())
    }
    @Test fun wrongReceiptDigestAndReplacementIdPreserveOriginalReceipt() {
        val f = RescueRequestFixture(); val original = f.store().prepare(f.intent()); val receipt = f.receipt(original)
        assertThrows(IllegalStateException::class.java) { f.store().receipt(original.key, JSONObject(receipt.toString()).put("requestSha256", "a".repeat(64))) }
        assertNull(f.store().get(original.key).receipt)
        val received = f.store().receipt(original.key, receipt)
        assertThrows(IllegalStateException::class.java) { f.store().receipt(original.key, f.receipt(original)) }
        assertEquals(received, f.store().get(original.key))
    }
    @Test fun materialMustRetainOriginalTextSignatureScopeAndReceipt() {
        val f = RescueRequestFixture(); val original = f.store().prepare(f.intent())
        val received = f.store().receipt(original.key, f.receipt(original)); val material = f.material(received)
        for (field in listOf("originalText", "deviceSignature")) {
            assertThrows(IllegalStateException::class.java) { f.store().material(original.key, JSONObject(material.toString()).put(field, "changed")) }
        }
        assertNull(f.store().get(original.key).material)
        val saved = f.store().material(original.key, material)
        assertEquals(saved, f.store().get(original.key))
        assertEquals(original.body.toString(), saved.body.toString())
    }
    @Test fun savedRevocationAndGrantCannotDisappearOnLaterRead() {
        val f = RescueRequestFixture(); val original = f.store().prepare(f.intent())
        val received = f.store().receipt(original.key, f.receipt(original))
        val revoked = f.material(received).put("status", "REVOKED")
            .put("authorization", JSONObject().put("signedAuthorization", "synthetic immutable token"))
            .put("revocation", JSONObject().put("signedRevocation", "synthetic immutable revocation"))
        val saved = f.store().material(original.key, revoked)
        assertThrows(IllegalStateException::class.java) { f.store().material(original.key, f.material(received)) }
        assertEquals(saved, f.store().get(original.key))
    }
}
