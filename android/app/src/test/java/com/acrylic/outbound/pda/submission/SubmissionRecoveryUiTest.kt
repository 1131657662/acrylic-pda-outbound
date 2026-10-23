package com.acrylic.outbound.pda.submission

import android.content.Intent
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.sync.RecoveryProtection
import com.acrylic.outbound.pda.ui.RescueExportActivity
import com.acrylic.outbound.pda.ui.SubmissionActivity
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = com.acrylic.outbound.pda.PdaTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SubmissionRecoveryUiTest {
    @get:Rule val folder = TemporaryFolder()
    @After fun closeScheduler() { androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase() }

    @Test fun reconcilingUnsubmittedOrderCannotOpenFreezeConfirmation() = scenario(true) { activity ->
        assertTrue(activity.findViewById<TextView>(R.id.status).text.contains("恢复待对账"))
        val freeze = activity.findViewById<Button>(R.id.freeze)
        assertFalse(freeze.isEnabled)
        freeze.performClick()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test fun normalUnsubmittedOrderCanStillOpenFreezeConfirmation() = scenario(false) { activity ->
        val freeze = activity.findViewById<Button>(R.id.freeze)
        assertTrue(freeze.isEnabled)
        freeze.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog); assertTrue(dialog.isShowing); dialog.dismiss()
    }

    @Test fun syncedSubmissionCanOpenReadOnlyExportForOriginalOrder() = scenario(false, "SYNCED") { activity ->
        assertTrue(activity.findViewById<TextView>(R.id.status).text.contains("回执已验签"))
        val export = activity.findViewById<Button>(R.id.rescueExport)
        assertTrue(export.isShown); assertTrue(export.isEnabled)
        export.performClick()
        val destination = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(RescueExportActivity::class.java.name, destination.component?.className)
        assertEquals("FORMAL", destination.getStringExtra("source"))
        assertEquals(activity.intent.getStringExtra("assignmentId"), destination.getStringExtra("localOrderId"))
    }

    @Test fun reconcilingSyncedSubmissionRetainsReadOnlyExportEntry() = scenario(true, "SYNCED") { activity ->
        assertTrue(activity.findViewById<TextView>(R.id.status).text.contains("恢复待对账"))
        assertFalse(activity.findViewById<Button>(R.id.freeze).isEnabled)
        val export = activity.findViewById<Button>(R.id.rescueExport)
        assertTrue(export.isShown); assertTrue(export.isEnabled)
        export.performClick()
        assertEquals("FORMAL", Shadows.shadowOf(activity).nextStartedActivity.getStringExtra("source"))
    }

    private fun scenario(reconciling: Boolean, submissionState: String? = null, verify: (SubmissionActivity) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<PdaApplication>()
        val fixture = runBlocking(Dispatchers.IO) {
            WorkFixture(folder.root).also {
                it.scan()
                RecoveryProtection(it.db).observe(JSONObject().put("serverRecoveryEpoch", if (reconciling) "2" else "1")
                    .put("serviceMode", "NORMAL"))
                if (submissionState != null) it.db.submissions().insert(LocalSubmission(
                    id = "ui-only-submission", assignmentId = it.assignment, payload = "{}",
                    sha256 = "ui-only-not-a-verified-receipt", state = submissionState))
                it.db.close()
            }
        }
        val intent = Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", fixture.assignment)
        val controller = Robolectric.buildActivity(SubmissionActivity::class.java, intent).setup()
        try {
            awaitRendered(controller.get()); verify(controller.get())
            runBlocking(Dispatchers.IO) {
                assertEquals(submissionState, app.database.submissions().forOrder(fixture.assignment)?.state)
                assertEquals("SCANNING", app.database.work().order(fixture.assignment)!!.state)
                assertEquals(1L, app.database.scans().total(fixture.assignment).count)
            }
        } finally {
            controller.pause().stop().destroy(); app.work.close(); app.database.close(); fixture.close()
        }
    }

    private fun awaitRendered(activity: SubmissionActivity) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<TextView>(R.id.status).text.isNotEmpty()) return
            Thread.sleep(10)
        }
        fail("Submission screen did not read the preserved order")
    }
}
