package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSupervisorFileActivityTest.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueSupervisorFileActivityTest {
    class TestApp : PdaApplication() { override fun onCreate() = Unit }

    @Test fun productButtonSelectsOriginalFileAndCancelDoesNotImportOrPersistMaterial() = activity { activity ->
        val original = "  synthetic.untrusted.material\r\n"
        val file = File(activity.cacheDir, "synthetic-supervisor.txt").apply { writeText(original) }
        val before = privateFiles(activity)
        val picker = select(activity)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(original, dialog.findViewById<EditText>(R.id.material).text.toString())
        assertEquals(before, privateFiles(activity)); assertEquals(original, file.readText())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle()
        assertEquals(before, privateFiles(activity)); assertFalse(model(activity).state.value.unlocked)
    }

    @Test fun cancelledPickerDoesNotOpenConfirmation() = activity { activity ->
        val before = privateFiles(activity); val picker = select(activity)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_CANCELED, null); idle()
        assertTrue(ShadowAlertDialog.getLatestAlertDialog()?.isShowing != true)
        assertEquals(before, privateFiles(activity)); assertFalse(model(activity).state.value.unlocked)
    }

    @Test fun confirmingUntrustedFileUsesOriginalVerifierAndCannotGrantAccess() = activity { activity ->
        val file = File(activity.cacheDir, "synthetic-untrusted.txt").apply { writeText("untrusted-material") }
        val before = privateFiles(activity); val picker = select(activity)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { !model(activity).state.value.busy && model(activity).state.value.message.contains("主管签名格式错误") }
        assertFalse(model(activity).state.value.unlocked); assertEquals(before, privateFiles(activity))
        assertEquals("untrusted-material", file.readText())
    }

    @Test fun invalidFileShowsSafeFailureWithoutImportingOrLeakingContent() = activity { activity ->
        val file = File(activity.cacheDir, "synthetic-invalid.txt").apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        val before = privateFiles(activity); val picker = select(activity)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        await { activity.findViewById<TextView>(R.id.status).text.contains("尚未导入") }
        assertTrue(ShadowAlertDialog.getLatestAlertDialog()?.isShowing != true)
        assertEquals(before, privateFiles(activity)); assertFalse(model(activity).state.value.unlocked)
    }

    private fun select(activity: RescueExportActivity): Intent {
        activity.findViewById<Button>(R.id.supervisorFileImport).performClick()
        return Shadows.shadowOf(activity).nextStartedActivityForResult.intent
    }

    private fun privateFiles(activity: RescueExportActivity) = activity.noBackupFilesDir.walkTopDown()
        .filter { it.isFile }.associate { it.relativeTo(activity.noBackupFilesDir).path to it.readBytes().toList() }

    private fun model(activity: RescueExportActivity) = ViewModelProvider(activity)[RescueExportViewModel::class.java]
    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()
    private fun await(condition: () -> Boolean) {
        repeat(500) { idle(); if (condition()) return; Thread.sleep(10) }
        fail("product UI did not settle")
    }

    private fun activity(test: (RescueExportActivity) -> Unit) {
        val controller = Robolectric.buildActivity(RescueExportActivity::class.java).setup()
        try { await { !model(controller.get()).state.value.busy }; test(controller.get()) }
        finally { controller.pause().stop().destroy(); ApplicationProvider.getApplicationContext<TestApp>().database.close() }
    }
}
