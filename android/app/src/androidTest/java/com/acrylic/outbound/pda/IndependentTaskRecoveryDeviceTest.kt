package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentTaskActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

class IndependentTaskRecoveryDeviceTest {
    private val directory get() = File(F.app.filesDir, "independent-task-device")
    private val repository get() = IndependentTaskRepository(F.app.database, { TrustedTime.sample(F.app) }) {
        val s = F.app.session.requireSession(); IndependentTaskActor(s.getString("userId"), s.getString("deviceId"), s.getString("warehouseId"))
    }
    @Test fun actualAuditTailFailureKeepsOriginalRequestAndNoPartialTask() {
        F.guard(); assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        val db = F.app.database.openHelper.writableDatabase; val before = F.app.database.independentTasks().count()
        db.execSQL("""CREATE TRIGGER isolated_task_audit_tail BEFORE INSERT ON independent_task_event
            BEGIN SELECT RAISE(ABORT,'isolated_task_audit_tail'); END""")
        try {
            ActivityScenario.launch<IndependentTaskActivity>(Intent(F.app, IndependentTaskActivity::class.java)).use {
                F.await { onView(withId(R.id.newTask)).check(matches(isEnabled())) }; F.click(R.id.newTask)
                F.await { onView(withId(R.id.vehicle)).check(matches(isEnabled())) }
                text(R.id.vehicle, "E06-TAIL-TRUCK"); text(R.id.reason, "Actual audit tail rollback"); text(R.id.quantity, "3")
                F.click(R.id.add); F.secret(); F.click(R.id.create); F.message("isolated_task_audit_tail")
                onView(withId(R.id.vehicle)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            }
            val request = repository.latestRequest()!!
            assertEquals(before, F.app.database.independentTasks().count()); assertNull(repository.request(request.id).second)
            assertTrue(F.app.database.independentTasks().lines(request.id).isEmpty())
            assertNull(F.app.database.independentTasks().state(request.id)); assertTrue(F.app.database.independentTasks().events(request.id).isEmpty())
            File(directory, "pending-original.json").writeText(JSONObject().put("id", request.id).put("commandJson", request.commandJson)
                .put("commandSha256", request.commandSha256).put("authorizationJson", request.authorizationJson).toString())
        } finally { db.execSQL("DROP TRIGGER isolated_task_audit_tail") }
    }
    @Test fun newProcessRetriesExactOriginalAfterRealTailFailure() {
        F.guard(); assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        val original = JSONObject(File(directory, "pending-original.json").readText()); val before = F.app.database.independentTasks().count()
        ActivityScenario.launch<IndependentTaskActivity>(Intent(F.app, IndependentTaskActivity::class.java)).use {
            F.message("原创建请求已保存")
            onView(withId(R.id.vehicle)).check(matches(withText("E06-TAIL-TRUCK")))
            F.secret(); F.click(R.id.create); F.message("独立计划已保存")
        }
        val request = repository.latestRequest()!!; val task = repository.read(request.id)
        assertEquals(original.getString("id"), request.id); assertEquals(original.getString("commandJson"), request.commandJson)
        assertEquals(original.getString("authorizationJson"), request.authorizationJson)
        assertEquals(original.getString("commandSha256"), task.task.commandSha256)
        assertEquals(before + 1, F.app.database.independentTasks().count()); assertEquals(1, task.events.size)
        assertEquals(3L, task.lines.single().plannedCount); assertEquals(0L, task.state.actualCount)
        File(directory, "tail-recovered.json").writeText(JSONObject().put("actualTailFailure", true).put("newProcess", true)
            .put("exactOriginalRequest", true).put("wholeTaskAtomic", true).toString())
    }
    private fun text(id: Int, value: String) = onView(withId(id)).perform(independentSkuScroll(), replaceText(value), closeSoftKeyboard())
}
