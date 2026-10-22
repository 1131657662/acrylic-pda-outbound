package com.acrylic.outbound.pda.ui

import android.widget.Button
import android.widget.EditText
import android.content.Intent
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.device.ScannerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentWorkCardTest {
    @Test fun cardRetryPreservesOriginalBroadcastCaptureAfterSaveTailFailure() {
        IndependentWorkCardUiFixture().use { f ->
            val db = f.base.app.database
            runBlocking(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_card_scan_tail BEFORE INSERT ON independent_task_event " +
                    "WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'synthetic card tail'); END") }
            f.receive(f.begin()); f.await { f.engine.state.value.ready }
            f.activity.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, "003A0000001"))
            f.await { f.engine.state.value.queued == 0 && f.engine.state.value.blocked.contains("synthetic card tail") }
            val original = checkNotNull(f.engine.state.value.candidate).capture
            runBlocking(Dispatchers.IO) {
                assertNull(db.independentScans().scan(original.id))
                assertEquals("CONFIRM_REQUESTED", db.independentScans().capture(original.id)?.processing?.state)
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER synthetic_card_scan_tail")
            }
            f.receive(f.begin()); f.await { f.engine.state.value.queued == 0 && f.engine.state.value.task?.state?.actualCount == 1L }
            runBlocking(Dispatchers.IO) {
                assertEquals(original, db.independentScans().captures(f.original.id).single().capture)
                assertNotNull(db.independentScans().scan(original.id))
                assertEquals(1, db.independentTasks().events(f.original.id).count { it.type == "INDEPENDENT_SAVED" })
            }
        }
    }

    @Test fun originalCardStartsOriginalTaskAndManualPageSavesWithoutPasswordTranscription() {
        IndependentWorkCardUiFixture().use { f ->
            val original = f.original; val cardBytes = f.task.card.card.readBytes()
            assertEquals("", f.activity.findViewById<EditText>(R.id.workPassword).text.toString())
            assertFalse(f.engine.state.value.ready)
            f.receive(f.begin()); f.await { f.engine.state.value.ready }
            f.activity.findViewById<Button>(R.id.workManualToggle).performClick()
            f.activity.findViewById<EditText>(R.id.workManual).setText("003A0000001")
            f.activity.findViewById<Button>(R.id.workValidate).performClick()
            f.await { f.engine.state.value.candidate?.processing?.state == "WAITING_CONFIRM" && f.engine.state.value.queued == 0 }
            assertEquals(0L, f.engine.state.value.task?.state?.actualCount)
            f.activity.findViewById<Button>(R.id.workConfirm).performClick()
            f.await { f.engine.state.value.task?.state?.actualCount == 1L && f.engine.state.value.queued == 0 }
            assertEquals(original, f.engine.state.value.task?.task)
            assertEquals(300500L, f.engine.state.value.task?.state?.weightGrams)
            assertArrayEquals(cardBytes, f.task.card.card.readBytes())
            assertEquals(1, f.base.server.requestCount)
            runBlocking(Dispatchers.IO) {
                val scans = f.base.app.database.independentScans().captures(original.id)
                assertEquals(1, scans.size); assertEquals(1L, scans.single().capture.sequence)
                assertEquals("COMMITTED", scans.single().processing.state)
            }
        }
    }
}
