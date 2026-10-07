package com.acrylic.outbound.pda.temporary

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
class TemporaryHandoverRejectionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun signedWrongOriginAndWatermarksAreRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val original = f.manifest.toString()
            val changes: List<(JSONObject) -> Unit> = listOf(
                { it.getJSONObject("source").put("authorizationId", f.prepared.authorizationId) },
                { it.getJSONObject("source").put("deviceId", f.session.getString("deviceId")) },
                { it.getJSONObject("source").put("assignmentId", "invented") },
                { it.getJSONObject("temporaryStop").put("orderScanHighWatermark", "9") },
                { it.getJSONObject("temporaryStop").put("submissionId", "wrong") },
                { it.getJSONObject("caseContext").put("toWorkRound", "4") }
            )
            for (change in changes) {
                f.manifest = JSONObject(original)
                change(f.manifest.getJSONObject("stopReceipt")); f.resign("stopReceipt")
                assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
                assertNull(f.db.temporaryHandoverStages().get(f.caseId))
            }
        }
    }

    @Test fun tamperedSignedOrUnsignedEvidenceCannotStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val original = f.manifest.toString()
            val changes: List<(JSONObject) -> Unit> = listOf(
                { it.getJSONObject("preparationReceipt").getJSONObject("businessDecision").put("status", "ACTIVATED") },
                { it.getJSONObject("preparedTicket").getJSONObject("temporaryQualification").put("expiresAt", "2099-01-01T00:00:00Z") },
                { it.put("inputHighWatermark", "9") },
                { it.getJSONObject("capabilities").put("mayResumeScanning", true) },
                { it.put("sourceKind", "FORMAL") },
                { it.put("recoveryPackageSha256", "0".repeat(64)) }
            )
            for (change in changes) {
                f.manifest = JSONObject(original); change(f.manifest)
                assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
                assertNull(f.db.temporaryHandoverStages().get(f.caseId))
            }
        }
    }

    @Test fun revokedOrDifferentSessionCannotReadButKeepsEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            f.repository.stage(f.caseId)
            val original = f.session.toString()
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "userAuthVersion", "deviceVersion")) {
                f.local.auth = JSONObject(original).put(field, "changed")
                assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
                assertNotNull(f.db.temporaryHandoverStages().header(f.caseId))
            }
            f.local.auth = JSONObject(original).put("blockedReason", "撤权")
            assertThrows(Exception::class.java) { f.repository.read(f.caseId) }
            f.local.auth = JSONObject(original)
            assertEquals(f.caseId, f.repository.read(f.caseId).caseId)
        }
    }
}
