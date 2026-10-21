package com.acrylic.outbound.pda.rescue

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RescuePackageDraftTest {
    private fun order() = JSONObject().put("source", "FORMAL").put("localOrderId", "synthetic-order")
        .put("deviceId", "synthetic-device").put("operatorId", "synthetic-operator").put("warehouseId", "synthetic-warehouse")
    private fun saved(draft: RescuePackageDraft): ByteArray {
        val output = ByteArrayOutputStream(); draft.write({ output }, { output.toByteArray().inputStream() }); return output.toByteArray()
    }
    @Test fun savedSecretRequiresExplicitReadbackAndOnlyDecryptsTheExternalEnvelope() {
        RescuePackageDraft.create(order()) {}.use { draft ->
            assertThrows(IllegalStateException::class.java) { draft.credential() }
            val bytes = saved(draft); assertFalse(draft.verified)
            val material = JSONObject(bytes.toString(Charsets.UTF_8)); val secret = material.getString("packagePassword")
            assertTrue(secret.matches(Regex("[A-Za-z0-9_-]{32}"))); assertFalse(material.has("credential"))
            assertFalse(material.has("accessToken")); assertFalse(material.has("privateKey"))
            draft.verify(bytes.inputStream()); val password = draft.credential()
            try {
                val original = "synthetic-original-file".toByteArray()
                val encrypted = RescueEnvelope.encrypt(original, password)
                assertArrayEquals(original, RescueEnvelope.decrypt(encrypted, secret.toCharArray()))
                assertThrows(Exception::class.java) { RescueEnvelope.decrypt(encrypted, "synthetic-rescue-unlock-password".toCharArray()) }
            } finally { password.fill('\u0000') }
        }
    }
    @Test fun everyDraftHasIndependentSecretAndMaterialIdentifier() {
        RescuePackageDraft.create(order()) {}.use { first ->
            RescuePackageDraft.create(order()) {}.use { second ->
                val a = JSONObject(saved(first).toString(Charsets.UTF_8)); val b = JSONObject(saved(second).toString(Charsets.UTF_8))
                assertNotEquals(a.getString("materialId"), b.getString("materialId"))
                assertNotEquals(a.getString("packagePassword"), b.getString("packagePassword"))
                assertEquals(a.getString("orderSha256"), b.getString("orderSha256"))
            }
        }
    }
    @Test fun changedTruncatedOversizeAndReformattedMaterialCannotBeConfirmed() {
        RescuePackageDraft.create(order()) {}.use { draft ->
            val original = saved(draft); draft.verify(original.inputStream()); assertTrue(draft.verified)
            for (bad in listOf(original.copyOf(original.size - 1), original + byteArrayOf(32), ByteArray(4097),
                JSONObject(original.toString(Charsets.UTF_8)).put("warehouseId", "other").toString().toByteArray())) {
                assertThrows(IllegalStateException::class.java) { draft.verify(bad.inputStream()) }; assertFalse(draft.verified)
            }
            draft.verify(original.inputStream()); assertTrue(draft.verified)
        }
    }
    @Test fun failedWriteCannotReuseAPreviouslyVerifiedSecret() {
        RescuePackageDraft.create(order()) {}.use { draft ->
            val bytes = saved(draft); draft.verify(bytes.inputStream())
            assertThrows(IOException::class.java) { draft.write({ throw IOException("synthetic interrupted target") }, { bytes.inputStream() }) }
            assertFalse(draft.verified); assertThrows(IllegalStateException::class.java) { draft.verify(bytes.inputStream()) }
        }
    }
    @Test fun invalidatedOriginalScopePreventsCredentialAndFurtherWrites() {
        var valid = true
        RescuePackageDraft.create(order()) { check(valid) }.use { draft ->
            val original = saved(draft); draft.verify(original.inputStream()); valid = false
            assertThrows(IllegalStateException::class.java) { draft.credential() }
            assertThrows(IllegalStateException::class.java) { saved(draft) }
        }
    }
    @Test fun closedDraftCannotBeRevivedWithItsPreviouslySavedMaterial() {
        val draft = RescuePackageDraft.create(order()) {}; val bytes = saved(draft); draft.verify(bytes.inputStream()); draft.close()
        assertThrows(IllegalStateException::class.java) { draft.credential() }
        assertThrows(IllegalStateException::class.java) { draft.verify(bytes.inputStream()) }
        assertArrayEquals(bytes, bytes.copyOf())
    }
}
