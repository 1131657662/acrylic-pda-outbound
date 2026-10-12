package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/** Reads original isolated HTTP evidence after exclusion/link/confirm; no recreated signature. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryExclusionSuccessTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun excludedCandidateDoesNotChangeOriginalSourceOrGrantDeletion() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("EXCLUSION_ANDROID_VECTOR")
        assumeTrue("Requires original HTTP selection evidence and independently pinned public key", !path.isNullOrBlank())
        val input = File(checkNotNull(path)).canonicalFile
        check(input.path.replace('\\', '/').startsWith("D:/acrylic-pda-outbound/.local/goal-20261011/"))
        TemporarySuccessFixture(folder.root, JSONObject(input.readText())).use { f ->
            val target = f.vector.getString("mappedOrderId")
            val hash = f.repository.retain(f.local.id, f.bytes)
            assertTrue(runCatching { f.repository.verify(f.local.id, hash, f.key) }.isFailure)
            val receipt = f.repository.verify(f.local.id, hash, f.key, target)
            assertEquals(target, receipt.getString("orderId"))
            assertEquals("SUCCEEDED", receipt.getString("status"))
            assertFalse(receipt.getBoolean("mayResumeScanning"))
            assertFalse(receipt.getBoolean("stockReleased"))
            assertFalse(receipt.optBoolean("localDeletionAllowed", false))
            f.reopen()
            assertEquals(receipt.toString(), f.repository.read(f.local.id, f.key).toString())
            assertArrayEquals(f.bytes, f.db.temporarySuccess().response(f.local.id, hash)!!.responseBytes)
            assertEquals(f.local, f.db.temporarySubmissions().get(f.local.id))
            assertNotNull(f.db.temporaryScans().guard(f.work.barcodes[0]))
            File(input.parentFile, "android-consumption-result.json").writeText(JSONObject()
                .put("submissionId", f.local.id).put("responseSha256", hash).put("mappedOrderId", target)
                .put("rawThenReopen", true).put("localDeletionAllowed", false).toString())
        }
    }
}
