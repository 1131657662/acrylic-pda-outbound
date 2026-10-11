package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import kotlinx.coroutines.*
import org.json.JSONArray
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
class TemporaryRecoveredHandoverRejectionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun signedWrongQualificationCannotPersist() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val original = f.manifest.toString()
            val mutations: List<(JSONObject) -> Unit> = listOf(
                { it.put("purpose", "TEMP_WORK") },
                { it.put("warehouseId", UUID.randomUUID().toString()) },
                { it.getJSONObject("recipient").put("deviceId", UUID.randomUUID().toString()) },
                { it.getJSONObject("recipient").put("userAuthVersion", "2") },
                { it.getJSONObject("recipient").put("keySha256", "f".repeat(64)) },
                { it.getJSONObject("control").put("transportRecoveryEpoch", "1") },
                { it.getJSONObject("source").put("sourceRecoveryEpoch", "2") },
                { it.getJSONObject("source").put("payloadSha256", "f".repeat(64)) },
                { it.getJSONObject("baseline").put("inventorySnapshotId", UUID.randomUUID().toString()) },
                { it.put("expiresAt", f.local.time.toString()) },
                { it.put("capabilities", JSONArray().put("PREPARE").put("DOWNLOAD").put("SCAN")) })
            for (mutate in mutations) {
                f.manifest = JSONObject(original); mutate(f.qualification); f.resign()
                assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
                assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
            }
        }
    }
    @Test fun unsignedMutationOrPartialDownloadCannotPersist() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val original = f.manifest.toString()
            f.manifest.getJSONObject("preparationReceipt").getJSONObject("caseContext").put("approvedBy", UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            f.manifest = JSONObject(original)
            f.base.beforeOriginal = { if (it.endsWith("recovery-package")) error("synthetic interrupted download") }
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun exactSignedAlternateChecklistCannotReplaceSavedCase() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            f.manifest.getJSONArray("checklist").getJSONObject(0).put("state", "UNLOADED")
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertEquals(row, f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun newLocalLoginCannotReadOldSessionBoundStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            f.session.put("localSessionId", UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
            assertEquals(row, f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun ordinaryModeAndDefaultClosedGateCannotReceiveRecoveryStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            assertThrows(Exception::class.java) { TemporaryRecoveredHandoverRepository(f.db, f.base.connection).stage(f.caseId) }
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "NORMAL"))
            assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
            assertNull(f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
    @Test fun expiredSessionCannotReadRetainedEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            f.local.time = f.local.time.plusSeconds(600)
            assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
            assertEquals(row, f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
}
