package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.SubmissionJson
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
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverImportRejectionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun changedRecoveryBytesNeverCreateTargetPreparation() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val stage = checkNotNull(f.db.handoverStages().get(f.caseId))
            f.db.handoverStages().update(stage.copy(recoveryBytes = "{}".toByteArray()))

            assertThrows(Exception::class.java) { f.install() }
            assertNull(f.db.preparations().order(f.target))
        }
    }

    @Test fun inputFromDifferentOriginalAssignmentCannotBeImported() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.body.getJSONArray("captures").getJSONObject(0)
                .put("assignmentId", UUID.randomUUID().toString())
            f.db.handoverStages().update(f.stage())

            assertThrows(IllegalStateException::class.java) { f.install() }
            assertNull(f.db.preparations().order(f.target))
            assertNull(f.db.work().order(f.target))
        }
    }

    @Test fun expiredTargetAuthorizationNeverCreatesLocalWork() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val stage = checkNotNull(f.db.handoverStages().get(f.caseId))
            val expired = f.input.copy(now = Instant.parse(f.input.assignment.getString("expiresAt")))

            assertThrows(IllegalStateException::class.java) { HandoverLocalImport(f.db).install(stage, expired) }
            assertNull(f.db.preparations().order(f.target))
        }
    }

    @Test fun damagedFixedInventoryRollsBackImport() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val stage = checkNotNull(f.db.handoverStages().get(f.caseId))
            val damaged = f.input.copy(payloads = f.input.payloads + ("inventory" to "[]".toByteArray()))

            assertThrows(IllegalStateException::class.java) { HandoverLocalImport(f.db).install(stage, damaged) }
            assertNull(f.db.preparations().order(f.target))
            assertEquals("STAGED", f.db.handoverStages().get(f.caseId)?.state)
        }
    }

    @Test fun differentReceiverCannotImportSignedSource() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            val stage = checkNotNull(f.db.handoverStages().get(f.caseId))
            val other = f.input.copy(session = JSONObject(f.input.session.toString()).put("deviceId", UUID.randomUUID().toString()))

            assertThrows(IllegalStateException::class.java) { HandoverLocalImport(f.db).install(stage, other) }
            assertNull(f.db.preparations().order(f.target))
        }
    }

    @Test fun signedFailureWithoutAcknowledgementTimeCannotImportUnresolvedInput() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root).use { f ->
            f.body.getJSONArray("captures").getJSONObject(1).put("failureAckAt", JSONObject.NULL)
            f.db.handoverStages().update(f.stage())

            assertThrows(IllegalStateException::class.java) { f.install() }
            assertNull(f.db.preparations().order(f.target))
        }
    }

    @Test fun historicalDispositionRetainsGuardButExcludesTotals() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverImportFixture(folder.root) { source ->
            val capture = source.scan()
            val scan = checkNotNull(source.db.scans().get(capture.id))
            val proof = JSONObject().put("captureId", capture.id).put("approvedEvidenceId", UUID.randomUUID().toString())
                .put("action", "QUARANTINE").put("baleId", scan.baleId).put("barcode", scan.barcode)
                .put("expectedDispatchCycle", scan.expectedDispatchCycle.toString())
            source.db.runInTransaction {
                source.db.dispositions().insert(LocalDisposition(capture.id, source.assignment,
                    proof.getString("approvedEvidenceId"), "QUARANTINE", SubmissionJson.canonical(proof), 0))
                source.db.work().totals(source.assignment, 0, 0, null)
                source.db.work().lineTotals(source.assignment, scan.deliveryCode, 0, 0)
            }
        }.use { f ->
            val work = f.install()
            val scan = f.db.scans().page(f.target, 0, 100).single()

            assertEquals(0L, work.actualCount)
            assertEquals(0L, work.weightGrams)
            assertEquals(scan.captureId, f.db.scans().guard(scan.barcode)?.captureId)
            assertEquals(SubmissionJson.canonical(f.body.getJSONArray("dispositions")),
                SubmissionJson.canonical(f.db.dispositions().proofs(f.target)))
        }
    }
}
