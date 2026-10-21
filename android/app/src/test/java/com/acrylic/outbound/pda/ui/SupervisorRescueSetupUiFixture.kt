package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.acrylic.outbound.pda.R
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Synthetic HTTP/JCA only; generated setup uses the real product Activity and vault. */
internal class SupervisorRescueSetupUiFixture : AutoCloseable {
    init { resetFactory() }
    val base = RescueSignedFileFixture()
    val activity get() = base.activity
    val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()
    val status get() = dialog.findViewById<TextView>(R.id.setupStatus).text.toString()
    fun open(material: String = base.token) {
        base.select(material)
        dialog.findViewById<Button>(R.id.generateSupervisorSetup).performClick()
        base.await { ShadowAlertDialog.getLatestAlertDialog()?.findViewById<Button>(R.id.saveCard) != null }
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    fun choose(id: Int, file: File?, action: String) {
        dialog.findViewById<Button>(id).performClick()
        val intent = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(action, intent.action)
        Shadows.shadowOf(activity).receiveResult(intent, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        base.await { dialog.findViewById<Button>(R.id.saveCard).isEnabled }
    }
    fun save(): File {
        val file = File(activity.cacheDir, "synthetic-supervisor-card-" + java.util.UUID.randomUUID() + ".json")
        choose(R.id.saveCard, file, Intent.ACTION_CREATE_DOCUMENT)
        assertTrue(status.contains("目标写入及回读一致")); return file
    }
    fun verify(file: File) {
        choose(R.id.verifyCard, file, Intent.ACTION_OPEN_DOCUMENT)
        assertTrue(status.contains("逐字回读一致")); assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    fun confirm() {
        dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isChecked = true
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        base.await { !dialog.isShowing || dialog.findViewById<Button>(R.id.saveCard).isEnabled }
        base.await { !base.model.state.value.busy }
    }
    fun password(file: File) = JSONObject(file.readText()).getString("credential").toCharArray()
    fun unlock(file: File) {
        activity.findViewById<EditText>(R.id.credential).setText(String(password(file)))
        activity.findViewById<Button>(R.id.unlock).performClick(); base.await { !base.model.state.value.busy }
    }
    override fun close() { try { base.close() } finally { resetFactory() } }
    private fun resetFactory() {
        org.robolectric.util.ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }
}
