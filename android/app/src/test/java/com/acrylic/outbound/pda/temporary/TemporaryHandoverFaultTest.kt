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
class TemporaryHandoverFaultTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun signedSupervisorFenceStagesOriginalWithoutDeviceAcknowledgement() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            TemporaryHandoverFaultFixture.install(f)
            f.repository.stage(f.caseId)
            val staged = f.repository.read(f.caseId)
            assertArrayEquals(f.bytes(f.body), staged.payloadBytes)
            assertEquals("FAULT_FENCE", JSONObject(staged.manifestJson).getString("stopKind"))
            assertEquals(false, JSONObject(staged.manifestJson).getJSONObject("faultReceipt")
                .getJSONObject("businessDecision").get("oldDeviceAcknowledged"))
        }
    }

    @Test fun signedInvalidSupervisorOrCustodyCannotPersistStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            TemporaryHandoverFaultFixture.install(f); val original = f.manifest.toString()
            val mutations: List<(JSONObject) -> Unit> = listOf(
                { it.getJSONObject("actor").put("principalId", f.sourceUser) },
                { it.getJSONObject("actor").put("principalId", it.getJSONObject("caseContext").getString("requestedBy")) },
                { it.getJSONObject("actor").put("deviceId", f.sourceDevice) },
                { it.getJSONObject("actor").getJSONObject("permissionSnapshot").put("effectivePermissions", org.json.JSONArray()) },
                { it.getJSONObject("businessDecision").put("oldDeviceAcknowledged", true) },
                { it.getJSONObject("businessDecision").getJSONObject("physicalControl").put("deviceState", "LOST") },
                { it.getJSONObject("businessDecision").getJSONObject("physicalControl").put("unresolvedInputCount", "1") },
                { it.getJSONObject("businessDecision").getJSONObject("archivedEvidence").put("orderScanHighWatermark", "2") }
            )
            for (mutation in mutations) {
                f.manifest = JSONObject(original); mutation(f.manifest.getJSONObject("faultReceipt")); f.resign("faultReceipt")
                assertThrows(Exception::class.java) { f.repository.stage(f.caseId) }
                assertNull(f.db.temporaryHandoverStages().get(f.caseId))
            }
        }
    }
}
