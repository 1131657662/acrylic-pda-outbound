package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.PdaNetwork
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporarySuccessHttpTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun actualHttpLostReadAndDurableRawRecoverWithoutProductRelease() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("TEMP_SUCCESS_HTTP_PRIVATE")
        assumeTrue("Requires protected nonempty MySQL runner", !path.isNullOrBlank())
        val inputFile = File(checkNotNull(path)).canonicalFile
        check(inputFile.path.replace('\\', '/').matches(Regex(
            "D:/acrylic-pda-worktrees/r06-e2a-migration/\\.local/e2a-cross-20260925-[1-9][0-9]{0,2}/private/android-success-input\\.json")))
        val input = JSONObject(inputFile.readText())
        check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/")
        TemporarySuccessFixture(folder.root, input.getJSONObject("vector")).use { f ->
            val network = PdaNetwork(input.getString("baseUrl"))
            val headers = mapOf("Authorization" to "Bearer ${input.getString("token")}")
            val route = "pda/submissions/${f.local.id}"
            network.original(route, null, headers) // Deliberately lose the first complete response before local persistence.
            f.reopen(); assertNull(f.repository.read(f.local.id, f.key))
            val raw = network.original(route, null, headers)
            val hash = f.repository.retain(f.local.id, raw)
            f.reopen(); assertNull(f.repository.read(f.local.id, f.key))
            val receipt = f.repository.verify(f.local.id, hash, f.key)
            f.reopen(); assertEquals(receipt.toString(), f.repository.read(f.local.id, f.key).toString())
            assertArrayEquals(raw, f.db.temporarySuccess().response(f.local.id, hash)!!.responseBytes)
            assertEquals(f.local, f.db.temporarySubmissions().get(f.local.id))
            assertNotNull(f.db.temporaryScans().guard(f.work.barcodes[0]))
            File(checkNotNull(checkNotNull(inputFile.parentFile).parentFile), "android-success-completed.json").writeText(
                JSONObject().put("submissionId", f.local.id).put("responseSha256", hash).put("httpReads", 2)
                    .put("rawThenReopen", true).put("productState", f.local.state).put("localDeletionAllowed", false).toString())
        }
    }
}
