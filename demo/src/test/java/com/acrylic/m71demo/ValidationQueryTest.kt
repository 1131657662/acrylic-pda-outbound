package com.acrylic.m71demo

import android.app.Application
import com.acrylic.m71demo.data.OrderLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ValidationQueryTest {
    @Test fun missingOptionalRowsStillReturnOrderAndExactPlan() = TestRig().use { rig ->
        val rows = rig.db.validation().context("order", "missing", "999A0000001", "missing")!!
        assertEquals("order", rows.order.id)
        assertEquals(3L, rows.plannedTotal)
        assertNull(rows.line); assertNull(rows.bale); assertNull(rows.cloud); assertNull(rows.guard); assertNull(rows.auth)
    }

    @Test fun extraProductDoesNotMultiplyTotalOrCandidateRows() = TestRig().use { rig ->
        rig.db.orders().insertLines(listOf(OrderLine("order", "777", 320)))
        val auth = rig.db.orders().get("order")!!.authorizationId
        val rows = rig.db.validation().context("order", "snapshot", "003A0000001", auth)!!
        assertEquals(323L, rows.plannedTotal)
        assertEquals("003", rows.line!!.deliveryCode)
        assertEquals(300500L, rows.bale!!.weightGrams)
    }

    @Test fun finalValidationSeesNewOccupationWithoutCaching() = TestRig().use { rig ->
        val candidate = rig.receive("003A0000001")
        assertNull(rig.store.validation(candidate).guard)
        rig.confirmation.request(candidate.id, rig.clock.elapsedNs())
        rig.confirmation.save(candidate.id)
        assertEquals(candidate.id, rig.store.validation(candidate).guard!!.scanId)
        assertEquals(1, rig.store.validation(candidate).line!!.actualCount)
    }

    @Test fun stateIndexPreservesEarliestSequenceAcrossStates() = TestRig().use { rig ->
        val first = rig.receive("003A0000001")
        val second = rig.receive("003A0000002")
        rig.confirmation.request(second.id, rig.clock.elapsedNs())
        assertEquals(first.id, rig.db.captures().pending("order")!!.id)
        rig.correction.discard(first.id)
        assertEquals(second.id, rig.db.captures().pending("order")!!.id)
    }
}
