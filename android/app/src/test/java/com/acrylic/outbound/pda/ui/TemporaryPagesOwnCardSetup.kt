package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.acrylic.outbound.pda.R
import java.io.File
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Online precondition uses the real own-card page; authority and documents are synthetic. */
internal class TemporaryPagesOwnCardSetup(private val base: TemporaryPagesSessionFixture) {
    private val controller = Robolectric.buildActivity(RescueCredentialActivity::class.java).setup()
    private val activity get() = controller.get()
    private val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()

    fun create(): File = try {
        base.await { activity.findViewById<TextView>(R.id.status).text.contains("未发现此人") }
        activity.findViewById<Button>(R.id.generateSetup).performClick()
        base.await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val card = File(activity.cacheDir, "synthetic-page-own-card-" + java.util.UUID.randomUUID() + ".json")
        choose(R.id.saveCard, card, Intent.ACTION_CREATE_DOCUMENT)
        choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        base.reply()
        dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isChecked = true
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        base.await { !dialog.isShowing }
        val request = base.request()
        assertEquals("GET", request.method); assertEquals("/api/v1/pda/devices/me", request.path)
        assertTrue(activity.findViewById<TextView>(R.id.status).text.contains("已预置"))
        card
    } finally { controller.pause().stop().destroy() }

    private fun choose(id: Int, file: File, action: String) {
        dialog.findViewById<Button>(id).performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(action, picker.action)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        base.await { dialog.findViewById<Button>(R.id.saveCard).isEnabled }
        assertTrue(dialog.findViewById<TextView>(R.id.setupStatus).text.contains("回读一致"))
    }
}
