package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import com.acrylic.outbound.pda.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RescueSupervisorImportDialogTest {
    @Test fun importedMaterialRemainsUntrustedUntilExplicitConfirmationAndCancelClearsViews() = activity { activity ->
        var imports = 0
        RescueSupervisorImportDialog.show(activity, { _, password -> password.fill('\u0000'); imports++ }, "synthetic.original")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val material = dialog.findViewById<EditText>(R.id.material)
        val password = dialog.findViewById<EditText>(R.id.password)
        password.setText("synthetic-only-password")
        assertEquals("synthetic.original", material.text.toString()); assertEquals(0, imports)
        assertFalse(material.isSaveEnabled); assertFalse(password.isSaveEnabled)
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, imports); assertTrue(material.text.isEmpty()); assertTrue(password.text.isEmpty())
    }

    @Test fun confirmationCallsExistingImporterOnceWithExactOriginal() = activity { activity ->
        var calls = 0; var received = ""
        val original = "  synthetic.original.signature\r\n"
        RescueSupervisorImportDialog.show(activity, { text, password ->
            calls++; received = text; assertEquals("synthetic-only-password", String(password)); password.fill('\u0000')
        }, original)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.findViewById<EditText>(R.id.password).setText("synthetic-only-password")
        val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE); confirm.performClick(); confirm.performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, calls); assertEquals(original, received)
        assertTrue(dialog.findViewById<EditText>(R.id.material).text.isEmpty())
    }

    private fun activity(test: (ComponentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try { test(controller.get()) } finally { controller.pause().stop().destroy() }
    }
}
