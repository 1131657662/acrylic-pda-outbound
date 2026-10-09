package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.HandoverActivity
import com.acrylic.outbound.pda.ui.SubmissionActivity
import com.acrylic.outbound.pda.submission.LocalSubmission
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual FORMAL product pages, copied one-shot Room and exact signed result; no live MySQL or original identity. */
class FormalFaultSubmissionDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun originalResultQuerySurvivesLostResponseThroughHandoverProductPage() {
        FormalFaultUiFixture(app).use { f ->
            f.prepare(true)
            val before = checkNotNull(app.database.submissions().get(f.id))
            assertEquals("FROZEN", before.state)
            identityIsolation(f, before)
            ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { page ->
                FormalFaultUiEvidence.await { onView(withText("查看提交与回执")).check(matches(isEnabled())) }
                openSubmissionCard()
                page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, f.directory, "handover") }
                onView(withText("查看提交与回执")).perform(click())
                retryOriginal(f, before)
                FormalFaultUiEvidence.click(R.id.back)
            }
            assertEquals(2, f.calls.get())
            File(f.directory, "opening.marker").writeText("Actual HandoverActivity/SubmissionActivity lost-query/retry, same original ID, synthetic session and HTTP replay.\n")
        }
    }

    @Test fun forceStoppedProcessShowsSameVerifiedSuccessWithoutResending() {
        FormalFaultUiFixture(app).use { f ->
            f.prepare(false)
            val before = checkNotNull(app.database.submissions().get(f.id))
            assertEquals("SYNCED", before.state)
            val intent = Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", f.assignment)
            ActivityScenario.launch<SubmissionActivity>(intent).use { page ->
                status("云端已确认出库")
                page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, f.directory, "restarted") }
                assertEquals(before, app.database.submissions().get(f.id))
                assertEquals(0, f.calls.get())
                FormalFaultUiEvidence.click(R.id.back)
            }
            File(f.directory, "restore.marker").writeText("New app process retained same signed success and frozen original; no replay query or upload.\n")
        }
    }

    private fun identityIsolation(f: FormalFaultUiFixture, before: LocalSubmission) {
        f.otherIdentity()
        ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java)).use { page ->
            FormalFaultUiEvidence.await {
                page.onActivity { assertEquals(0, it.findViewById<LinearLayout>(R.id.cards).childCount) }
                onView(withId(R.id.status)).check(matches(withText(containsString("本机资料已保留"))))
            }
            page.onActivity { FormalFaultUiEvidence.screen(it.window.decorView, f.directory, "other-identity") }
            FormalFaultUiEvidence.click(R.id.back)
        }
        assertEquals(before, app.database.submissions().get(f.id)); assertEquals(0, f.calls.get())
        f.restoreIdentity()
    }

    private fun retryOriginal(f: FormalFaultUiFixture, before: LocalSubmission) {
        status("本单已冻结")
        FormalFaultUiEvidence.click(R.id.refresh)
        try { status("上传结果待核对") }
        catch (error: Throwable) {
            runCatching { snapshotCurrent(f, "failed-result-query") }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
        assertEquals(1, f.calls.get())
        assertEquals(before.payload, app.database.submissions().get(f.id)!!.payload)
        assertNull(app.database.submissions().get(f.id)!!.receipt)
        snapshotCurrent(f, "lost-result")
        FormalFaultUiEvidence.click(R.id.refresh)
        status("云端已确认出库")
        val after = checkNotNull(app.database.submissions().get(f.id))
        assertEquals(before.payload, after.payload); assertEquals(before.sha256, after.sha256)
        assertEquals("SYNCED", after.state); assertFalse(after.autoRetry)
        snapshotCurrent(f, "verified-result")
    }

    private fun snapshotCurrent(f: FormalFaultUiFixture, label: String) {
        onView(withId(R.id.status)).check { view, error ->
            if (error != null) throw error
            FormalFaultUiEvidence.screen(view.rootView, f.directory, label)
        }
    }

    private fun openSubmissionCard() {
        repeat(20) {
            if (runCatching { onView(withText("查看提交与回执")).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(swipeUp())
            Thread.sleep(200)
        }
        onView(withText("查看提交与回执")).check(matches(isCompletelyDisplayed()))
    }

    private fun status(text: String) = FormalFaultUiEvidence.await {
        onView(withId(R.id.status)).check(matches(withText(containsString(text))))
    }
}
