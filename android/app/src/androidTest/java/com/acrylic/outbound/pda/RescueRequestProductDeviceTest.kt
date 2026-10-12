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
import com.acrylic.outbound.pda.ui.RescueRequestActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

/** Native encrypted journal/actual UI plus isolated HTTP; original Keystore and Room retained. */
class RescueRequestProductDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "rescue-request-product").apply { mkdirs() }
    private val input get() = JSONObject(File(app.filesDir, "rescue-supervisor/private/input.json").readText())
    private fun repository() = RescueRequestRepository.shared(app)
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("rescueRequestProduct") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    private fun loginOriginal() {
        val original = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        app.session.login(original.getString("username"), original.getString("password"))
    }
    private fun target() = JSONObject(File(app.filesDir, "scan-adjustment/temporary-result.json").readText()).getString("clientOrderId")
    @Test fun actualExportPageHasOriginalPersonRequestEntry() {
        guard(); loginOriginal()
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use {
            await { onView(withId(R.id.supervisorRequest)).check(matches(isEnabled())) }
            onView(withId(R.id.supervisorRequest)).perform(scrollTo(), click())
            await { onView(withId(R.id.prepare)).check(matches(isEnabled())) }
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }
    @Test fun productDurablyPreparesOriginalDeviceRequestWithoutSending() {
        guard(); loginOriginal()
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use { scenario ->
            await { onView(withId(R.id.prepare)).check(matches(isEnabled())) }
            val index = repository().ownOrders().rows.indexOfFirst { it.target == RescueTarget("TEMP", target()) }
            check(index >= 0)
            scenario.onActivity { it.findViewById<Spinner>(R.id.order).setSelection(index) }
            onView(withId(R.id.rescuer)).perform(scrollTo(), replaceText(input.getString("rescuerId")), closeSoftKeyboard())
            onView(withId(R.id.reason)).perform(scrollTo(), replaceText("Isolated actual product durable read request"), closeSoftKeyboard())
            onView(withId(R.id.prepare)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("申请原件已保存")))) }
        }
        val original = repository().list().single { it.document.getString("rescuerId") == input.getString("rescuerId") }
        val session = app.session.requireSession()
        RescueRequestDocument.verify(original, RescueReadIdentity(session.getString("deviceId"), session.getString("userId"), session.getString("warehouseId")),
            Base64.getDecoder().decode(DeviceKeys().publicKey(false)))
        assertNull(original.receipt); assertEquals(RescueTarget("TEMP", target()), original.target)
        File(directory, "prepared-original.json").writeText(original.body.put("requestKey", original.key).put("epoch", original.epoch).toString())
    }
    @Test fun newProcessProductSubmitsPersistedOriginalAndSavesReceipt() {
        guard(); val before = original()
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use {
            await { onView(withId(R.id.send)).check(matches(isEnabled())) }
            onView(withId(R.id.send)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("服务端申请回执已核验")))) }
        }
        val after = original(); assertEquals(before.originalText, after.originalText); assertEquals(before.signature, after.signature)
        assertEquals(before.key, after.key); assertEquals(before.epoch, after.epoch)
        val receipt = JSONObject(checkNotNull(after.receipt)).put("localOrderId", after.target.localOrderId)
            .put("nativeKeystore", true).put("productPage", true)
        File(directory, "request-result.json").writeText(receipt.toString())
    }
    @Test fun nextProcessProductRetriesSameReceiptAndQueriesOriginal() {
        guard(); val before = original(); val first = checkNotNull(before.receipt)
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use {
            await { onView(withId(R.id.send)).check(matches(isEnabled())) }
            onView(withId(R.id.send)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("服务端申请回执已核验")))) }
            onView(withId(R.id.refresh)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("原申请材料已核验保存")))) }
        }
        val after = original(); assertEquals(first, after.receipt)
        assertEquals(before.body.toString(), after.body.toString())
        assertEquals("REQUESTED", JSONObject(checkNotNull(after.material)).getString("status"))
        File(directory, "retry-result.json").writeText(JSONObject().put("originalKeyReused", true).put("newProcess", true).toString())
    }
    @Test fun productReadsActualSignedApprovalWithoutGrantingNewWorkRights() {
        guard(); val before = original()
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use {
            await { onView(withId(R.id.refresh)).check(matches(isEnabled())) }
            onView(withId(R.id.refresh)).perform(scrollTo(), click())
            await { onView(withId(R.id.detail)).check(matches(withText(containsString("最后取得状态 AUTHORIZED")))) }
            onView(withId(R.id.showMaterial)).perform(scrollTo(), click())
            await { onView(withId(R.id.detail)).check(matches(withText(containsString("signedAuthorization")))) }
        }
        val after = original(); assertEquals(before.body.toString(), after.body.toString())
        val material = JSONObject(checkNotNull(after.material)); assertFalse(material.getBoolean("localDeletionAllowed"))
        assertEquals(input.getString("authorization"), material.getJSONObject("authorization").getString("signedAuthorization"))
        File(directory, "approval-result.json").writeText(JSONObject().put("signedMaterialVerified", true).put("originalUnchanged", true).toString())
    }
    @Test fun switchedPersonCannotListOrSendOriginalJournal() {
        guard(); val before = original(); val auth = input
        app.session.logout(); app.session.login(auth.getString("username"), auth.getString("password"))
        assertTrue(repository().list().isEmpty())
        assertThrows(IllegalStateException::class.java) { repository().send(before.key) }
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本人本机原件已读取")))) }
            onView(withId(R.id.send)).check(matches(isNotEnabled()))
            onView(withId(R.id.detail)).check(matches(withText("")))
        }
        app.session.logout(); loginOriginal()
        assertEquals(before, original())
        File(directory, "identity-result.json").writeText(JSONObject().put("otherPersonDenied", true).put("originalUnchanged", true).toString())
    }
    @Test fun stoppedServiceProductRetainsOriginalReceiptAndLastKnownMaterial() {
        guard(); val before = original()
        ActivityScenario.launch<RescueRequestActivity>(Intent(app, RescueRequestActivity::class.java)).use {
            await { onView(withId(R.id.refresh)).check(matches(isEnabled())) }
            onView(withId(R.id.refresh)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("原申请与作业记录保留")))) }
        }
        assertEquals(before, original())
        File(directory, "offline-result.json").writeText(JSONObject().put("offlineFailureRetainedOriginal", true).put("newProcess", true).toString())
    }
    private fun original(): RescueRequestIntent {
        val prepared = JSONObject(File(directory, "prepared-original.json").readText())
        return repository().list().single { it.key == prepared.getString("requestKey") }.also {
            assertEquals(prepared.getString("originalText"), it.originalText); assertEquals(prepared.getString("signature"), it.signature)
        }
    }
    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(150) } while (System.nanoTime() < end)
        throw AssertionError("Original request product did not reach expected state", last)
    }
}
