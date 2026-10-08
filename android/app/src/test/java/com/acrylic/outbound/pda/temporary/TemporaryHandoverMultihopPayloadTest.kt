package com.acrylic.outbound.pda.temporary

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
class TemporaryHandoverMultihopPayloadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun cumulativeOrderHighCanExceedStoppingDeviceLocalHigh() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            multihop(f)
            val original = f.bytes(f.body)
            val row = f.repository.stage(f.caseId)
            assertEquals(2L, row.inputHighWatermark)
            assertArrayEquals(original, row.payloadBytes)
            f.local.reopen()
            assertArrayEquals(original, f.repository.read(f.caseId).payloadBytes)
        }
    }

    @Test fun signedStopBelowOwnDeviceHighCannotStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            multihop(f)
            f.body.getJSONArray("captures").getJSONObject(1).put("deviceCaptureSeq", "2")
            f.resnapshot()
            val error = assertThrows(IllegalStateException::class.java) { f.repository.stage(f.caseId) }
            assertTrue(error.message!!.contains("高水位不一致"))
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
        }
    }

    @Test fun signedOrderHighCannotHideMissingPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            multihop(f)
            f.body.getJSONArray("captures").remove(0)
            f.resnapshot()
            val error = assertThrows(IllegalStateException::class.java) { f.repository.stage(f.caseId) }
            assertTrue(error.message!!.contains("序号不连续"))
            assertNull(f.db.temporaryHandoverStages().get(f.caseId))
        }
    }

    private fun multihop(f: TemporaryHandoverFixture) {
        f.deviceStopHigh = "1"
        f.body.put("workRound", "2").put("captureHighWatermark", "2")
        val captures = f.body.getJSONArray("captures")
        captures.getJSONObject(0).put("originDeviceId", UUID.randomUUID().toString()).put("deviceCaptureSeq", "1")
        captures.put(JSONObject().put("orderScanSeq", "2").put("originDeviceId", f.sourceDevice).put("deviceCaptureSeq", "1"))
        f.resnapshot()
    }
}
