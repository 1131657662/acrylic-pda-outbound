package com.acrylic.m71demo

import android.app.Application
import com.acrylic.m71demo.device.TransactionTiming
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TransactionTimingTest {
    @After fun clearObserver() { TransactionTiming.observer = null }

    @Test fun failingTransactionRollsBackWithoutReportingCommittedTiming() = TestRig().use { rig ->
        var reported = false
        TransactionTiming.observer = { _, _, _, _, _ -> reported = true }
        assertThrows(java.io.IOException::class.java) {
            TransactionTiming.run(rig.db, "TEST", "capture") {
                rig.db.orders().update(rig.db.orders().get("order")!!.copy(actualCount = 77))
                throw java.io.IOException("simulated failure")
            }
        }
        assertEquals(0, rig.db.orders().get("order")!!.actualCount)
        assertFalse(reported)
    }

    @Test fun diagnosticFailureDoesNotUndoSuccessfulTransaction() = TestRig().use { rig ->
        TransactionTiming.observer = { _, _, _, _, _ -> throw IllegalStateException("diagnostic failed") }
        TransactionTiming.run(rig.db, "TEST", "capture") {
            rig.db.orders().update(rig.db.orders().get("order")!!.copy(vehicle = "durable"))
        }
        rig.reopen()
        assertEquals("durable", rig.db.orders().get("order")!!.vehicle)
    }
}
