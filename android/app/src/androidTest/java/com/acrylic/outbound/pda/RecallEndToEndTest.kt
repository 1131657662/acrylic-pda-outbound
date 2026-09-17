package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.ui.RecallActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecallEndToEndTest {
    @Test fun originalPdaStopsAndRetriesLostStopProofWithoutReleasingLoadedBale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val app = context.applicationContext as PdaApplication
        val file = File(context.filesDir, "recall-fixture.json"); val fixture = JSONObject(file.readText()); file.delete()
        val admin = RecallAdmin(fixture)
        SubmissionProxy(fixture.getString("baseUrl")).use { proxy ->
            val key = app.session.publicKey(); val device = admin.register(key)
            app.session.settings.bind(proxy.base, device, key); app.session.login(fixture.getString("username"), fixture.getString("password"))
            val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
            val a = app.repository.claim(order, "召回实物验证"); val prepared = app.repository.prepare(a) {}; val assignment = prepared.assignmentId
            collect(app, assignment); admin.recall(prepared.orderId); app.recalls.check(assignment)
            assertFalse(app.work.receive("003ABCDEFGHI", "BROADCAST")); assertEquals("RECALL_PENDING", app.database.work().order(assignment)!!.state)
            screen(app, assignment, proxy)
            assertEquals("RECALL_PENDING", admin.status(prepared.orderId)); assertEquals(1, app.database.scans().total(assignment).count)
            val local = app.database.recalls().get(assignment)!!; app.work.pause(); app.database.close()
            val reopened = PdaDatabase.open(context)
            try { assertEquals(local, reopened.recalls().get(assignment)); assertEquals("RECALLED", reopened.work().order(assignment)!!.state) }
            finally { reopened.close() }
            File(context.filesDir, "recall-evidence.json").writeText(JSONObject().put("assignmentId", assignment).put("orderId", prepared.orderId)
                .put("stoppedAfterReopen", true).put("lostStopResponseRecovered", true).put("actualCount", 1).put("pendingInputReviewed", true)
                .put("signedProofVerified", true).put("orderRemainsRecallPending", true).toString())
        }
    }
    private fun collect(app: PdaApplication, assignment: String) {
        val codes = app.database.openHelper.readableDatabase.query("SELECT barcode FROM prepared_bale WHERE assignmentId=? ORDER BY barcode", arrayOf(assignment)).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) } }
        app.work.resume(assignment); waitUntil { app.work.state.value.ready }
        assertTrue(app.work.receive(codes[0], "BROADCAST")); waitUntil { app.work.state.value.order?.actualCount == 1L }
        assertTrue(app.work.receive(codes[1], "MANUAL")); waitUntil { app.work.state.value.candidate?.state == "WAITING_CONFIRM" }
    }
    private fun screen(app: PdaApplication, assignment: String, proxy: SubmissionProxy) {
        ActivityScenario.launch<RecallActivity>(Intent(app, RecallActivity::class.java).putExtra("assignmentId", assignment)).use { screen ->
            waitUntil { var enabled = false; screen.onActivity { enabled = it.findViewById<Button>(R.id.freeze).isEnabled }; enabled }
            screen.onActivity { assertTrue(it.findViewById<TextView>(R.id.pending).text.contains("原输入")); it.findViewById<Button>(R.id.freeze).performClick() }
            onView(withText("我已逐项核对"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(click())
            waitUntil { app.database.recalls().header(assignment)?.packageId != null }
            assertEquals(0, app.database.captures().pendingCount(assignment)); val payload = app.database.recalls().get(assignment)!!.payload
            proxy.loseStopResponse.set(true)
            waitUntil { var enabled = false; screen.onActivity { enabled = it.findViewById<Button>(R.id.upload).isEnabled }; enabled }
            screen.onActivity { it.findViewById<Button>(R.id.upload).performClick() }
            waitUntil { var failed = false; screen.onActivity { failed = it.findViewById<TextView>(R.id.error).text.isNotEmpty() && it.findViewById<Button>(R.id.upload).isEnabled }; failed }
            assertEquals("RECOVERY_FROZEN", app.database.recalls().header(assignment)!!.state)
            screen.onActivity { it.findViewById<Button>(R.id.upload).performClick() }
            waitUntil { app.database.recalls().header(assignment)?.state == "RECALLED" }
            assertEquals(payload, app.database.recalls().get(assignment)!!.payload)
            screen.recreate()
            waitUntil { var verified = false; screen.onActivity { verified = it.findViewById<TextView>(R.id.status).text.contains("停止证明已验签") }; verified }
        }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Recall state did not arrive", condition())
    }
}
