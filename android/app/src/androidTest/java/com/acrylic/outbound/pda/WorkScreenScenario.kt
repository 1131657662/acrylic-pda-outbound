package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.ui.WorkActivity
import org.junit.Assert.*

object WorkScreenScenario {
    fun exercise(app: PdaApplication, assignment: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val codes = app.database.openHelper.readableDatabase.query("SELECT barcode FROM prepared_bale WHERE assignmentId=? ORDER BY barcode", arrayOf(assignment)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) } }
        ActivityScenario.launch<WorkActivity>(Intent(context, WorkActivity::class.java).putExtra("assignmentId", assignment)).use { screen ->
            waitUntil { app.work.state.value.ready }
            broadcast(app, "bad"); waitUntil { app.work.state.value.candidate?.state == "INVALID" }
            broadcast(app, codes[0]); waitUntil { app.work.state.value.pending == 1 }
            assertEquals(0, app.database.scans().total(assignment).count)
            click(screen, R.id.confirm); waitUntil { app.work.state.value.order?.actualCount == 1L }
            broadcast(app, codes[0]); waitUntil { app.work.state.value.candidate?.state == "INVALID" }
            click(screen, R.id.confirm); waitUntil { app.work.state.value.candidate == null }
            click(screen, R.id.manualToggle)
            screen.onActivity { it.findViewById<EditText>(R.id.manual).setText(codes[1]) }
            click(screen, R.id.validate); waitUntil { app.work.state.value.candidate?.state == "WAITING_CONFIRM" }
            assertEquals(1, app.database.scans().total(assignment).count)
            screen.recreate(); waitUntil { app.work.state.value.ready && app.work.state.value.candidate?.state == "WAITING_CONFIRM" }
            click(screen, R.id.confirm); waitUntil { app.work.state.value.order?.actualCount == 2L }
            assertEquals(601500, app.database.scans().total(assignment).grams)
            assertEquals(listOf(2L, 4L), app.database.scans().page(assignment, 0, 10).map { it.orderScanSeq })
            screen.moveToState(Lifecycle.State.CREATED); broadcast(app, codes[0])
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(4, app.database.captures().page(assignment, 0, 20).size)
            screen.moveToState(Lifecycle.State.RESUMED); waitUntil { app.work.state.value.ready }
            screen.onActivity { assertTrue(it.findViewById<TextView>(R.id.totals).text.contains("601.5")) }
        }
        app.work.pause()
    }
    private fun broadcast(app: PdaApplication, code: String) {
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).setPackage(app.packageName).putExtra(ScannerReceiver.BARCODE_KEY, code))
    }
    private fun click(screen: ActivityScenario<WorkActivity>, id: Int) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        screen.onActivity { assertTrue(it.findViewById<Button>(id).performClick()) }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Expected work state did not arrive", condition())
    }
}
