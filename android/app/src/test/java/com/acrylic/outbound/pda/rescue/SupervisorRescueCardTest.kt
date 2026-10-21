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
class SupervisorRescueCardTest {
    private val authority = RescueSupervisorFixture()
    private val token = authority.token(authority.claims())
    private fun original() = RescueDelegation.verify(token, authority.trusted, authority.binding)
    private fun generated(): ByteArray {
        val actor = authority.actor().put("signingKey", authority.trusted)
            .put("serverRecoveryEpoch", "1").put("localSessionId", "synthetic-card-consumer")
        return SupervisorRescueSetupDraft.create(actor, token, authority.binding).use { draft ->
            val out = ByteArrayOutputStream(); draft.write({ out }, { out.toByteArray().inputStream() }); out.toByteArray()
        }
    }
    @Test fun originalGeneratedCardSuppliesPasswordOnlyForExactSignedAuthorization() {
        val bytes = generated(); val password = JSONObject(bytes.toString(Charsets.UTF_8)).getString("credential")
        SupervisorRescueCard.read(bytes.inputStream()).use { card ->
            assertArrayEquals(password.toCharArray(), card.credential(original()))
            val otherToken = authority.token(authority.claims())
            assertThrows(IllegalStateException::class.java) {
                card.credential(RescueDelegation.verify(otherToken, authority.trusted, authority.binding))
            }
        }
    }
    @Test fun changedOriginalGrantRescuerIdentityOrKeyCannotSupplyCredential() {
        val bytes = generated().toString(Charsets.UTF_8)
        for (field in listOf("grantId", "rescuerId", "deviceId", "operatorId", "warehouseId",
            "deviceKeySha256", "authorizationSha256")) {
            val bad = JSONObject(bytes).put(field, if (field.endsWith("Sha256")) "b".repeat(64) else "different")
            SupervisorRescueCard.read(bad.toString().byteInputStream()).use { card ->
                assertThrows(IllegalStateException::class.java) { card.credential(original()) }
            }
        }
    }
    @Test fun duplicateTrailingMalformedOrNonStringClaimsAreRejected() {
        val original = generated().toString(Charsets.UTF_8)
        val duplicate = original.replaceFirst("{", "{\"credential\":\"" + "x".repeat(32) + "\",")
        for (bad in listOf(duplicate, original + " {}", original.dropLast(1), "[" + original + "]",
            JSONObject(original).put("credential", 123456789).toString()))
            assertThrows(IllegalStateException::class.java) { SupervisorRescueCard.read(bad.byteInputStream()) }
    }
    @Test fun unknownPurposeClaimsAndBadDigestOrPasswordShapeAreRejected() {
        val original = generated().toString(Charsets.UTF_8)
        for ((field, value) in listOf("format" to "own-card", "purpose" to "write", "credential" to "short",
            "authorizationSha256" to "invalid", "rescuerId" to "", "signingKey" to "self-issued"))
            assertThrows(IllegalStateException::class.java) {
                SupervisorRescueCard.read(JSONObject(original).put(field, value).toString().byteInputStream())
            }
    }
    @Test fun malformedUtf8OversizeAndProviderFailureCloseInputWithoutLeakingData() {
        for (bytes in listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(4097), byteArrayOf())) {
            var closed = false
            val input = object : java.io.ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            assertThrows(IllegalStateException::class.java) { SupervisorRescueCard.read(input) }; assertTrue(closed)
        }
        var closed = false
        val input = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("sensitive-synthetic-provider")
            override fun close() { closed = true }
        }
        val error = assertThrows(IllegalStateException::class.java) { SupervisorRescueCard.read(input) }
        assertTrue(closed); assertFalse(error.message.orEmpty().contains("sensitive"))
    }
    @Test fun closedCardCannotSupplyCredential() {
        val card = SupervisorRescueCard.read(generated().inputStream()); card.close()
        assertThrows(IllegalStateException::class.java) { card.credential(original()) }
    }
}
