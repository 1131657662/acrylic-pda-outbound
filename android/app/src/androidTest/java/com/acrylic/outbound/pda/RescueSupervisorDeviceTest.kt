package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual original emulator Keystore + HTTP authority; native signature setup is a test consumer. */
class RescueSupervisorDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "rescue-supervisor").apply { mkdirs() }
    private val input get() = JSONObject(File(directory, "private/input.json").readText())
    private val password = "synthetic-supervisor-local-password"
    private val packagePassword = "synthetic-supervisor-package-password"
    private fun credentials() = RescueCredentials.shared(app, app.session)
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("rescueSupervisor") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    @Test fun nativeOriginalRequestHasActualDeviceSignatureAndExactScope() {
        guard()
        val original = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        app.session.login(original.getString("username"), original.getString("password"))
        val session = app.session.requireSession(); val key = UUID.randomUUID().toString()
        val order = JSONObject(File(app.filesDir, "scan-adjustment/temporary-result.json").readText()).getString("clientOrderId")
        val text = JSONObject().put("purpose", "PDA_RESCUE_REQUEST_V1").put("requestKey", key)
            .put("deviceId", session.getString("deviceId")).put("operatorId", session.getString("userId"))
            .put("warehouseId", session.getString("warehouseId")).put("rescuerId", input.getString("rescuerId"))
            .put("orderScope", JSONArray().put(JSONObject().put("source", "TEMP").put("localOrderId", order)))
            .put("reason", "Isolated native Keystore supervisor read test").toString()
        val body = JSONObject().put("originalText", text).put("signature", DeviceKeys().sign("PDA_RESCUE_REQUEST_V1\n$text"))
        File(directory, "native-request.json").writeText(body.toString())
        val result = app.session.call("pda/rescue-requests", body, session.getString("serverRecoveryEpoch"),
            extraHeaders = mapOf("Idempotency-Key" to key))
        assertEquals(result.toString(), app.session.call("pda/rescue-requests", body, session.getString("serverRecoveryEpoch"),
            extraHeaders = mapOf("Idempotency-Key" to key)).toString())
        File(directory, "request-result.json").writeText(result.put("localOrderId", order).put("nativeKeystore", true).toString())
        app.session.logout()
    }
    @Test fun authenticatedRescuerProvisionsThroughActualImportDialogAndLogsOut() {
        guard(); val auth = input
        app.session.login(auth.getString("username"), auth.getString("password"))
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use {
            importMaterial(auth.getString("authorization"), password)
            await { onView(withId(R.id.status)).check(matches(withText(containsString("主管签名只读范围已预置")))) }
        }
        val choice = credentials().choices().single { it.id.startsWith("supervisor:") }
        assertEquals(auth.getString("grantId"), choice.id.removePrefix("supervisor:"))
        assertNotEquals(app.session.requireSession().getString("userId"), choice.identity.operatorId)
        app.session.logout(); assertNull(app.session.saved())
        File(directory, "provision-result.json").writeText(JSONObject().put("originalIdentity", choice.identity.operatorId)
            .put("grantId", auth.getString("grantId")).put("nativeKeystore", true).put("productDialog", true).put("loggedOut", true).toString())
    }
    @Test fun stoppedServiceNewProcessExportsOnlyTheAuthorizedOriginalOrder() {
        guard(); assertNull(app.session.saved())
        val auth = input; val id = auth.getString("localOrderId")
        val original = checkNotNull(app.database.temporarySubmissions().forOrder(id))
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use { scenario ->
            await { onView(withId(R.id.unlock)).check(matches(isEnabled())) }
            scenario.onActivity { activity -> activity.findViewById<Spinner>(R.id.identity).setSelection(
                credentials().choices().indexOfFirst { it.id == "supervisor:${auth.getString("grantId")}" }) }
            onView(withId(R.id.credential)).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.unlock)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("只读范围核验通过")))) }
            onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.packagePassword)).perform(scrollTo(), replaceText(packagePassword), closeSoftKeyboard())
            onView(withId(R.id.generate)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
            preserve(auth)
        }
        assertEquals(original, app.database.temporarySubmissions().forOrder(id)); assertNull(app.session.saved())
    }
    @Test fun offlineRevocationDialogDurablyDisablesTheAlreadyProvisionedGrant() {
        guard(); assertNull(app.session.saved())
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use {
            importMaterial(input.getString("revocation"), "")
            await { onView(withId(R.id.status)).check(matches(withText(containsString("主管撤销签名已核验并保存")))) }
        }
        assertThrows(IllegalStateException::class.java) { credentials().unlockChoice("supervisor:${input.getString("grantId")}", password.toCharArray()) }
        File(directory, "revoke-result.json").writeText(JSONObject().put("signedRevocationPersisted", true)
            .put("loggedOut", true).put("localDeletionAllowed", false).toString())
    }
    @Test fun newProcessCannotRestoreRevokedAuthority() {
        guard(); assertNull(app.session.saved())
        assertThrows(IllegalStateException::class.java) { credentials().unlockChoice("supervisor:${input.getString("grantId")}", password.toCharArray()) }
        assertTrue(credentials().choices().none { it.id == "supervisor:${input.getString("grantId")}" })
        File(directory, "restart-revoke-result.json").writeText(JSONObject().put("revocationSurvivedNewProcess", true).toString())
    }
    private fun importMaterial(text: String, localPassword: String) {
        await { onView(withId(R.id.supervisorImport)).check(matches(isEnabled())) }
        onView(withId(R.id.supervisorImport)).perform(scrollTo(), click())
        onView(withId(R.id.material)).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
        onView(withId(R.id.password)).perform(scrollTo(), replaceText(localPassword), closeSoftKeyboard())
        onView(withText("核验并保存")).perform(click())
    }
    private fun preserve(auth: JSONObject) {
        val access = credentials().unlockChoice("supervisor:${auth.getString("grantId")}", password.toCharArray())
        val orders = RescueOrders(app.database).page(access).rows
        assertEquals(listOf(RescueTarget("TEMP", auth.getString("localOrderId"))), orders.map { it.target })
        val files = RescueExportFiles(app); val generated = files.recover(files.available(access).first().id, access)
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "export-result.json").writeText(JSONObject().put("packageId", generated.id).put("sha256", generated.sha256)
            .put("originalOperator", access.identity.operatorId).put("rescuer", auth.getString("rescuerId"))
            .put("newProcess", true).put("serviceStopped", true).put("localDeletionAllowed", false).toString())
    }
    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(150) } while (System.nanoTime() < end)
        throw AssertionError("Supervisor rescue product did not reach expected state", last)
    }
}
