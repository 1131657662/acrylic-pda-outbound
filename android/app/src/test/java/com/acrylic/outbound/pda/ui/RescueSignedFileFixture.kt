package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.*
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Product Activity/ViewModel/credentials/vault execute unchanged; HTTP authority is synthetic. */
internal class RescueSignedFileFixture : AutoCloseable {
    class TestApp : PdaApplication() { override fun onCreate() = Unit }
    val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val provider = RescueFileJvmKeyStore.install()
    val authority = RescueSupervisorFixture()
    val server = MockWebServer()
    val vault = SessionVault(app)
    val digest: String
    val token: String
    private val controller: org.robolectric.android.controller.ActivityController<RescueExportActivity>
    val activity get() = controller.get()
    val model get() = ViewModelProvider(activity)[RescueExportViewModel::class.java]
    val credentials get() = RescueCredentials.shared(app, app.session)
    val credentialFile get() = File(app.noBackupFilesDir, "rescue-supervisor-credentials.enc")
    private var destroyed = false
    fun recreate() { controller.recreate(); await { !model.state.value.busy } }
    fun destroy() { if (!destroyed) { controller.pause().stop().destroy(); destroyed = true } }

    init {
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 1)
        server.start()
        val public = DeviceKeys().publicKey(false)
        digest = RescueBundle.sha(Base64.getDecoder().decode(public))
        app.session.settings.bind(server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString(), authority.device, public)
        saveActor()
        token = authority.token(authority.claims().put("deviceKeySha256", digest))
        controller = Robolectric.buildActivity(RescueExportActivity::class.java).setup()
        await { !model.state.value.busy }
    }

    fun saveActor(actor: String = authority.rescuer) {
        vault.save(authority.actor().put("userId", actor).put("signingKey", authority.trusted)
            .put("timeAnchor", TrustedTime.anchor(authority.now.toString(), TrustedTime.sample(app)))
            .put("serverRecoveryEpoch", "1").put("localSessionId", "synthetic-file-rescue"))
    }

    fun reply(original: String = token, time: Instant = authority.now) {
        val data = JSONObject().put("status", "AUTHORIZED").put("revocation", JSONObject.NULL)
            .put("authorization", JSONObject().put("signedAuthorization", original))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            JSONObject().put("code", "OK").put("message", "synthetic").put("data", data)
                .put("requestId", "synthetic-rescue-file").put("serverTime", time.toString())
                .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL").toString()))
    }

    fun select(material: String): File {
        val file = File(activity.cacheDir, "synthetic-" + java.util.UUID.randomUUID() + ".json").apply { writeText(material) }
        activity.findViewById<Button>(R.id.supervisorFileImport).performClick()
        val picker = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertEquals(material, dialog().findViewById<EditText>(R.id.material).text.toString())
        return file
    }

    fun confirm() {
        dialog().findViewById<EditText>(R.id.password).setText(String(authority.password))
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        await { !model.state.value.busy }
    }

    fun unlock() {
        activity.findViewById<EditText>(R.id.credential).setText(String(authority.password))
        activity.findViewById<Button>(R.id.unlock).performClick()
        await { !model.state.value.busy }
    }

    fun assertAuthorityGet() {
        val request = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/api/v1/pda/rescue-grants/" + authority.grantId, request.path)
        assertEquals("Bearer synthetic-must-not-persist", request.getHeader("Authorization"))
    }

    fun selected() = authority.selected().put("deviceKeySha256", digest)
    fun dialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
    fun await(condition: () -> Boolean) {
        repeat(500) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("signed file product flow did not settle")
    }

    override fun close() {
        destroy()
        server.close(); vault.clear(); app.database.close()
        RescueFileJvmKeyStore.remove(provider)
    }
}
