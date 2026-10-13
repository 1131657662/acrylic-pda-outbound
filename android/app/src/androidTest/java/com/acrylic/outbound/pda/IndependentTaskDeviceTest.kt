package com.acrylic.outbound.pda

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentTaskActivity
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

class IndependentTaskDeviceTest {
    private val directory get() = File(F.app.filesDir, "independent-task-device").apply { mkdirs() }
    private val repository get() = IndependentTaskRepository(F.app.database, { TrustedTime.sample(F.app) }) {
        val session = F.app.session.requireSession()
        IndependentTaskActor(session.getString("userId"), session.getString("deviceId"), session.getString("warehouseId"))
    }
    private fun guard() {
        F.guard(); check(InstrumentationRegistry.getArguments().getString("independentTask") == "true")
    }
    private fun intent() = Intent(F.app, IndependentTaskActivity::class.java)
    @Test fun createPlanThroughProductPageWhileActuallyOffline() {
        guard(); assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        val products = repository.activeProducts(); val before = F.app.database.independentTasks().count()
        ActivityScenario.launch<IndependentTaskActivity>(intent()).use { scenario ->
            F.await { onView(withId(R.id.newTask)).check(matches(isEnabled())) }
            F.click(R.id.newTask)
            F.await { onView(withId(R.id.vehicle)).check(matches(isEnabled())) }
            text(R.id.vehicle, "E06-ORIGINAL-TRUCK"); text(R.id.reason, "Actual offline independent collection")
            text(R.id.quantity, "2"); F.click(R.id.add)
            F.secret(); F.click(R.id.create); F.message("独立计划已保存")
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                IndependentSkuDeviceEvidence.capture(activity, directory, "created-plan")
            }
        }
        val request = checkNotNull(repository.latestRequest()); val task = repository.read(request.id)
        assertEquals(before + 1, F.app.database.independentTasks().count())
        assertEquals(products.first.id, task.task.importId); assertEquals(products.first.sha256, task.task.skuSha256)
        assertEquals(products.second.first().deliveryCode, task.lines.single().deliveryCode)
        assertEquals(2L, task.lines.single().plannedCount); assertEquals(0L, task.state.actualCount)
        assertEquals("PLANNED", task.state.state); assertEquals(1, task.events.size)
        File(directory, "original-task-id.txt").writeText(request.id)
        File(directory, "original-task.json").writeText(JSONObject().put("taskId", request.id).put("commandSha256", task.task.commandSha256)
            .put("signedCredential", task.task.signedCredential).put("trustedKey", JSONObject(task.task.trustedKeyJson))
            .put("skuSha256", task.task.skuSha256).put("importId", task.task.importId).put("planned", task.task.plannedTotal.toString())
            .put("nativeKeystore", true).put("actualProductPage", true).put("networkDisabled", true).toString())
    }
    @Test fun originalRequestPlanAndAuditSurviveRealNewProcess() {
        guard(); assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        val id = File(directory, "original-task-id.txt").readText(); val original = JSONObject(File(directory, "original-task.json").readText())
        ActivityScenario.launch<IndependentTaskActivity>(intent()).use {
            F.message("独立计划已保存")
            onView(withId(R.id.vehicle)).check(matches(withText("E06-ORIGINAL-TRUCK"))).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            F.click(R.id.reload); F.message("独立计划已保存")
        }
        val task = repository.read(id)
        assertEquals(original.getString("commandSha256"), task.task.commandSha256)
        assertEquals(original.getString("signedCredential"), task.task.signedCredential)
        assertEquals(1, task.events.size); assertEquals(0L, task.state.actualCount)
        File(directory, "new-process.json").writeText(JSONObject().put("newProcess", true).put("originalRequest", true)
            .put("originalPlanAndSignedCredential", true).put("noRepeatedTask", true).toString())
    }
    @Test fun actualLayoutAllowsTaskInputLargeTextAndKeyboard() {
        guard()
        ActivityScenario.launch<IndependentTaskActivity>(intent()).use { scenario ->
            F.await { onView(withId(R.id.newTask)).check(matches(isEnabled())) }; F.click(R.id.newTask)
            F.await { onView(withId(R.id.vehicle)).check(matches(isEnabled())) }
            val scale = F.app.resources.configuration.fontScale.toString()
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
                scroll.scrollTo(0, 0)
            }
            F.await { scenario.onActivity { activity ->
                val scroll = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
                assertEquals(0, scroll.scrollY)
            } }
            scenario.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "layout-$scale-top") }
            onView(withId(R.id.reason)).perform(independentSkuScroll(), click())
            F.await { scenario.onActivity { activity ->
                assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
            } }
            scenario.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "layout-$scale-keyboard") }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.back)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            scenario.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "layout-$scale-bottom") }
        }
    }
    private fun text(id: Int, value: String) = onView(withId(id)).perform(independentSkuScroll(), replaceText(value), closeSoftKeyboard())
}
