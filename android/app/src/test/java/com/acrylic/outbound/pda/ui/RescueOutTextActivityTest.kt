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
class RescueOutTextActivityTest {
    @Test fun cancellingNextSaveDoesNotShowEarlierSuccess() {
        RescuePackageSetupUiFixture().use { f ->
            val original = save(f)
            val bytes = original.readBytes()

            val request = choose(f)
            assertFalse(status(f).contains("写入与回读一致"))
            Shadows.shadowOf(f.activity).receiveResult(request, Activity.RESULT_CANCELED, null)
            f.await { status(f).contains("保存已取消") }

            assertFalse(status(f).contains("写入与回读一致"))
            assertArrayEquals(bytes, original.readBytes())
            assertTrue(f.model.state.value.canExportText)
        }
    }

    @Test fun failedNextDestinationDoesNotShowEarlierSuccess() {
        RescuePackageSetupUiFixture().use { f ->
            val original = save(f)
            val bytes = original.readBytes()
            val request = choose(f)

            Shadows.shadowOf(f.activity).receiveResult(request, Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(f.activity.cacheDir)))
            f.await { !f.model.state.value.busy && status(f).contains("保存未完成") }

            assertFalse(status(f).contains("写入与回读一致"))
            assertArrayEquals(bytes, original.readBytes())
            assertTrue(f.model.state.value.canExportText)
        }
    }

    @Test fun rereadingSnapshotClearsEarlierSuccess() {
        RescuePackageSetupUiFixture().use { f ->
            save(f)

            f.activity.findViewById<Button>(R.id.readSnapshot).performClick()
            f.await { !f.model.state.value.busy && status(f).contains("可直接导出") }

            assertFalse(status(f).contains("写入与回读一致"))
            assertTrue(f.model.state.value.canExportText)
        }
    }

    private fun save(f: RescuePackageSetupUiFixture): File {
        val file = File(f.activity.cacheDir, "synthetic-out-" + java.util.UUID.randomUUID() + ".txt")
        Shadows.shadowOf(f.activity).receiveResult(choose(f), Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        f.await { !f.model.state.value.busy && status(f).contains("写入与回读一致") }
        assertTrue(file.readText(Charset.forName("GBK")).contains("测试人员"))
        return file
    }

    private fun choose(f: RescuePackageSetupUiFixture): Intent {
        f.await { f.activity.findViewById<Button>(R.id.exportText).isEnabled }
        f.activity.findViewById<Button>(R.id.exportText).performClick()
        return checkNotNull(Shadows.shadowOf(f.activity).nextStartedActivityForResult).intent.also {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, it.action)
            assertEquals("out.txt", it.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    private fun status(f: RescuePackageSetupUiFixture) = f.activity.findViewById<TextView>(R.id.textExportState).text.toString()
}
