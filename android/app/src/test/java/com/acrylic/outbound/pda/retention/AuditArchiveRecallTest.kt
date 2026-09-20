package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.recall.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AuditArchiveRecallTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun recallUsesVerifiedPrefixAndRetainsAllCurrentOrderEvents() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); f.event("RECALL_STOPPED", "recovery")
            val body = RecoveryBody(f.work.store).create(LocalRecall("recall", f.work.assignment, "{}"), "recovery", f.work.now.toString())
            assertEquals(2, body.getJSONArray("events").length())
            assertEquals(1, body.getJSONArray("auditArchives").length())
            assertEquals("1", body.getJSONArray("auditCoverage").getJSONObject(0).getString("afterSeq"))
            assertEquals("RECALL_STOPPED", body.getJSONArray("auditEvents").getJSONObject(0).getString("type"))
        }
    }

    @Test fun legacyRecallStillCarriesAllDeviceAuditFromSequenceOne() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.event("RECALL_STOPPED", "recovery")
            val body = RecoveryAuditBody(f.work.store).attach(JSONObject(), f.device, "1", f.warehouse)
            assertFalse(body.has("auditArchives")); assertEquals(2, body.getJSONArray("auditEvents").length())
            assertEquals("0", body.getJSONArray("auditCoverage").getJSONObject(0).getString("afterSeq"))
        }
    }

    @Test fun recallRejectsLocallyModifiedArchivedEvent() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); f.event("RECALL_STOPPED", "recovery")
            f.db.openHelper.writableDatabase.execSQL("UPDATE work_event SET detail='tampered' WHERE localSeq=1")
            assertThrows(IllegalStateException::class.java) { RecoveryAuditBody(f.work.store).attach(JSONObject(), f.device, "1", f.warehouse) }
        }
    }

    @Test fun recallRejectsGapAfterVerifiedPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        AuditArchiveFixture(folder.root).use { f ->
            f.repository.archive(); f.event(); f.event("RECALL_STOPPED", "recovery")
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM work_event WHERE localSeq=2")
            assertThrows(IllegalStateException::class.java) { RecoveryAuditBody(f.work.store).attach(JSONObject(), f.device, "1", f.warehouse) }
        }
    }
}
