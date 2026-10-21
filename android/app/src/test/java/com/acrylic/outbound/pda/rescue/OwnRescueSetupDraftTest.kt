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
class OwnRescueSetupDraftTest {
    private fun actor() = JSONObject().put("userId", "synthetic-user").put("deviceId", "synthetic-device")
        .put("warehouseId", "synthetic-warehouse").put("serverRecoveryEpoch", "1")
        .put("localSessionId", "synthetic-generation").put("accessToken", "synthetic-must-not-export")
    private val digest = "a".repeat(64)

    @Test fun generatedCardRequiresSeparateReadbackAndContainsNoLoginMaterial() {
        OwnRescueSetupDraft.create(actor(), digest).use { draft ->
            val output = ByteArrayOutputStream()
            draft.write({ output }, { output.toByteArray().inputStream() })
            assertFalse(draft.verified)
            assertThrows(IllegalStateException::class.java) { draft.credential(actor(), digest) }
            val bytes = output.toByteArray(); val json = JSONObject(bytes.toString(Charsets.UTF_8))
            assertEquals("synthetic-user", json.getString("operatorId"))
            val password = json.getString("credential")
            assertTrue(password.matches(Regex("[A-Za-z0-9_-]{32}")))
            assertFalse(json.toString().contains("synthetic-must-not-export"))
            assertFalse(json.has("localSessionId")); assertFalse(json.has("accessToken"))
            draft.verify(bytes.inputStream())
            assertTrue(draft.verified); assertArrayEquals(password.toCharArray(), draft.credential(actor(), digest))
        }
    }

    @Test fun changedSessionOrDeviceKeyCannotUseVerifiedDraft() {
        OwnRescueSetupDraft.create(actor(), digest).use { draft ->
            val output = ByteArrayOutputStream(); draft.write({ output }, { output.toByteArray().inputStream() })
            draft.verify(output.toByteArray().inputStream())
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId"))
                assertThrows(IllegalStateException::class.java) { draft.credential(actor().put(field, "another"), digest) }
            assertThrows(IllegalStateException::class.java) { draft.credential(actor(), "b".repeat(64)) }
        }
    }

    @Test fun mismatchedOrOversizedReadbackCannotEnableDraft() {
        OwnRescueSetupDraft.create(actor(), digest).use { draft ->
            val output = ByteArrayOutputStream(); draft.write({ output }, { output.toByteArray().inputStream() })
            draft.verify(output.toByteArray().inputStream()); assertTrue(draft.verified)
            assertThrows(IllegalStateException::class.java) { draft.verify("another-card".byteInputStream()) }
            assertFalse(draft.verified)
            assertThrows(IllegalStateException::class.java) { draft.verify(ByteArray(4097).inputStream()) }
            assertFalse(draft.verified)
        }
    }

    @Test fun failedTargetReadbackCannotBeVerifiedLaterAsSuccessfulWrite() {
        OwnRescueSetupDraft.create(actor(), digest).use { draft ->
            val output = ByteArrayOutputStream()
            assertThrows(IllegalStateException::class.java) { draft.write({ output }, { "truncated".byteInputStream() }) }
            assertThrows(IllegalStateException::class.java) { draft.verify(output.toByteArray().inputStream()) }
            assertFalse(draft.verified)
        }
    }

    @Test fun closingInvalidatesDraftEvenWithPreviouslyMatchingCard() {
        val draft = OwnRescueSetupDraft.create(actor(), digest); val output = ByteArrayOutputStream()
        draft.write({ output }, { output.toByteArray().inputStream() }); draft.verify(output.toByteArray().inputStream())
        draft.close(); assertFalse(draft.verified)
        assertThrows(IllegalStateException::class.java) { draft.credential(actor(), digest) }
        assertThrows(IllegalStateException::class.java) { draft.write({ output }, { output.toByteArray().inputStream() }) }
    }

    @Test fun failedOutputCannotEnableVerificationAndInputAlwaysCloses() {
        OwnRescueSetupDraft.create(actor(), digest).use { draft ->
            val failed = object : java.io.OutputStream() { override fun write(value: Int) { throw java.io.IOException("synthetic full target") } }
            assertThrows(java.io.IOException::class.java) { draft.write({ failed }, { "unused".byteInputStream() }) }
            var closed = false
            val input = object : java.io.ByteArrayInputStream(byteArrayOf(1)) { override fun close() { closed = true; super.close() } }
            assertThrows(IllegalStateException::class.java) { draft.verify(input) }
            assertTrue(closed); assertFalse(draft.verified)
        }
    }
}
