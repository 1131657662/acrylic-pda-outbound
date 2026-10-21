package com.acrylic.outbound.pda.rescue

import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class OwnRescueCardTest {
    private val identity = RescueReadIdentity("synthetic-device", "synthetic-person", "synthetic-warehouse")
    private val digest = "a".repeat(64)
    private fun generated(): ByteArray {
        val actor = JSONObject().put("deviceId", identity.deviceId).put("userId", identity.operatorId)
            .put("warehouseId", identity.warehouseId).put("serverRecoveryEpoch", "1").put("localSessionId", "synthetic-session")
        return OwnRescueSetupDraft.create(actor, digest).use { draft ->
            val output = ByteArrayOutputStream(); draft.write({ output }, { output.toByteArray().inputStream() })
            output.toByteArray()
        }
    }
    @Test fun generatedOriginalCardReturnsSecretOnlyForOriginalScope() {
        val bytes = generated(); val password = JSONObject(String(bytes, Charsets.UTF_8)).getString("credential")
        OwnRescueCard.read(bytes.inputStream()).use { card ->
            assertArrayEquals(password.toCharArray(), card.credential(identity, digest))
            for (other in listOf(identity.copy(deviceId = "other"), identity.copy(operatorId = "other"), identity.copy(warehouseId = "other")))
                assertThrows(IllegalStateException::class.java) { card.credential(other, digest) }
            assertThrows(IllegalStateException::class.java) { card.credential(identity, "b".repeat(64)) }
        }
    }
    @Test fun duplicateFieldsTrailingBodyAndNonStringValuesAreRejected() {
        val original = String(generated(), Charsets.UTF_8)
        val duplicate = original.replaceFirst("{", "{\"credential\":\"" + "x".repeat(32) + "\",")
        val numeric = JSONObject(original).put("credential", 123456789).toString()
        for (bad in listOf(duplicate, original + " {}", numeric, original.dropLast(1), "[" + original + "]"))
            assertThrows(IllegalStateException::class.java) { OwnRescueCard.read(bad.byteInputStream()) }
    }
    @Test fun unknownFormatExtraClaimsAndWrongPasswordShapeCannotCreateCard() {
        val original = String(generated(), Charsets.UTF_8)
        for ((key,value) in listOf("format" to "supervisor-card", "purpose" to "write", "credential" to "short",
            "deviceKeySha256" to "invalid", "operatorId" to "", "trustedKey" to "self-asserted")) {
            val bad = JSONObject(original).put(key, value).toString()
            assertThrows(IllegalStateException::class.java) { OwnRescueCard.read(bad.byteInputStream()) }
        }
    }
    @Test fun malformedUtf8OversizeAndReadFailureCloseInputWithoutExposingData() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(4097), byteArrayOf())) {
            var closed = false
            val input = object : java.io.ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            assertThrows(IllegalStateException::class.java) { OwnRescueCard.read(input) }; assertTrue(closed)
        }
        var closed = false
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("synthetic sensitive provider error")
            override fun close() { closed = true }
        }
        val error = assertThrows(IllegalStateException::class.java) { OwnRescueCard.read(input) }
        assertTrue(closed); assertFalse(error.message.orEmpty().contains("sensitive"))
    }
    @Test fun closedCardCannotReturnCredentialAgain() {
        val card = OwnRescueCard.read(generated().inputStream()); card.close()
        assertThrows(IllegalStateException::class.java) { card.credential(identity, digest) }
    }
}
