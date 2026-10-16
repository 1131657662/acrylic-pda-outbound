package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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
import java.security.MessageDigest

/** Exports new synthetic databases; never copies or edits the retained native app database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class NativeForegroundInputTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun exportSignedSyntheticManualInputsForNativeLifecycleConsumers() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("NATIVE_FOREGROUND_QUEUE_OUT")
        assumeTrue("Requires an explicitly allocated one-shot evidence directory", !path.isNullOrBlank())
        val out = File(checkNotNull(path)).canonicalFile
        check(out.path.replace('\\', '/').matches(Regex(".*/\\.local/goal-20261017-resume/foreground-queue01/seeds0[2-9]")))
        check(!out.exists() && out.mkdirs())
        formal(out); temporary(out)
    }

    private fun formal(out: File) {
        WorkFixture(folder.newFolder("formal")).use { f ->
            val capture = f.receive(source = "MANUAL")
            assertEquals("RECEIVED", capture.state)
            assertEquals(0L, f.db.scans().total(f.assignment).count)
            val data = JSONObject().put("syntheticOnly", true).put("id", f.assignment)
                .put("captureId", capture.id).put("barcode", capture.context.barcode)
                .put("session", f.session).put("now", f.now.toString())
            f.db.close()
            val target = File(out, "formal.db")
            f.context.getDatabasePath("outbound-pda.db").copyTo(target)
            data.put("sha256", sha(target))
            File(out, "formal.json").writeText(data.toString(2))
        }
    }

    private fun temporary(out: File) {
        TemporaryWorkFixture(folder.newFolder("temporary")).use { f ->
            f.start()
            val capture = f.receive(source = "MANUAL")
            assertEquals("RECEIVED", capture.state)
            assertEquals(0L, f.store.total(f.id).count)
            val data = JSONObject().put("syntheticOnly", true).put("id", f.id)
                .put("captureId", capture.id).put("barcode", capture.barcode)
                .put("session", f.fixture.auth).put("now", f.fixture.time.toString())
            f.db.close()
            val target = File(out, "temporary.db")
            f.fixture.context.getDatabasePath("outbound-pda.db").copyTo(target)
            data.put("sha256", sha(target))
            File(out, "temporary.json").writeText(data.toString(2))
        }
    }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }
}
