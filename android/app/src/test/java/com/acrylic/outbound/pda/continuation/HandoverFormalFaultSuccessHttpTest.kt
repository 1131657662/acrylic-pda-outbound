package com.acrylic.outbound.pda.continuation

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.submission.ReceiptVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/** Actual restarted-server receipt and original Room copy; synthetic session, JVM SQLite and signature/DAO consumer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverFormalFaultSuccessHttpTest {
    private lateinit var context: android.content.Context
    private lateinit var db: PdaDatabase
    private lateinit var attempt: File
    private lateinit var input: JSONObject

    @Test fun durableSuccessRetainsOriginalFrozenBytesAfterLostReadAndRoomTailFailure() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires one-shot FORMAL success input", !path.isNullOrBlank())
        initialize(File(checkNotNull(path)).canonicalFile)
        try {
            val result = JSONObject(File(attempt, "android-result.json").readText())
            val local = checkNotNull(db.submissions().get(result.getString("submissionId")))
            val network = PdaNetwork(input.getString("baseUrl"))
            val headers = mapOf("Authorization" to "Bearer ${input.getJSONObject("session").getString("accessToken")}")
            val route = "pda/submissions/${local.id}"
            network.original(route, null, headers) // Lose this successful read before any local write.
            reopen(); assertEquals(local, db.submissions().get(local.id))
            val raw = network.original(route, null, headers)
            val receipt = JSONObject(raw.toString(Charsets.UTF_8)).getJSONObject("data")
            val status = ReceiptVerifier.verify(local, receipt, input.getJSONObject("session").getJSONObject("signingKey"))
            assertEquals("SYNCED", status)
            roomFailure(local.id, receipt.toString()); reopen(); assertEquals(local, db.submissions().get(local.id))
            db.submissions().progress(local.id, status, receipt.toString(), "", false)
            reopen(); assertEquals(local.copy(state = "SYNCED", receipt = receipt.toString(), error = "", autoRetry = false), db.submissions().get(local.id))
            assertEquals(result.getString("submissionSha256"), db.submissions().get(local.id)!!.sha256)
            File(attempt, "formal-success-http-response.json").writeBytes(raw)
            File(attempt, "formal-success-android.json").writeText(JSONObject().put("submissionId", local.id)
                .put("state", "SYNCED").put("lostRead", true).put("roomTailRollback", true).put("reopened", true)
                .put("responseSha256", com.acrylic.outbound.pda.security.JwtVerifier.sha(raw)).put("localDeletionAllowed", false).toString())
        } finally { preserve() }
    }

    private fun initialize(file: File) {
        check(file.path.replace('\\', '/').matches(Regex(".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/handover-input\\.json")))
        input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_HANDOVER_REAL_HTTP" && input.getBoolean("deferFormalUpload"))
        check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/")
        attempt = file.parentFile!!.parentFile!!
        context = ApplicationProvider.getApplicationContext()
        val destination = context.getDatabasePath("outbound-pda.db")
        check(!destination.exists()); check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
        File(attempt, "private/android-room.db").copyTo(destination)
        for (suffix in listOf("-wal", "-shm")) {
            val source = File(attempt, "private/android-room.db$suffix")
            if (source.exists()) source.copyTo(File(destination.path + suffix))
        }
        db = PdaDatabase.open(context)
    }

    private fun roomFailure(id: String, receipt: String) {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER formal_success_tail AFTER UPDATE ON local_submission WHEN NEW.state='SYNCED' BEGIN SELECT RAISE(ABORT,'FORMAL_SUCCESS_ROOM_TAIL'); END")
        try {
            val failure = assertThrows(android.database.sqlite.SQLiteException::class.java) {
                db.submissions().progress(id, "SYNCED", receipt, "", false)
            }
            assertTrue(failure.message.orEmpty().contains("FORMAL_SUCCESS_ROOM_TAIL"))
        } finally { sql.execSQL("DROP TRIGGER formal_success_tail") }
    }

    private fun reopen() { db.close(); db = PdaDatabase.open(context) }
    private fun preserve() {
        db.close()
        val original = context.getDatabasePath("outbound-pda.db")
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(original.path + suffix)
            if (source.isFile) source.copyTo(File(attempt, "private/formal-success-room.db$suffix"))
        }
    }
}
