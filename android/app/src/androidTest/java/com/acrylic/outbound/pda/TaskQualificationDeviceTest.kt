package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Reads earlier synthetic tasks on the retained native identity; does not seed or freeze tasks. */
class TaskQualificationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var evidence: File

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("taskQualification") == "true")
        check(app.session.settings.server == "http://127.0.0.1:19781/api/v1/")
        val run = checkNotNull(args.getString("evidenceRun")); check(run.matches(Regex("[a-z0-9-]{1,40}")))
        evidence = File(app.filesDir, "task-qualification/$run"); check(evidence.mkdirs())
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    private fun ids() = listOf(
        "FORMAL" to File(app.filesDir, "formal-pending-status/formal-status05/assignment.txt").readText().trim(),
        "TEMP" to File(app.filesDir, "temporary-pending-status/order.txt").readText().trim())

    @Test fun rebootKeepsOriginalRecordsReadableWithoutGrantingScanTime() {
        guard()
        assertTrue(runCatching { app.session.now() }.isFailure)
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        ids().forEach { (source, id) ->
            ActivityScenario.launch<TaskQualificationActivity>(qualification(source, id)).use { scenario ->
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("可看：原人原机已核对")))) }
                onView(withId(R.id.summary)).check(matches(withText(containsString("可扫：未通过"))))
                onView(withId(R.id.summary)).check(matches(withText(containsString("尚未冻结，无原批次可上传"))))
                onView(withId(R.id.qualificationDetails)).perform(independentSkuScroll(), click())
                onView(withId(R.id.rawDetails)).check(matches(withText(containsString("设备已重启"))))
                capture(source + "-reboot")
                scenario.recreate()
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("可扫：未通过")))) }
            }
        }
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(evidence, "result.json").writeText(JSONObject().put("rebootReadOnly", true).put("roomUnchanged", true).toString(2))
    }

    @Test fun normalConnectionRecheckAllowsBothProductEntrancesWithoutBusinessWrites() {
        guard()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        val original = app.session.requireSession(); val key = app.session.publicKey()
        ActivityScenario.launch<DeviceSettingsActivity>(Intent(app, DeviceSettingsActivity::class.java)).use {
            await { onView(withId(R.id.checkConnection)).check(matches(isEnabled())) }
            onView(withId(R.id.checkConnection)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("后台连接正常")))) }
        }
        val current = app.session.requireSession()
        listOf("deviceId", "userId", "warehouseId", "localSessionId").forEach { assertEquals(original.getString(it), current.getString(it)) }
        assertEquals(key, app.session.publicKey())
        ids().forEach { (source, id) -> entrance(source, id) }
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(evidence, "result.json").writeText(JSONObject().put("normalProductConnection", true)
            .put("formalAndTemporaryEntrances", true).put("roomUnchanged", true).put("identityPreserved", true).toString(2))
    }

    private fun entrance(source: String, id: String) {
        val type = if (source == "FORMAL") WorkActivity::class.java else TemporaryWorkActivity::class.java
        val field = if (source == "FORMAL") "assignmentId" else "clientOrderId"
        ActivityScenario.launch<androidx.activity.ComponentActivity>(Intent(app, type).putExtra(field, id)).use {
            await {
                val ready = if (source == "FORMAL") app.work.state.value.ready else app.temporaryWork.state.value.ready
                assertTrue(ready)
            }
            onView(withId(R.id.moreActions)).perform(independentSkuScroll(), click())
            onView(withId(R.id.taskQualification)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.summary)).check(matches(withText(containsString("可扫：本机条件已核对")))) }
            onView(withId(R.id.summary)).check(matches(withText(containsString("可结：本机前置已核对"))))
            onView(withId(R.id.summary)).check(matches(withText(containsString("本页未解锁"))))
            capture(source + "-positive")
            onView(withId(R.id.retry)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.summary)).check(matches(withText(containsString("可扫：本机条件已核对")))) }
            onView(withId(R.id.back)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.moreActions)).check(matches(isDisplayed())) }
        }
    }

    @Test fun missingOrderDoesNotExposeAnotherTaskOrReportQualification() {
        guard()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        ids().forEach { (source, _) ->
            ActivityScenario.launch<TaskQualificationActivity>(qualification(source, "00000000-0000-0000-0000-000000000000")).use {
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("本单核对未完成")))) }
                onView(withId(R.id.summary)).check(matches(not(withText(containsString("可看：原人原机已核对")))))
            }
        }
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    @Test fun qualificationTextAndActionsRemainReachableAtConfiguredFont() {
        guard()
        val scale = InstrumentationRegistry.getArguments().getString("expectedFontScale")!!.toFloat()
        assertEquals(scale, app.resources.configuration.fontScale, 0.01f)
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        ids().forEach { (source, id) ->
            ActivityScenario.launch<TaskQualificationActivity>(qualification(source, id)).use { scenario ->
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("可看：原人原机已核对")))) }
                val ui = FormalDetailsDeviceUi(evidence)
                ui.inspect(source + "-top")
                ui.click(R.id.retry)
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("可看：原人原机已核对")))) }
                ui.click(R.id.qualificationDetails)
                ui.inspect(source + "-details")
                ui.click(R.id.qualificationDetails)
                scenario.recreate()
                await { onView(withId(R.id.summary)).check(matches(withText(containsString("本页未解锁")))) }
                ui.click(R.id.back)
            }
        }
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(evidence, "font-result.json").writeText(JSONObject().put("scale", scale).put("roomUnchanged", true).toString(2))
    }

    private fun qualification(source: String, id: String) = Intent(app, TaskQualificationActivity::class.java)
        .putExtra("source", source).putExtra("localOrderId", id)

    private fun capture(label: String) {
        FormalDetailsDeviceUi(evidence).inspect(label)
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Qualification page did not reach expected state", last)
    }
}
