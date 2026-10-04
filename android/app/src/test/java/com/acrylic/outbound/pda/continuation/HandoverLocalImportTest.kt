package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.preparation.LocalActivation
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.submission.SubmissionRecords
import com.acrylic.outbound.pda.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
class HandoverLocalImportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun sourceFactsSurviveTargetRoomImportUnchanged() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val order = f.install()

            assertEquals("HANDOVER_FROZEN", order.state)
            assertEquals(f.body.getString("captureHighWatermark").toLong() + 1, order.nextOrderScanSeq)
            assertEquals(f.body.getString("expectedCount").toLong(), order.actualCount)
            assertRows(f, "captures", f.db.captures().page(f.target, 0, 100).map(SubmissionRecords::capture))
            assertRows(f, "scans", f.db.scans().page(f.target, 0, 100).map(SubmissionRecords::scan))
            assertRows(f, "events", f.db.work().events(f.target).map(SubmissionRecords::event))
            assertEquals("IMPORTED_FROZEN", f.db.handoverStages().get(f.caseId)?.state)
            assertEquals(1L, f.db.work().device(f.input.session.getString("deviceId"))?.nextCaptureSeq)
        }
    }

    @Test fun retryAfterDatabaseReopenDoesNotDuplicateFacts() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val first = f.install()
            f.reopen()

            assertEquals(first, f.install())
            assertEquals(2, f.db.captures().page(f.target, 0, 100).size)
            assertEquals(1, f.db.scans().page(f.target, 0, 100).size)
        }
    }

    @Test fun failureAfterCaptureWritesRollsBackPreparationAndAllFacts() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_handover_event BEFORE INSERT ON work_event
                BEGIN SELECT RAISE(ABORT, 'test event persistence failure'); END""")

            assertThrows(Exception::class.java) { f.install() }

            assertNull(f.db.preparations().order(f.target))
            assertNull(f.db.work().order(f.target))
            assertTrue(f.db.captures().page(f.target, 0, 100).isEmpty())
            assertTrue(f.db.scans().page(f.target, 0, 100).isEmpty())
            assertEquals("STAGED", f.db.handoverStages().get(f.caseId)?.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_handover_event")
            assertEquals("HANDOVER_FROZEN", f.install().state)
        }
    }

    @Test fun importedOrderCannotReceiveNewInput() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.install()

            assertThrows(IllegalStateException::class.java) {
                WorkReception(store(f)).receive(WorkInput(f.target, "10000000001", "BROADCAST", TimeSample(1, 1, 1)))
            }
            assertEquals(2, f.db.captures().page(f.target, 0, 100).size)
        }
    }

    @Test fun importedOrderCannotFreezeAFinalSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.install()

            assertThrows(IllegalStateException::class.java) { SubmissionFreeze(store(f)) { f.input.now }.freeze(f.target, "") }
            assertNull(f.db.submissions().forOrder(f.target))
        }
    }

    @Test fun retryRejectsChangedOriginalRowsWithoutOverwritingThem() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.install()
            val saved = f.db.captures().page(f.target, 0, 100).first()
            f.db.captures().progress(saved.id, saved.state, "changed", saved.confirmedAt)

            assertThrows(IllegalStateException::class.java) { f.install() }
            assertEquals("changed", f.db.captures().get(saved.id)?.error)
        }
    }

    @Test fun retryRejectsAdditionalGuardWithoutOverwritingIt() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.install()
            val scan = f.db.scans().page(f.target, 0, 100).single()
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE work_guard SET barcode=? WHERE captureId=?",
                arrayOf("unexpected-barcode", scan.captureId))

            assertThrows(IllegalStateException::class.java) { f.install() }
            assertEquals(scan.captureId, f.db.scans().guard("unexpected-barcode")?.captureId)
        }
    }

    @Test fun restagingSameEvidencePreservesImportedState() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val original = checkNotNull(f.db.handoverStages().get(f.caseId))
            f.install()

            saveVerifiedHandoverStage(f.db, original, f.input.session)

            assertEquals("IMPORTED_FROZEN", f.db.handoverStages().get(f.caseId)?.state)
            assertEquals("HANDOVER_FROZEN", f.install().state)
        }
    }

    @Test fun existingTargetWorkIsNeverOverwritten() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            LocalActivation(f.db).activate(f.input)
            val previous = WorkOrder(f.target, f.input.assignment.getString("orderId"), "old", 1, 1)
            f.db.work().insertOrder(previous)

            assertThrows(IllegalStateException::class.java) { f.install() }
            assertEquals(previous, f.db.work().order(f.target))
            assertEquals("STAGED", f.db.handoverStages().get(f.caseId)?.state)
        }
    }

    @Test fun voidedSourceScanDoesNotCreateTargetGuardOrTotals() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root) { source ->
            val capture = source.scan()
            source.processor.corrections.voidLast(source.assignment, capture.id, "test physical check")
        }.use { f ->
            val order = f.install()
            val scan = f.db.scans().page(f.target, 0, 100).single()

            assertEquals(0L, order.actualCount)
            assertEquals("VOID", scan.status)
            assertNull(f.db.scans().guard(scan.barcode))
            assertRows(f, "events", f.db.work().events(f.target).map(SubmissionRecords::event))
        }
    }

    private fun assertRows(f: HandoverImportFixture, key: String, actual: List<org.json.JSONObject>) {
        assertEquals(SubmissionJson.canonical(f.body.getJSONArray(key)), SubmissionJson.canonical(JSONArray(actual)))
    }

    private fun store(f: HandoverImportFixture) = WorkStore(f.db, object : WorkAccess {
        override fun identity(order: com.acrylic.outbound.pda.data.PreparedOrder) {
            check(order.deviceId == f.input.session.getString("deviceId") && order.operatorId == f.input.session.getString("userId"))
        }
        override fun authorize(order: com.acrylic.outbound.pda.data.PreparedOrder) = f.input.now.also { identity(order) }
        override fun sample() = TimeSample(1, f.input.now.toEpochMilli(), 1)
    })
}
