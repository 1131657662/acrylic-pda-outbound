package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverStageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun originalBytesSurviveReopenAndRetryWithoutCreatingAssignment() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            assertEquals("STAGED", row.state); assertEquals(1L, row.inputHighWatermark)
            assertEquals(f.submissionId, row.submissionId)
            f.local.reopen()
            val recovered = f.repository.read(f.caseId)
            assertArrayEquals(f.bytes(f.body), recovered.payloadBytes)
            assertEquals(row.copy(payloadBytes = recovered.payloadBytes), recovered)
            assertArrayEquals(row.payloadBytes, f.repository.stage(f.caseId).payloadBytes)
            f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM work_order").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun lastSqlFailureRollsBackAndOriginalRetryRecovers() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_temp_stage AFTER INSERT ON temporary_handover_stage BEGIN SELECT RAISE(ABORT,'D06_STAGE_LAST_WRITE'); END")
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_temp_stage")
            f.local.reopen()
            assertEquals(f.caseId, f.repository.stage(f.caseId).caseId)
            assertNotNull(f.db.temporary().byAuthorization(f.prepared.authorizationId))
        }
    }

    @Test fun delayedResponseCannotCrossLoginGeneration() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            f.beforeOriginal = { if (it.endsWith("recovery-package")) f.session.put("localSessionId", UUID.randomUUID().toString()) }
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
        }
    }

    @Test fun changedOriginalCannotOverwriteDurableFacts() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val saved = f.repository.stage(f.caseId)
            f.body.put("extraFact", "changed"); f.resnapshot()
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertArrayEquals(saved.payloadBytes, f.repository.read(f.caseId).payloadBytes)
        }
    }

    @Test fun corruptStoredPayloadIsRetainedAndRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            f.repository.stage(f.caseId)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_handover_stage SET payloadBytes=? WHERE caseId=?", arrayOf("{}".toByteArray(), f.caseId))
            f.local.reopen()
            assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
            assertArrayEquals("{}".toByteArray(), f.db.temporaryHandoverStages().get(f.caseId)!!.payloadBytes)
        }
    }

    @Test fun missingAckOrExpiredOwnCredentialCannotStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_preparation SET ackJson=NULL")
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            f.db.temporary().acknowledged(f.prepared.authorizationId, f.prepared.ackJson!!)
            f.local.time = java.time.Instant.parse(f.local.manifest.getString("expiresAt"))
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
        }
    }
}
