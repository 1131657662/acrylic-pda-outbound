package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import com.acrylic.outbound.pda.R
import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueTemporaryTextActivityTest {
    @Test fun originalTemporaryPagesCanWriteTextAfterLogoutWithoutEncryptedPackageSetup() {
        TemporaryPagesExportFixture().use { f ->
            f.base.populate(); f.base.stopServer(); f.logout(); f.openExport(); f.chooseOwnCard(); f.readSnapshot()
            f.base.await { f.activity.findViewById<Button>(R.id.exportText).isEnabled }
            assertTrue(f.packages.isEmpty())

            f.activity.findViewById<Button>(R.id.exportText).performClick()
            val picker = checkNotNull(Shadows.shadowOf(f.activity).nextStartedActivityForResult).intent
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
            assertEquals("out.txt", picker.getStringExtra(Intent.EXTRA_TITLE))
            val file = File(f.activity.cacheDir, "synthetic-temporary-out-" + java.util.UUID.randomUUID() + ".txt")
            Shadows.shadowOf(f.activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            f.base.await { !f.model.state.value.busy && f.activity.findViewById<TextView>(R.id.textExportState).text.contains("写入与回读一致") }

            assertTrue(file.readText(Charset.forName("GBK")).contains("\t原姓名甲\t"))
            assertEquals(1, file.readText(Charset.forName("GBK")).lineSequence().count { it.isNotBlank() })
            assertTrue(f.packages.isEmpty());assertNull(f.base.vault.read())
        }
    }
}
