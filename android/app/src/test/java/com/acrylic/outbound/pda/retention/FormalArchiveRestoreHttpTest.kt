package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.ArchiveRestoreRoom
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FormalArchiveRestoreHttpTest {
    @Test fun productFormalGuardsSurviveSqlFilesAndRoomRestore() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("E2A_FORMAL_HTTP_PRIVATE")
        assumeTrue("Requires isolated FORMAL real HTTP/MySQL driver", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/formal-http-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_FORMAL_ANDROID_REAL_HTTP")
        val attempt = checkNotNull(checkNotNull(file.parentFile).parentFile)
        when (System.getenv("E2A_FORMAL_RESTORE_PHASE")) {
            "source" -> source(attempt, input)
            "restored" -> restored(attempt, input)
            else -> error("Explicit source/restored phase required")
        }
    }

    private fun source(attempt: File, input: JSONObject) {
        val directory = File(attempt, "formal-source-room"); check(directory.mkdirs())
        val db = ArchiveRestoreRoom.open(directory)
        try {
            FormalArchiveWork(attempt, input, db).source()
            File(attempt, "formal-source-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
        } finally { db.close() }
        File(attempt, "formal-source-completed.json").writeText("{\"state\":\"SYNCED\",\"guards\":2}")
    }

    private fun restored(attempt: File, input: JSONObject) {
        val directory = File(attempt, "formal-restored-room")
        ArchiveRestoreRoom.copyOriginal(File(attempt, "formal-source-room"), directory)
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val expected = JSONObject(File(attempt, "formal-source-facts.json").readText())
            assertEquals(SubmissionJson.canonical(expected), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            FormalArchiveWork(attempt, input, db).restored()
            File(attempt, "formal-restored-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
        } finally { db.close() }
        File(attempt, "formal-restored-completed.json").writeText("{\"allTablesPreserved\":true}")
    }
}
