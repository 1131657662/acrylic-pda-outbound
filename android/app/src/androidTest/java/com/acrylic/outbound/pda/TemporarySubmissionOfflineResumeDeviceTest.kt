package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.TemporarySubmissionActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Continues a retained original batch, without refreezing, reseeding, or replacing identity. */
class TemporarySubmissionOfflineResumeDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun offlineQueryKeepsRetainedBatchAndCollapsedDetails() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporarySubmissionOfflineResume") == "true")
        val order = requireNotNull(args.getString("originalOrder"))
        require(UUID.fromString(order).toString() == order)
        val sha = requireNotNull(args.getString("payloadSha256"))
        require(sha.matches(Regex("[0-9a-f]{64}")))
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        check(app.session.settings.server == "http://127.0.0.1:19434/api/v1/")
        check(runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 19434), 500) } }.isFailure)
        directory = File(app.filesDir, "temporary-submission-entry/$run")
        check(directory.mkdirs()) { "Preserve previous evidence" }
        snapshot("before")
        try { exercise(order, sha) } finally {
            androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
            snapshot("after")
        }
    }

    private fun exercise(order: String, sha: String) {
        val saved = requireNotNull(app.database.temporarySubmissions().forOrder(order))
        assertEquals(sha, saved.sha256)
        assertEquals("RESULT_UNKNOWN", saved.state)
        val intent = Intent(app, TemporarySubmissionActivity::class.java).putExtra("clientOrderId", order)
        ActivityScenario.launch<TemporarySubmissionActivity>(intent).use { page ->
            ready()
            onView(withId(R.id.notice)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.upload)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, directory, "retained") }
            FormalFaultUiEvidence.click(R.id.refresh)
            ready()
            assertEquals(saved, app.database.temporarySubmissions().forOrder(order))
            page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, directory, "queried") }
            details(page)
            page.recreate()
            ready()
            onView(withId(R.id.notice)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            assertEquals(saved, app.database.temporarySubmissions().forOrder(order))
            page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, directory, "recreated") }
            FormalFaultUiEvidence.click(R.id.back)
        }
        File(directory, "result.json").writeText(JSONObject().put("submissionId", saved.id)
            .put("payloadSha256", sha).put("sameCompleteSubmission", true).put("serviceOffline", true).toString(2))
    }

    private fun details(page: ActivityScenario<TemporarySubmissionActivity>) {
        FormalFaultUiEvidence.click(R.id.technicalDetails)
        FormalFaultUiEvidence.reveal(R.id.notice)
        onView(withId(R.id.notice)).check(matches(withText(containsString("原资格、冻结正文与实物占用持续保留"))))
        page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, directory, "details") }
    }

    private fun ready() = FormalFaultUiEvidence.await {
        onView(withId(R.id.status)).check(matches(withText(containsString("结果待核对"))))
        onView(withId(R.id.refresh)).check(matches(isEnabled()))
        onView(withId(R.id.refresh)).check(matches(withText("先查询原临时批次结果")))
    }

    private fun snapshot(label: String) {
        File(directory, "database-$label.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }
}
