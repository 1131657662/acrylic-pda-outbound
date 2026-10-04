package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.preparation.LocalActivation
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.work.WorkAccess
import com.acrylic.outbound.pda.work.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverWorkOpeningTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun stagedHandoverCannotOpenEmptyOrdinaryWork() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            rejectsOrdinaryOpening(f)
            assertEquals("STAGED", f.db.handoverStages().get(f.caseId)?.state)
        }
    }

    @Test fun unstagedHandoverCannotOpenEmptyOrdinaryWork() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_handover_stage")

            rejectsOrdinaryOpening(f)
            assertNull(f.db.handoverStages().get(f.caseId))
        }
    }

    @Test fun zeroInputHandoverCannotOpenEmptyOrdinaryWork() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root) {}.use { f ->
            assertEquals("1", f.input.assignment.getString("startOrderScanSeq"))
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_handover_stage")

            rejectsOrdinaryOpening(f)
        }
    }

    @Test fun importedFrozenWorkCanBeReopenedWithoutCreatingNewFacts() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val original = f.install()
            val events = f.db.work().events(f.target)
            f.reopen()

            store(f).open(f.target)

            assertEquals(original, f.db.work().order(f.target))
            assertEquals("HANDOVER_FROZEN", original.state)
            assertEquals(events, f.db.work().events(f.target))
        }
    }

    private fun rejectsOrdinaryOpening(f: HandoverImportFixture) {
        LocalActivation(f.db).activate(f.input)
        assertNotNull(f.db.preparations().order(f.target))

        assertThrows(IllegalStateException::class.java) { store(f).open(f.target) }

        assertNull(f.db.work().order(f.target))
        assertTrue(f.db.captures().page(f.target, 0, 100).isEmpty())
        assertTrue(f.db.work().events(f.target).isEmpty())
    }

    private fun store(f: HandoverImportFixture) = WorkStore(f.db, object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.deviceId == f.input.session.getString("deviceId") &&
                order.operatorId == f.input.session.getString("userId"))
        }
        override fun authorize(order: PreparedOrder): java.time.Instant {
            identity(order)
            JwtVerifier.authorization(JSONObject(order.assignmentJson), f.input.session,
                JSONObject(order.signingKeyJson), f.input.now)
            return f.input.now
        }
        override fun sample() = TimeSample(1, f.input.now.toEpochMilli(), 1)
    })
}
