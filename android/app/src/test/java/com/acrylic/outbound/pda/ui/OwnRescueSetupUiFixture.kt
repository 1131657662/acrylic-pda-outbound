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

/** Shares the tested synthetic JCA/HTTP boundary, then opens the real own-setup Activity. */
internal class OwnRescueSetupUiFixture : AutoCloseable {
    val base = RescueSignedFileFixture()
    val controller = Robolectric.buildActivity(RescueCredentialActivity::class.java).setup()
    val activity get() = controller.get()
    val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()
    val credentialFile get() = File(base.app.noBackupFilesDir, "rescue-credentials.enc")
    val status get() = dialog.findViewById<TextView>(R.id.setupStatus).text.toString()
    init { base.await { activity.findViewById<TextView>(R.id.status).text.contains("未发现此人") } }

    fun open() {
        activity.findViewById<Button>(R.id.generateSetup).performClick()
        base.await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        base.await { !dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled }
    }

    fun choose(id: Int, file: File?, expectedAction: String) {
        dialog.findViewById<Button>(id).performClick()
        val picker = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(expectedAction, picker.action)
        val result = file?.let { Intent().setData(Uri.fromFile(it)) }
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, result)
        base.await { dialog.findViewById<Button>(R.id.saveCard).isEnabled }
    }

    fun save(): File {
        val target = File(activity.cacheDir, "synthetic-custody-" + java.util.UUID.randomUUID() + ".json")
        choose(R.id.saveCard, target, Intent.ACTION_CREATE_DOCUMENT)
        assertTrue(status.contains("目标写入及回读一致")); return target
    }

    fun verify(file: File) {
        choose(R.id.verifyCard, file, Intent.ACTION_OPEN_DOCUMENT)
        assertTrue(status.contains("逐字回读一致"))
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }

    fun confirm() {
        dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isChecked = true
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        base.await { !dialog.isShowing || dialog.findViewById<Button>(R.id.saveCard).isEnabled }
    }

    fun ownGrant(password: CharArray) = base.credentials.unlockChoice("own:" + base.authority.rescuer, password)
    override fun close() { controller.pause().stop().destroy(); base.close() }
}
