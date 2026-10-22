package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.CheckBox
import androidx.lifecycle.ViewModelProvider
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.RescueSupervisorFixture
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowAlertDialog

/** Synthetic authority/session; original signed-file and generated-card product UI. */
internal class SupervisorPagesCardSetup(private val f: TemporaryPagesExportFixture) {
    val authority = RescueSupervisorFixture()
    val actor: JSONObject = JSONObject(checkNotNull(f.base.work.fixture.auth).toString())
    val claims: JSONObject = authority.claims().put("deviceId", actor.getString("deviceId"))
        .put("operatorId", actor.getString("userId")).put("warehouseId", actor.getString("warehouseId"))
        .put("deviceKeySha256", f.base.digest).put("issuedAt", f.base.work.fixture.time.toString())
        .put("orderScope", JSONArray().put(JSONObject().put("source", "TEMP").put("localOrderId", f.base.work.id)))
    val token = authority.token(claims)
    private val controller = Robolectric.buildActivity(RescueExportActivity::class.java)
    private val activity get() = controller.get()
    private val dialog: AlertDialog get() = ShadowAlertDialog.getLatestAlertDialog()

    fun create(): File {
        val session = JSONObject(f.base.app.session.requireSession().toString())
            .put("userId", authority.rescuer).put("signingKey", authority.trusted)
        f.base.vault.save(session); controller.setup()
        try {
            val model = ViewModelProvider(activity)[RescueExportViewModel::class.java]
            f.base.await { !model.state.value.busy }
            importOriginal()
            val card = File(activity.cacheDir, "synthetic-supervisor-pages-" + java.util.UUID.randomUUID() + ".json")
            choose(R.id.saveCard, card, Intent.ACTION_CREATE_DOCUMENT)
            choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            reply(); dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isChecked = true
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            f.base.await { !dialog.isShowing && !model.state.value.busy }
            checkRequest(card); return card
        } finally { controller.pause().stop().destroy() }
    }

    private fun importOriginal() {
        val material = File(activity.cacheDir, "synthetic-authorization-" + java.util.UUID.randomUUID() + ".json")
            .apply { writeText(token) }
        activity.findViewById<Button>(R.id.supervisorFileImport).performClick()
        receive(material, Intent.ACTION_OPEN_DOCUMENT)
        f.base.await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        dialog.findViewById<Button>(R.id.generateSupervisorSetup).performClick()
        f.base.await { ShadowAlertDialog.getLatestAlertDialog()?.findViewById<Button>(R.id.saveCard) != null }
        assertEquals(token, material.readText())
    }

    private fun choose(id: Int, file: File, action: String) {
        dialog.findViewById<Button>(id).performClick(); receive(file, action)
        f.base.await { dialog.findViewById<Button>(R.id.saveCard).isEnabled }
        assertTrue(dialog.findViewById<android.widget.TextView>(R.id.setupStatus).text.contains("回读一致"))
    }

    private fun receive(file: File, action: String) {
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(action, picker.action)
        Shadows.shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
    }

    private fun reply() {
        val data = JSONObject().put("status", "AUTHORIZED").put("revocation", JSONObject.NULL)
            .put("authorization", JSONObject().put("signedAuthorization", token))
        f.base.server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("code", "OK").put("message", "synthetic").put("data", data)
                .put("requestId", "synthetic-supervisor-pages").put("serverTime", f.base.work.fixture.time.toString())
                .put("serverRecoveryEpoch", actor.getString("serverRecoveryEpoch")).put("serviceMode", "NORMAL").toString()))
    }

    private fun checkRequest(card: File) {
        val request = f.base.request()
        assertEquals("GET", request.method); assertEquals(0L, request.bodySize)
        assertEquals("/api/v1/pda/rescue-grants/" + authority.grantId, request.path)
        assertFalse(request.headers.toString().contains(JSONObject(card.readText()).getString("credential")))
    }
}
