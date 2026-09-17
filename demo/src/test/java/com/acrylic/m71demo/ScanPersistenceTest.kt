package com.acrylic.m71demo

import android.app.Application
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ScanPersistenceTest {
    private lateinit var rig: TestRig
    @Before fun setup() { rig = TestRig() }
    @After fun teardown() { rig.close() }

    @Test fun receivingDoesNotSaveWithoutEnter() {
        val c = rig.receive("003A0000001")
        assertEquals("WAITING_CONFIRM", c.state)
        assertEquals(0, rig.db.scans().count("order"))
    }

    @Test fun repeatedConfirmationCannotSaveTwice() {
        val c = rig.receive("003A0000001")
        assertTrue(rig.confirmation.request(c.id, rig.clock.elapsedNs()))
        assertFalse(rig.confirmation.request(c.id, rig.clock.elapsedNs()))
        rig.confirmation.save(c.id)
        assertEquals("ALREADY_SAVED", rig.confirmation.save(c.id).status)
        assertEquals(1, rig.db.orders().get("order")!!.actualCount)
        assertEquals(300500L, rig.db.orders().get("order")!!.weightGrams)
    }

    @Test fun duplicateBarcodeHasAnExplicitRejectedInput() {
        rig.save("003A0000001")
        val repeat = rig.receive("003A0000001")
        assertEquals("INVALID", repeat.state)
        assertEquals("BARCODE_ALREADY_OCCUPIED", repeat.error)
        assertEquals(2, rig.db.captures().count("order"))
    }

    @Test fun mixedProductOrderUsesReceptionSequenceEvenWhenClockRollsBack() {
        rig.save("003A0000001")
        rig.clock.wall -= 30_000
        rig.save("571A0000001")
        rig.save("003A0000002")
        val rows = rig.db.scans().page("order", 0, 100)
        assertEquals(listOf("003A0000001", "571A0000001", "003A0000002"), rows.map { it.context.barcode })
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.context.orderScanSeq })
    }

    @Test fun voidAndRescanPreserveSequenceGapAndWeight() {
        rig.save("003A0000001")
        rig.correction.voidLast("order", "实物已核对卸回")
        rig.save("003A0000001")
        val rows = rig.db.scans().page("order", 0, 100)
        assertEquals(listOf("VOID", "ACTIVE"), rows.map { it.status })
        assertEquals(2L, rows.last().context.orderScanSeq)
        assertEquals(300500L, rig.db.orders().get("order")!!.weightGrams)
    }

    @Test fun durableConfirmationSurvivesDatabaseReopen() {
        val c = rig.receive("003A0000001")
        rig.confirmation.request(c.id, rig.clock.elapsedNs())
        rig.reopen()
        assertEquals("CONFIRM_REQUESTED", rig.db.captures().get(c.id)!!.state)
        rig.confirmation.save(c.id)
        rig.reopen()
        assertEquals("ALREADY_SAVED", rig.confirmation.save(c.id).status)
        assertEquals(1, rig.db.scans().count("order"))
    }
}
