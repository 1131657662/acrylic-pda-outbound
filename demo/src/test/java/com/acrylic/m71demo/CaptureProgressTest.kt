package com.acrylic.m71demo

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CaptureProgressTest {
    @Test fun progressUpdateCannotOverwriteOriginalCaptureContext() = TestRig().use { rig ->
        val original = rig.receive("003A0000001")
        val changed = original.copy(state = "CONFIRM_REQUESTED", confirmedAtNs = 123L,
            context = original.context.copy(originDeviceId = "wrong", orderScanSeq = 99L,
                expectedDispatchCycle = 9, rawInput = "wrong", authorizationId = "wrong"))
        rig.db.captures().updateProgress(changed)
        val actual = requireNotNull(rig.db.captures().get(original.id))
        assertEquals(original.context, actual.context)
        assertEquals("CONFIRM_REQUESTED", actual.state)
        assertEquals(123L, actual.confirmedAtNs)
    }

    @Test fun missingOriginalCaptureCannotReportSuccessfulProgressUpdate() = TestRig().use { rig ->
        val capture = rig.receive("003A0000001").copy(id = "missing")
        try { rig.db.captures().updateProgress(capture); fail("Must reject missing original") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("missing")) }
        assertEquals(1, rig.db.captures().count("order"))
    }

    @Test fun savedTotalsAndCaptureSequenceRetainOrderConfiguration() = TestRig().use { rig ->
        val original = requireNotNull(rig.db.orders().get("order"))
        rig.save("003A0000001")
        rig.save("571A0000001")
        rig.reopen()
        assertEquals(original.copy(nextOrderScanSeq = 3, actualCount = 2, weightGrams = 601000),
            rig.db.orders().get("order"))
        assertEquals(1, rig.db.orders().line("order", "003")!!.actualCount)
        assertEquals(300500L, rig.db.orders().line("order", "571")!!.weightGrams)
    }
}
