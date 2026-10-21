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
class SupervisorRescueSetupDraftTest {
    private val authority = RescueSupervisorFixture()
    private fun actor() = authority.actor().put("signingKey", authority.trusted)
        .put("serverRecoveryEpoch", "1").put("localSessionId", "synthetic-generation")
        .put("userAuthVersion", "1").put("deviceVersion", "1")
    private fun create(material: String = authority.token(authority.claims()), session: JSONObject = actor()) =
        SupervisorRescueSetupDraft.create(session, material, authority.binding)
    private fun saved(draft: SupervisorRescueSetupDraft): ByteArray {
        val out = ByteArrayOutputStream(); draft.write({ out }, { out.toByteArray().inputStream() })
        return out.toByteArray()
    }
    @Test fun generatedCardBindsOriginalAuthorizationWithoutLoginOrSigningMaterial() {
        val token = authority.token(authority.claims())
        create(token).use { draft ->
            val bytes = saved(draft); val card = JSONObject(bytes.toString(Charsets.UTF_8))
            assertFalse(draft.verified)
            assertThrows(IllegalStateException::class.java) { draft.credential(actor(), authority.binding.second) }
            assertEquals("acrylic-supervisor-readonly-rescue-card-v1", card.getString("format"))
            assertEquals(authority.grantId, card.getString("grantId"))
            assertEquals(RescueBundle.sha(token.toByteArray(Charsets.UTF_8)), card.getString("authorizationSha256"))
            assertTrue(card.getString("credential").matches(Regex("[A-Za-z0-9_-]{32}")))
            for (secret in listOf(token, "synthetic-must-not-persist", "accessToken", "localSessionId", "privateKey"))
                assertFalse(card.toString().contains(secret))
            draft.verify(bytes.inputStream())
            assertArrayEquals(card.getString("credential").toCharArray(), draft.credential(actor(), authority.binding.second))
        }
    }
    @Test fun anotherActorDeviceOrAuthorityCannotGenerateUsableDraft() {
        val token = authority.token(authority.claims())
        for (field in listOf("userId", "deviceId", "warehouseId"))
            assertThrows(IllegalStateException::class.java) { create(token, actor().put(field, java.util.UUID.randomUUID().toString())) }
        val attacker = RescueSupervisorFixture()
        assertThrows(IllegalStateException::class.java) { create(attacker.token(authority.claims())) }
        assertThrows(IllegalStateException::class.java) { create(authority.token(authority.claims().put("deviceKeySha256", "b".repeat(64)))) }
    }
    @Test fun revocationCannotBeConvertedToNewLocalPassword() {
        val token = authority.token(authority.claims())
        val revoked = authority.token(authority.revocation(token))
        for (material in listOf(revoked, JSONObject().put("signedAuthorization", token).put("signedRevocation", revoked).toString()))
            assertThrows(IllegalStateException::class.java) { create(material) }
    }
    @Test fun changedLoginRecoveryVersionsOrKeyCannotUseVerifiedCard() {
        create().use { draft ->
            draft.verify(saved(draft).inputStream())
            for (field in listOf("userId", "deviceId", "warehouseId", "localSessionId", "serverRecoveryEpoch", "userAuthVersion", "deviceVersion"))
                assertThrows(IllegalStateException::class.java) { draft.credential(actor().put(field, "different"), authority.binding.second) }
            assertThrows(IllegalStateException::class.java) { draft.credential(actor(), "b".repeat(64)) }
        }
    }
    @Test fun wrongOversizedOrFailedReadbackCannotEnableProvision() {
        create().use { draft ->
            val original = saved(draft); draft.verify(original.inputStream()); assertTrue(draft.verified)
            for (bad in listOf("another-card".toByteArray(), ByteArray(4097))) {
                assertThrows(IllegalStateException::class.java) { draft.verify(bad.inputStream()) }; assertFalse(draft.verified)
            }
            val output = ByteArrayOutputStream()
            assertThrows(IllegalStateException::class.java) { draft.write({ output }, { "truncated".byteInputStream() }) }
            assertThrows(IllegalStateException::class.java) { draft.verify(output.toByteArray().inputStream()) }
        }
    }
    @Test fun closingInvalidatesVerifiedSecretAndInputAlwaysCloses() {
        val draft = create(); val bytes = saved(draft); draft.verify(bytes.inputStream()); draft.close()
        assertThrows(IllegalStateException::class.java) { draft.credential(actor(), authority.binding.second) }
        var closed = false
        val input = object : java.io.ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
        assertThrows(IllegalStateException::class.java) { draft.verify(input) }; assertTrue(closed)
    }
}
