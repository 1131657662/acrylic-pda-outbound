package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.sync.RecoveryProtection
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Real product flow/vault/signature; file chooser, HTTP authority and JCA keys are synthetic. */
internal class IndependentSetupUiFixture : AutoCloseable {
    val app = ApplicationProvider.getApplicationContext<RescueSignedFileFixture.TestApp>()
    private val provider = IndependentSetupJvmKeyStore.install()
    val authority = IndependentCredentialFixture()
    val server = MockWebServer()
    val vault = SessionVault(app)
    private val controller: org.robolectric.android.controller.ActivityController<IndependentCredentialActivity>
    val activity get() = controller.get()
    val model get() = ViewModelProvider(activity)[IndependentCredentialViewModel::class.java]
    val credentials get() = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()
    val status get() = dialog.findViewById<TextView>(R.id.setupStatus).text.toString()
    val credentialFile get() = File(app.noBackupFilesDir, "independent-credentials.enc")

    init {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 5)
        server.start()
        val public = DeviceKeys().publicKey(false)
        authority.binding = authority.device to JwtVerifier.sha(Base64.getDecoder().decode(public))
        app.session.settings.bind(server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(), authority.device, public)
        saveActor()
        controller = Robolectric.buildActivity(IndependentCredentialActivity::class.java).setup()
        await { !model.state.value.busy }
    }

    fun saveActor() {
        authority.session = authority.actor().put("timeAnchor", TrustedTime.anchor(authority.issued.toString(), TrustedTime.sample(app)))
        vault.save(authority.session)
    }
    fun reply(claims: JSONObject = authority.claims()) {
        val envelope = JSONObject().put("code", "OK").put("message", "synthetic").put("data", authority.receipt(claims))
            .put("requestId", "synthetic-independent-setup").put("serverTime", authority.issued.toString())
            .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL")
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(envelope.toString()))
    }
    fun request(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    fun open() {
        activity.findViewById<Button>(R.id.generateSetup).performClick()
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }
    fun choose(id: Int, file: File?, action: String) {
        dialog.findViewById<Button>(id).performClick()
        val picker = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(action, picker.action)
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        await { dialog.findViewById<Button>(R.id.saveCard).isEnabled }
    }
    fun save(): File {
        val file = File(activity.cacheDir, "synthetic-independent-" + java.util.UUID.randomUUID() + ".json")
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
        await { !dialog.isShowing || dialog.findViewById<Button>(R.id.saveCard).isEnabled }
        await { !model.state.value.busy }
    }
    fun await(condition: () -> Boolean) {
        repeat(500) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("synthetic independent setup flow did not settle")
    }
    override fun close() {
        controller.pause().stop().destroy()
        server.close(); vault.clear(); app.database.close(); IndependentSetupJvmKeyStore.remove(provider)
    }
}
