package com.acrylic.outbound.pda.temporary

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredHandoverHttpTest {
    @Test fun actualProductHttpOriginalsPersistAcrossFreshJvm() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("RECOVERED_HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires fresh isolated admitted recipient and actual HTTP server", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex("D:/acrylic-pda-outbound/\\.local/e2a-cross-20260925-[1-9][0-9]{0,5}/private/android-http-input\\.json")))
        val input = JSONObject(file.readText()); val phase = checkNotNull(System.getenv("RECOVERED_HANDOVER_ANDROID_PHASE"))
        check(phase in setOf("stage", "restart"))
        val original = File(input.getString("originalDirectory")).canonicalFile
        check(original == file.parentFile!!.parentFile)
        val evidence = File(original, "recovered-handover-android").also { it.mkdirs() }
        val output = File(evidence, phase).also { check(it.mkdir()) }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = context.getDatabasePath("outbound-pda.db"); check(!target.exists()); target.parentFile!!.mkdirs()
        if (phase == "restart") File(evidence, "stage-final.db").copyTo(target)
        val db = PdaDatabase.open(context)
        try { exercise(db, input, phase, original, output) }
        finally {
            db.close(); target.copyTo(File(evidence, "$phase-final.db"))
            File(output, "database-sha256.txt").writeText(JwtVerifier.sha(target.readBytes()))
        }
    }
    private fun exercise(db: PdaDatabase, input: JSONObject, phase: String, original: File, output: File) {
        val session = input.getJSONObject("session"); val id = input.getString("caseId")
        val connection = TemporaryRecoveredHandoverHttpConnection(input, output)
        val repository = TemporaryRecoveredHandoverRepository(db, connection, true)
        if (phase == "stage") {
            db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = session.getString("serverRecoveryEpoch"), mode = "RECONCILING"))
            assertThrows(java.io.IOException::class.java) { repository.stage(id) }
            assertNull(db.temporaryRecoveredHandoverStages().get(id))
        } else { connection.losePayload = false; assertNotNull(repository.read(id)) }
        val row = repository.stage(id)
        assertEquals(row, repository.read(id)); assertEquals(row, repository.stage(id))
        assertArrayEquals(File(original, "download-manifest.json").readBytes(), db.temporaryRecoveredHandoverChunks().read(id, "MANIFEST", row.manifestLength))
        assertArrayEquals(File(original, "download-payload.json").readBytes(), db.temporaryRecoveredHandoverChunks().read(id, "PAYLOAD", row.payloadLength))
        assertNull(db.temporaryHandoverStages().get(id)); assertNull(db.temporaryHandoverConfirmations().get(id))
        assertNull(db.temporaryHandoverActivations().get(id)); assertEquals("RECONCILING", db.sync().recovery()!!.mode)
        File(output, "result.json").writeText(JSONObject().put("phase", phase).put("caseId", id).put("sessionId", row.sessionId)
            .put("actualProductHttp", true).put("originalManifestAndBytes", true).put("nativeRoom", true).put("mayResumeScanning", false)
            .put("localDeletionAllowed", false).put("sourceRecoveryEpoch", row.sourceRecoveryEpoch).put("targetRecoveryEpoch", row.targetRecoveryEpoch)
            .put("manifestSha256", row.manifestSha256).put("payloadSha256", row.payloadSha256).toString())
    }
}
