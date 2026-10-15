package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.ui.TemporaryWorkActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real original-order product entry after a retained failed input has already been resolved. */
class TemporaryFailureAcknowledgementDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun duplicateAcknowledgementShowsCompletedFeedback() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporaryFailureAcknowledgement") == "true")
        val order = requireNotNull(args.getString("originalOrder"))
        require(UUID.fromString(order).toString() == order)
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        directory = File(app.filesDir, "temporary-failure-ack/$run")
        check(directory.mkdirs()) { "Preserve previous evidence" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        snapshot("before")
        try { exercise(order) } finally { snapshot("after") }
    }

    private fun exercise(order: String) {
        val scans = app.database.temporaryScans()
        val original = scans.captures(order)
        val barcode = scans.recent(order).single { it.status == "ACTIVE" }.barcode
        assertEquals(1L, app.temporaryStore().total(order).count)
        assertNull(scans.pending(order)); assertNull(scans.failure(order))
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", order)).use {
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.queued == 0) }
            app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, barcode))
            await { assertEquals("INVALID", app.temporaryWork.state.value.candidate?.state) }
            onView(withId(R.id.confirm)).check(matches(withText(containsString("本包未保存"))))
            val rejected = requireNotNull(app.temporaryWork.state.value.candidate)
            shot("duplicate-rejected")
            onView(withId(R.id.confirm)).perform(scrollTo(), click())
            await {
                assertNull(app.temporaryWork.state.value.candidate)
                onView(withId(R.id.message)).check(matches(withText(containsString("已确认本包未保存，原输入保留"))))
            }
            assertEquals(1L, app.temporaryStore().total(order).count)
            assertEquals(original.size + 1, scans.captures(order).size)
            original.forEach { row -> assertEquals(row, scans.capture(row.id)) }
            assertNotNull(scans.capture(rejected.id)?.failureAckAt)
            assertNull(app.database.temporarySubmissions().forOrder(order))
            shot("duplicate-acknowledged")
            File(directory, "result.json").writeText(JSONObject().put("order", order).put("capture", rejected.id)
                .put("count", 1).put("originalCapturesPreserved", true).put("noSubmission", true).toString(2))
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }

    private fun snapshot(label: String) {
        File(directory, "database-$label.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Original-order acknowledgement did not finish", last)
    }

    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Record original-order acknowledgement"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
