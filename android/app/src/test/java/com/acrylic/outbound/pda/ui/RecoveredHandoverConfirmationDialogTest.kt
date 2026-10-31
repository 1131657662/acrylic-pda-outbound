package com.acrylic.outbound.pda.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.ComponentActivity
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class RecoveredHandoverConfirmationDialogTest {
    @Test fun cancelLongChecklistCreatesNoReceivingIntent() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup(); var confirmed = 0
        try {
            RecoveredHandoverConfirmationDialog.show(controller.get(), (1..80).joinToString("\n") { "保留包 $it" }) { confirmed++ }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.window!!.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            button(dialog.window!!.decorView, "返回核对").performClick()
            assertEquals(0, confirmed); assertFalse(dialog.isShowing)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun explicitConfirmationRunsOnceAndDismissesChecklist() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup(); var confirmed = 0
        try {
            RecoveredHandoverConfirmationDialog.show(controller.get(), "原主管保留清单") { confirmed++ }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            button(dialog.window!!.decorView, "已核对，确认接收").performClick()
            assertEquals(1, confirmed); assertFalse(dialog.isShowing)
        } finally { controller.pause().stop().destroy() }
    }
    private fun button(root: View, text: String): Button = descendants(root).filterIsInstance<Button>().single { it.text == text }
    private fun descendants(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()
}
