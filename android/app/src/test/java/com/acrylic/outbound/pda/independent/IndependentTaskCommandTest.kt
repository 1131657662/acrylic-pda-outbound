package com.acrylic.outbound.pda.independent

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class IndependentTaskCommandTest {
    private fun command() = IndependentTaskCommand("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222",
        "沪A12345", "断网\n原人员采集", linkedMapOf("003" to 2L, "004" to 3L))
    @Test fun reorderedJsonFieldsAndProductInputHaveSameStableDigest() {
        val original = command()
        val value = JSONObject(original.json().toString())
        val reordered = original.copy(quantities = linkedMapOf("004" to 3L, "003" to 2L))
        assertEquals(original.sha(), reordered.sha()); assertEquals(original, IndependentTaskCommand.read(value))
        assertNotEquals(original.sha(), original.copy(reason = original.reason + "X").sha())
    }
    @Test fun invalidQuantityAndEncodingDoNotPassValidation() {
        val c = command()
        listOf(c.copy(quantities = emptyMap()), c.copy(quantities = mapOf("003" to 0L)), c.copy(quantities = mapOf("003" to -1L)),
            c.copy(quantities = mapOf("003" to Long.MAX_VALUE)), c.copy(quantities = mapOf("003" to 100000L, "004" to 1L)),
            c.copy(vehicle = "x\u0000"), c.copy(reason = "\uD800"), c.copy(vehicle = " ")).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }
    @Test fun duplicateProductCannotBeSilentlyOverwrittenOnRead() {
        val json = command().json(); json.getJSONArray("lines").put(JSONObject().put("code", "003").put("count", "7"))
        assertThrows(IllegalStateException::class.java) { IndependentTaskCommand.read(json) }
    }
}
