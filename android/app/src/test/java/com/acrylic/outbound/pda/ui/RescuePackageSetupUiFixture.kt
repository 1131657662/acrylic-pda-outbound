package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.work.WorkFixture
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Original signed nonempty Room input, real vault/grant, product Activity. Synthetic JCA/HTTP only. */
internal class RescuePackageSetupUiFixture : AutoCloseable {
    init { resetFactory() }
    private val directory = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "synthetic-package-input-" + java.util.UUID.randomUUID()).apply { mkdirs() }
    val app = ApplicationProvider.getApplicationContext<RescueSignedFileFixture.TestApp>()
    private val provider = RescueFileJvmKeyStore.install()
    val work = runBlocking(Dispatchers.IO) { WorkFixture(directory).also { it.scan() } }
    val server = okhttp3.mockwebserver.MockWebServer()
    val vault = SessionVault(app)
    val credentials get() = RescueCredentials.shared(app, app.session)
    private lateinit var controller: org.robolectric.android.controller.ActivityController<RescueExportActivity>
    val activity get() = controller.get()
    val model get() = ViewModelProvider(activity)[RescueExportViewModel::class.java]
    val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()
    val status get() = dialog.findViewById<TextView>(R.id.packageSetupStatus).text.toString()
    val packageDirectory get() = File(app.noBackupFilesDir, "rescue-exports")
    init { try { prepare() } catch (error: Throwable) { close(); throw error } }
    private fun prepare() {
        android.provider.Settings.Global.putInt(app.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 1)
        server.start(); val public = DeviceKeys().publicKey(false)
        app.session.settings.bind(server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(),
            work.session.getString("deviceId"), public)
        vault.save(JSONObject(work.session.toString()).put("signingKey", work.input.key)
            .put("accessToken", "synthetic-package-setup-token").put("localSessionId", "synthetic-package-setup-session")
            .put("timeAnchor", TrustedTime.anchor(work.now.toString(), TrustedTime.sample(app))))
        val response = JSONObject().put("code", "OK").put("data", JSONObject()).put("message", "synthetic")
            .put("requestId", "synthetic-package-setup").put("serverTime", work.now.toString())
            .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL")
        server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody(response.toString()))
        runBlocking(Dispatchers.IO) { credentials.provisionOwn("synthetic-package-rescue-password".toCharArray()) }
        assertEquals("GET", server.takeRequest().method)
        controller = org.robolectric.Robolectric.buildActivity(RescueExportActivity::class.java).setup()
        await { !model.state.value.busy && model.state.value.identities.isNotEmpty() }
        model.unlock(0, "synthetic-package-rescue-password".toCharArray())
        await { !model.state.value.busy }; assertTrue(model.state.value.unlocked)
        assertEquals(work.assignment, model.state.value.orders.single().target.localOrderId)
        activity.findViewById<Button>(R.id.readSnapshot).performClick()
        await { !model.state.value.busy && model.state.value.canGenerate }
    }
    fun await(condition: () -> Boolean) {
        repeat(500) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("synthetic package setup did not settle")
    }
    fun recreate() { controller.recreate(); await { !model.state.value.busy } }
    fun open() {
        activity.findViewById<Button>(R.id.generateSecurePackage).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    fun begin(id: Int): Intent {
        dialog.findViewById<Button>(id).performClick()
        return checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
    }
    fun receive(intent: Intent, file: File?) {
        Shadows.shadowOf(activity).receiveResult(intent, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        await { !dialog.isShowing || dialog.findViewById<Button>(R.id.savePackageSecret).isEnabled }
    }
    fun save(): File {
        val file = File(activity.cacheDir, "synthetic-package-material-" + java.util.UUID.randomUUID() + ".json")
        val intent = begin(R.id.savePackageSecret); assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action); receive(intent, file)
        assertTrue(status.contains("写入与回读一致")); return file
    }
    fun verify(file: File) {
        val intent = begin(R.id.verifyPackageSecret); assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action); receive(intent, file)
        assertTrue(status.contains("逐字回读一致"))
    }
    fun confirm() {
        dialog.findViewById<CheckBox>(R.id.packageCustody).isChecked = true
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { !dialog.isShowing || dialog.findViewById<Button>(R.id.savePackageSecret).isEnabled }
    }
    fun packages() = packageDirectory.listFiles { file -> file.extension == "pdarescue" }.orEmpty().toList()
    fun revoke() = credentials.revokeOwn()
    override fun close() {
        try {
            if (::controller.isInitialized) controller.pause().stop().destroy()
            server.close(); vault.clear(); app.database.close(); work.close()
        } finally { RescueFileJvmKeyStore.remove(provider); resetFactory() }
    }
    private fun resetFactory() = org.robolectric.util.ReflectionHelpers.setStaticField(
        ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
}
