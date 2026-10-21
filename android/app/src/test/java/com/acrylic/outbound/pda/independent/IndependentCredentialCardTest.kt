package com.acrylic.outbound.pda.independent

import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentCredentialCardTest {
    private val actor = JSONObject().put("userId", "synthetic-person").put("deviceId", "synthetic-device")
        .put("warehouseId", "synthetic-warehouse").put("localSessionId", "synthetic-session").put("serverRecoveryEpoch", "1")
        .put("userAuthVersion", "1").put("deviceVersion", "1").put("deviceKeySha256", "a".repeat(64))
    private fun generated(): ByteArray = IndependentSetupDraft.create(actor, actor.getString("deviceKeySha256")).use { draft ->
        val output = ByteArrayOutputStream(); draft.write({ output }, { output.toByteArray().inputStream() }); output.toByteArray()
    }
    @Test fun originalGeneratedCardReturnsSecretForOriginalBindingOnly() {
        val bytes = generated(); val password = JSONObject(String(bytes, Charsets.UTF_8)).getString("credential")
        IndependentCredentialCard.read(bytes.inputStream()).use { card ->
            assertArrayEquals(password.toCharArray(), card.credential(actor))
            for (field in listOf("userId", "deviceId", "warehouseId", "deviceKeySha256")) {
                assertThrows(IllegalStateException::class.java) { card.credential(JSONObject(actor.toString()).put(field, "other")) }
            }
        }
    }
    @Test fun duplicateFieldsTrailingBodyNonStringsAndTruncationAreRejected() {
        val original = String(generated(), Charsets.UTF_8)
        val duplicate = original.replaceFirst("{", "{\"credential\":\"" + "x".repeat(32) + "\",")
        for (bad in listOf(duplicate, original+" {}", JSONObject(original).put("credential", 1234).toString(), original.dropLast(1)))
            assertThrows(IllegalStateException::class.java) { IndependentCredentialCard.read(bad.byteInputStream()) }
    }
    @Test fun wrongPurposeExtraAuthorityAndInvalidSecretAreRejected() {
        val original = String(generated(), Charsets.UTF_8)
        for ((key, value) in listOf("format" to "acrylic-own-readonly-rescue-card-v1", "purpose" to "write",
            "credential" to "short", "deviceKeySha256" to "invalid", "operatorId" to "", "trustedKey" to "self-asserted")) {
            assertThrows(IllegalStateException::class.java) {
                IndependentCredentialCard.read(JSONObject(original).put(key, value).toString().byteInputStream())
            }
        }
    }
    @Test fun malformedUtf8OversizeAndProviderFailureCloseWithoutLeaking() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(4097), byteArrayOf())) {
            var closed = false
            val input = object : java.io.ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            assertThrows(IllegalStateException::class.java) { IndependentCredentialCard.read(input) }; assertTrue(closed)
        }
        var closed = false
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("synthetic sensitive provider error")
            override fun close() { closed = true }
        }
        val error = assertThrows(IllegalStateException::class.java) { IndependentCredentialCard.read(input) }
        assertTrue(closed); assertFalse(error.message.orEmpty().contains("sensitive"))
    }
    @Test fun closedCardCannotReturnSecret() {
        val card = IndependentCredentialCard.read(generated().inputStream()); card.close()
        assertThrows(IllegalStateException::class.java) { card.credential(actor) }
    }
}
