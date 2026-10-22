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
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Original task/capture/logout/export pages; synthetic JCA/file URI and loopback only. */
internal class FormalPagesExportFixture : AutoCloseable {
    val base = FormalPagesSessionFixture()
    val ownCard = FormalPagesOwnCardSetup(base).create()
    private var details: org.robolectric.android.controller.ActivityController<WorkDetailsActivity>? = null
    private var controller: org.robolectric.android.controller.ActivityController<RescueExportActivity>? = null
    val activity get() = checkNotNull(controller).get()
    val model get() = ViewModelProvider(activity)[RescueExportViewModel::class.java]
    val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()
    val packages get() = File(base.app.noBackupFilesDir, "rescue-exports")
        .listFiles { file -> file.extension == "pdarescue" }.orEmpty().toList()

    fun logout() {
        val settings = Robolectric.buildActivity(DeviceSettingsActivity::class.java).setup()
        try {
            val page = settings.get()
            base.await { page.findViewById<Button>(R.id.logout).isEnabled }
            page.findViewById<Button>(R.id.logout).performClick()
            val confirm = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(confirm.isShowing)
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            base.await { base.vault.read() == null && !page.findViewById<Button>(R.id.logout).isEnabled &&
                page.findViewById<Button>(R.id.retryIdentity).isEnabled &&
                page.findViewById<TextView>(R.id.identity).text.toString() == "当前未登录" }
            assertEquals("当前未登录", page.findViewById<TextView>(R.id.identity).text.toString())
            assertNull(base.vault.read())
        } finally { settings.pause().stop().destroy() }
    }

    fun openExport() {
        val intent = Intent(base.app, WorkDetailsActivity::class.java).putExtra("assignmentId", base.work.assignment)
        details = Robolectric.buildActivity(WorkDetailsActivity::class.java, intent).setup()
        val page = checkNotNull(details).get()
        base.await { page.findViewById<TextView>(R.id.status).text.contains("读取未完成") }
        assertNull(base.vault.read())
        assertEquals(0, page.findViewById<android.widget.LinearLayout>(R.id.rows).childCount)
        page.findViewById<Button>(R.id.rescueExport).performClick()
        val export = checkNotNull(Shadows.shadowOf(page).nextStartedActivity)
        assertEquals("FORMAL", export.getStringExtra("source"))
        assertEquals(base.work.assignment, export.getStringExtra("localOrderId"))
        controller = Robolectric.buildActivity(RescueExportActivity::class.java, export).setup()
        base.await { !model.state.value.busy && model.state.value.identities.isNotEmpty() }
        assertFalse(model.state.value.unlocked)
    }

    fun chooseOwnCard(file: File? = ownCard) {
        activity.findViewById<Button>(R.id.ownCardUnlock).performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action); receive(picker, file)
        base.await { !model.state.value.busy }
    }

    fun readSnapshot() {
        activity.findViewById<Button>(R.id.readSnapshot).performClick()
        base.await { !model.state.value.busy && model.state.value.canGenerate }
        assertEquals(base.work.assignment, model.state.value.orders[model.state.value.selected].target.localOrderId)
    }

    fun generate(): File {
        activity.findViewById<Button>(R.id.generateSecurePackage).performClick()
        base.await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val material = File(activity.cacheDir, "synthetic-pages-secret-" + java.util.UUID.randomUUID() + ".json")
        chooseMaterial(R.id.savePackageSecret, material, Intent.ACTION_CREATE_DOCUMENT)
        chooseMaterial(R.id.verifyPackageSecret, material, Intent.ACTION_OPEN_DOCUMENT)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.findViewById<CheckBox>(R.id.packageCustody).isChecked = true
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        base.await { !dialog.isShowing && !model.state.value.busy && model.state.value.canCopy }
        return material
    }

    private fun chooseMaterial(id: Int, file: File, action: String) {
        dialog.findViewById<Button>(id).performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(action, picker.action); receive(picker, file)
        base.await { dialog.findViewById<Button>(R.id.savePackageSecret).isEnabled }
        assertTrue(dialog.findViewById<TextView>(R.id.packageSetupStatus).text.contains("回读一致"))
    }

    fun copy(file: File?) {
        activity.findViewById<Button>(R.id.copy).performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action); receive(picker, file)
        base.await { !model.state.value.busy }
    }

    private fun receive(picker: Intent, file: File?) {
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
    }

    override fun close() {
        try { controller?.pause()?.stop()?.destroy(); details?.pause()?.stop()?.destroy() } finally { base.close() }
    }
}
