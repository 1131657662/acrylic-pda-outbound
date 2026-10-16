package com.acrylic.outbound.pda

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.acrylic.outbound.pda.temporary.TemporarySubmission
import com.acrylic.outbound.pda.ui.TemporarySubmissionActivity
import com.acrylic.outbound.pda.ui.TemporaryWorkActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Opt-in, one-shot evidence on a preserved emulator order; never creates identity or stock. */
class TemporarySubmissionEntryDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun originalBatchSurvivesFreezeRecreationAndOfflineQuery() {
        val order = guard()
        snapshot("before")
        try { exercise(order) } finally {
            androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
            snapshot("after")
        }
    }

    private fun guard(): String {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporarySubmissionEntry") == "true")
        val order = requireNotNull(args.getString("originalOrder"))
        require(UUID.fromString(order).toString() == order)
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        check(app.session.settings.server == "http://127.0.0.1:19434/api/v1/")
        check(runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 19434), 500) } }.isFailure)
        assertNull(app.database.temporarySubmissions().forOrder(order))
        assertEquals("SCANNING", app.database.temporaryDrafts().get(order)?.state)
        assertEquals(1L, app.temporaryStore().total(order).count)
        assertEquals(2L, app.database.temporaryDrafts().lines(order).sumOf { it.plannedCount })
        directory = File(app.filesDir, "temporary-submission-entry/$run")
        check(directory.mkdirs()) { "Preserve previous evidence" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        return order
    }

    private fun exercise(order: String) {
        val captures = app.database.temporaryScans().captures(order)
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java)
            .putExtra("clientOrderId", order)).use {
            FormalFaultUiEvidence.await { assertTrue(app.temporaryWork.state.value.ready) }
            FormalFaultUiEvidence.click(R.id.moreActions)
            FormalFaultUiEvidence.click(R.id.closeOrder)
            awaitStatus("尚未冻结")
            cancelFreeze(order)
            val frozen = confirmFreeze(order)
            recreate(frozen)
            offlineQuery(frozen)
            assertEquals(captures, app.database.temporaryScans().captures(order))
            assertEquals(1L, app.temporaryStore().total(order).count)
            File(directory, "result.json").writeText(JSONObject().put("order", order)
                .put("submissionId", frozen.id).put("payloadSha256", frozen.sha256)
                .put("capturesPreserved", true).put("serviceOffline", true)
                .put("workersCancelledOnExit", true).toString(2))
            FormalFaultUiEvidence.click(R.id.back)
        }
    }

    private fun cancelFreeze(order: String) {
        shot("open")
        onView(withId(R.id.notice)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        FormalFaultUiEvidence.click(R.id.freeze)
        onView(withText("返回核对")).perform(click())
        assertNull(app.database.temporarySubmissions().forOrder(order))
        assertEquals("SCANNING", app.database.temporaryDrafts().get(order)?.state)
        snapshot("cancelled")
        shot("cancelled")
    }

    private fun confirmFreeze(order: String): TemporarySubmission {
        onView(withId(R.id.reason)).perform(scrollTo(), replaceText("开发验收：原计划2包已装1包，保留短装待专项审批"), closeSoftKeyboard())
        FormalFaultUiEvidence.click(R.id.freeze)
        onView(withText("确认冻结")).perform(click())
        awaitStatus("尚未上传")
        val frozen = requireNotNull(app.database.temporarySubmissions().forOrder(order))
        assertEquals("FROZEN", frozen.state)
        assertEquals("FROZEN", app.database.temporaryDrafts().get(order)?.state)
        onView(withId(R.id.freeze)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.upload)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        onView(withId(R.id.refresh)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        File(directory, "frozen-payload.json").writeText(frozen.payload)
        snapshot("frozen")
        shot("frozen")
        return frozen
    }

    private fun recreate(frozen: TemporarySubmission) {
        instrumentation.runOnMainSync { current().recreate() }
        awaitStatus("尚未上传")
        assertEquals(frozen, app.database.temporarySubmissions().get(frozen.id))
        shot("recreated")
    }

    private fun offlineQuery(frozen: TemporarySubmission) {
        FormalFaultUiEvidence.click(R.id.upload)
        awaitStatus("结果待核对")
        FormalFaultUiEvidence.await {
            onView(withId(R.id.refresh)).check(matches(isEnabled()))
            onView(withId(R.id.error)).check(matches(withText(containsString("网络连接或传输失败"))))
        }
        assertOriginal(frozen)
        snapshot("offline-upload")
        shot("offline-upload")
        FormalFaultUiEvidence.click(R.id.technicalDetails)
        FormalFaultUiEvidence.reveal(R.id.notice)
        shot("details")
        onView(withId(R.id.refresh)).check(matches(withText("先查询原临时批次结果")))
        FormalFaultUiEvidence.click(R.id.refresh)
        awaitStatus("结果待核对")
        FormalFaultUiEvidence.await { onView(withId(R.id.refresh)).check(matches(isEnabled())) }
        assertOriginal(frozen)
        snapshot("offline-query")
        shot("offline-query")
    }

    private fun assertOriginal(frozen: TemporarySubmission) {
        val saved = requireNotNull(app.database.temporarySubmissions().forOrder(frozen.clientOrderId))
        assertEquals(frozen.id, saved.id)
        assertEquals(frozen.payload, saved.payload)
        assertEquals(frozen.sha256, saved.sha256)
        assertEquals("RESULT_UNKNOWN", saved.state)
        assertNull(saved.receipt)
    }

    private fun awaitStatus(text: String) = FormalFaultUiEvidence.await {
        onView(withId(R.id.status)).check(matches(withText(containsString(text))))
    }

    private fun current(): Activity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).single().also { assertTrue(it is TemporarySubmissionActivity) }

    private fun snapshot(label: String) {
        File(directory, "database-$label.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    private fun shot(label: String) {
        instrumentation.runOnMainSync { FormalFaultUiEvidence.screen(current().window.decorView, directory, label) }
    }
}
