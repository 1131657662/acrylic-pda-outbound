package com.acrylic.outbound.pda

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F
import com.acrylic.outbound.pda.IndependentClosureDeviceFixture as C

class IndependentClosureDeviceTest {
    @Test fun loggedOutOriginalCannotCreateANewClosure() {
        C.guard();assertNull(F.app.session.saved())
        val id = IndependentRescueDeviceFixture.id;val before = F.app.database.independentTasks().events(id)
        val intent = android.content.Intent(F.app,IndependentWorkActivity::class.java).putExtra("taskId",id)
        ActivityScenario.launch<IndependentWorkActivity>(intent).use {
            C.text(R.id.workPassword,F.password);F.click(R.id.workAuthorize)
            F.await { onView(withId(R.id.workBlocked)).check(matches(withText(containsString("请先登录")))) }
            onView(withId(R.id.workFinish)).check(matches(not(isEnabled())))
        }
        assertEquals(before,F.app.database.independentTasks().events(id))
        C.record("old-logout-refusal.json",JSONObject().put("originalEventsPreserved",true).put("newEndingCreated",false))
    }
    @Test fun originalDeviceObtainsNewCredentialThroughLiveProduct() {
        C.guard();val input = JSONObject(File(F.directory,"input.json").readText())
        F.app.session.login(input.getString("username"),input.getString("password"))
        androidx.work.WorkManager.getInstance(F.app).cancelAllWork().result.get()
        ActivityScenario.launch<IndependentCredentialActivity>(android.content.Intent(F.app,IndependentCredentialActivity::class.java)).use {
            F.await { onView(withId(R.id.provision)).check(matches(isEnabled())) }
            F.secret();F.click(R.id.provision);F.message("已保存签名独立身份")
            F.secret();F.click(R.id.verify);F.message("独立身份与可信期限已核验")
        }
        val entry=F.active();File(C.directory,"new-credential-receipt.json").writeText(entry.getString("receipt"))
        C.record("public-material.json",JSONObject().put("signingKey",entry.getJSONObject("trustedKey"))
            .put("devicePublicKey",F.app.session.publicKey()))
    }
    @Test fun offlineProductCapturesAndClosesItsNewTask() {
        C.guard();assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        createTask();captureAndClose()
        val body=C.ending();C.record("ending.json",body)
        assertEquals("2",body.getString("active"));assertEquals("1",body.getString("void"))
        assertFalse(body.getBoolean("businessProcessed"));assertFalse(body.getBoolean("localDeletionAllowed"))
    }
    private fun createTask() {
        ActivityScenario.launch<IndependentTaskActivity>(android.content.Intent(F.app,IndependentTaskActivity::class.java)).use {
            F.await { onView(withId(R.id.newTask)).check(matches(isEnabled())) };F.click(R.id.newTask)
            F.await { onView(withId(R.id.vehicle)).check(matches(isEnabled())) }
            C.text(R.id.vehicle,"E06-CLOSURE-TRUCK");C.text(R.id.reason,"Original offline collection for supervisor review")
            C.text(R.id.quantity,"4");F.click(R.id.add);F.secret();F.click(R.id.create);F.message("独立计划已保存")
        }
        File(C.directory,"task-id.txt").writeText(checkNotNull(C.repository.latestRequest()).id)
        assertEquals("PLANNED",C.repository.read(C.id).state.state)
    }
    private fun captureAndClose() {
        val bars=IndependentScanDeviceFixture.available(C.id)
        ActivityScenario.launch<IndependentWorkActivity>(C.intent()).use { scenario ->
            IndependentScanDeviceFixture.authorize();C.text(R.id.workManual,bars[0]);F.click(R.id.workValidate)
            F.await { onView(withId(R.id.workConfirm)).check(matches(isEnabled())) };F.click(R.id.workConfirm)
            IndependentScanDeviceFixture.count(C.id,1);C.ready();IndependentScanDeviceFixture.broadcast(bars[1])
            IndependentScanDeviceFixture.count(C.id,2);C.ready();voidAndRescan(bars[0]);F.click(R.id.workPause)
            F.await { onView(withId(R.id.workFinish)).check(matches(isEnabled())) }
            F.click(R.id.workFinishConfirmed);F.click(R.id.workFinish)
            F.await { onView(withId(R.id.workBlocked)).check(matches(withText(containsString("短装结束须记录原因")))) }
            assertEquals("SCANNING",C.repository.read(C.id).state.state)
            C.text(R.id.workPassword,F.password);F.click(R.id.workAuthorize);C.ready();F.click(R.id.workPause)
            C.text(R.id.workFinishReason,"Verified shortfall; supervisor physical reconciliation remains pending")
            F.click(R.id.workFinishConfirmed);F.click(R.id.workFinish);C.closed()
            onView(withId(R.id.workAuthorize)).check(matches(not(isEnabled())))
            scenario.onActivity { C.capture(it,"closed") }
        }
    }
    private fun voidAndRescan(barcode: String) {
        F.click(R.id.workPause);C.text(R.id.workLocateBarcode,barcode);F.click(R.id.workLocate)
        F.await { onView(withId(R.id.workVoid)).check(matches(isEnabled())) }
        C.text(R.id.workReason,"Original bale physically unloaded before rescan");F.click(R.id.workUnloaded);F.click(R.id.workVoid)
        IndependentScanDeviceFixture.count(C.id,1);F.click(R.id.workPause);C.ready();IndependentScanDeviceFixture.broadcast(barcode)
        IndependentScanDeviceFixture.count(C.id,2);C.ready()
    }
    @Test fun originalEndingSurvivesNewProcessAndKeepsEffectiveGuards() {
        C.guard();val body=File(C.directory,"ending.json").readText();val before=C.snapshot()
        ActivityScenario.launch<IndependentWorkActivity>(C.intent()).use { scenario ->
            F.await { onView(withId(R.id.workOriginal)).check(matches(withText(containsString("已结束封存")))) }
            onView(withId(R.id.workFinish)).check(matches(not(isEnabled())))
            onView(withId(R.id.workPause)).check(matches(not(isEnabled())))
            scenario.onActivity { C.capture(it,"new-process") }
        }
        assertEquals(body,C.ending().toString());assertEquals(before.events.map { it.toString() },C.snapshot().events.map { it.toString() })
        val active=before.scans.first { it.getString("status")=="ACTIVE" }
        assertThrows(Exception::class.java) { F.app.database.independentScans().release(active.getString("captureId")) }
        assertNotNull(F.app.database.independentScans().guard(active.getString("barcode")))
        C.record("restart.json",JSONObject().put("newProcess",true).put("originalEndingPreserved",true).put("effectiveGuardPreserved",true))
    }
    @Test fun sealedOriginalExportsThroughActualSaf() {
        C.guard();val before=C.ending().toString()
        val intent=android.content.Intent(F.app,RescueExportActivity::class.java).putExtra("source","INDEPENDENT").putExtra("localOrderId",C.id)
        ActivityScenario.launch<RescueExportActivity>(intent).use {
            IndependentRescueDeviceFixture.unlock();F.click(R.id.readSnapshot)
            F.await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            C.text(R.id.packagePassword,IndependentRescueDeviceFixture.packagePassword);F.click(R.id.generate)
            F.await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
            F.click(R.id.copy);File(C.directory,"ready-for-saf.txt").writeText("Actual CreateDocument chooser pending")
            F.await(110) { onView(withId(R.id.copyState)).check(matches(withText(containsString("目标写入与回读一致")))) }
            C.preservePackage()
        }
        assertEquals(before,C.ending().toString())
    }
    @Test fun actualClosureControlsRemainReachableWithLargeTextAndKeyboard() {
        C.guard();val before=C.ending().toString()
        ActivityScenario.launch<IndependentWorkActivity>(C.intent()).use { scenario ->
            F.await { onView(withId(R.id.workOriginal)).check(matches(withText(containsString("已结束封存")))) }
            onView(withId(R.id.workBack)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            onView(withId(R.id.workFinish)).check(matches(not(isEnabled())))
            scenario.onActivity { C.capture(it,"sealed-layout-${it.resources.configuration.fontScale}") }
        }
        val original=android.content.Intent(F.app,IndependentWorkActivity::class.java)
            .putExtra("taskId",IndependentRescueDeviceFixture.id)
        ActivityScenario.launch<IndependentWorkActivity>(original).use { scenario ->
            F.await { onView(withId(R.id.workFinishReason)).check(matches(isEnabled())) }
            onView(withId(R.id.workFinishReason)).perform(independentSkuScroll(),androidx.test.espresso.action.ViewActions.click())
            F.await { scenario.onActivity { activity ->
                assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
            } }
            scenario.onActivity { C.capture(it,"ending-keyboard-${it.resources.configuration.fontScale}") }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.workFinishConfirmed)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            onView(withId(R.id.workBack)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            scenario.onActivity { C.capture(it,"ending-footer-${it.resources.configuration.fontScale}") }
        }
        assertEquals(before,C.ending().toString())
    }
}
