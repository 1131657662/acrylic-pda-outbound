package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.ui.RecallActivity
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContinuationEndToEndTest {
    @Test fun rejectedOrderResumesAfterLostActivationAndLocalRollbackThenSubmitsSecondRound() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val app = context.applicationContext as PdaApplication
        val file = File(context.filesDir, "continuation-fixture.json"); val fixture = JSONObject(file.readText()); file.delete()
        val admin = RecallAdmin(fixture)
        SubmissionProxy(fixture.getString("baseUrl")).use { proxy ->
            val key = app.session.publicKey(); val device = admin.register(key)
            app.session.settings.bind(proxy.base, device, key); app.session.login(fixture.getString("username"), fixture.getString("password"))
            val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
            val a = app.repository.claim(order, "续作模拟器验证"); val prepared = app.repository.prepare(a) {}; val id = prepared.assignmentId
            val codes = codes(app, id); firstRound(app, id, codes.first())
            val first = app.freezer.freeze(id, "待主管退审补扫"); app.submissions.upload(first.id); admin.review(first, "REJECT"); app.submissions.upload(first.id)
            admin.recall(prepared.orderId); app.recalls.check(id); app.recoveryFreezer.freeze(id, app.recoveryFreezer.fingerprint(app.recoveryFreezer.unresolved(id))); app.recalls.upload(id)
            val recovery = app.database.recalls().get(id)!!; val next = app.database.work().order(id)!!.nextOrderScanSeq
            val approval = admin.continueWork(prepared.orderId, first.id); proxy.loseContinuationResponse.set(true)
            assertThrows(Exception::class.java) { app.continuations.activate(id) }; assertEquals("RECALLED", app.database.work().order(id)!!.state)
            rollbackActivation(app, id, first.id)
            activateFromScreen(app, id)
            val continuation = app.database.continuations().current(id)!!
            assertEquals(approval.getString("expiresAt"), JSONObject(continuation.authorizationJson).getString("expiresAt"))
            assertEquals(next, app.database.work().order(id)!!.nextOrderScanSeq); assertEquals(first.payload, app.database.submissions().get(first.id)!!.payload)
            assertNull(app.database.submissions().forOrder(id)); assertNull(app.database.recalls().header(id))
            val store = WorkStore(app.database, SessionWorkAccess(app, app.session))
            assertThrows(IllegalStateException::class.java) { WorkCorrections(store).voidLast(id, app.database.scans().lastActive(id)!!.captureId, "禁止直接卸保留包") }
            secondRound(app, id, codes.last()); val second = app.freezer.freeze(id, "实际库存不足，主管核验短装")
            val body = JSONObject(second.payload); assertNotEquals(first.id, second.id); assertEquals("2", body.getString("workRound"))
            assertEquals(id, body.getJSONArray("captures").getJSONObject(0).getString("assignmentId"))
            assertEquals(JSONObject(continuation.authorizationJson).getString("id"), body.getJSONArray("captures").getJSONObject(2).getString("assignmentId"))
            app.submissions.upload(second.id); admin.review(second, "APPROVE"); assertEquals("SYNCED", app.submissions.upload(second.id).state)
            assertEquals("COMPLETED", admin.status(prepared.orderId)); app.work.pause(); app.database.close()
            val db = PdaDatabase.open(context)
            try {
                assertEquals(first.payload, db.submissions().get(first.id)!!.payload); assertEquals(second.payload, db.submissions().get(second.id)!!.payload)
                assertEquals(2L, db.scans().total(id).count); assertEquals(continuation, db.continuations().current(id))
            } finally { db.close() }
            File(context.filesDir, "continuation-evidence.json").writeText(JSONObject().put("orderId", prepared.orderId).put("workRound", 2)
                .put("originalFactsPreserved", true).put("lostActivationRecovered", true).put("localRollbackVerified", true).put("nextSequence", next)
                .put("retainedVoidRejected", true).put("signedSuccessVerified", true).put("databaseReopenVerified", true).put("recoverySha256", recovery.sha256).toString())
        }
    }
    private fun rollbackActivation(app: PdaApplication, id: String, previous: String) {
        val db = app.database.openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_continuation BEFORE INSERT ON local_continuation BEGIN SELECT RAISE(ABORT,'test activation storage fault'); END")
        try {
            assertThrows(Exception::class.java) { app.continuations.activate(id) }
            assertNull(app.database.continuations().current(id)); assertEquals(previous, app.database.submissions().forOrder(id)!!.id)
            assertEquals("RECALLED", app.database.work().order(id)!!.state); assertNotNull(app.database.recalls().header(id))
        } finally { db.execSQL("DROP TRIGGER fail_continuation") }
    }
    private fun activateFromScreen(app: PdaApplication, id: String) {
        ActivityScenario.launch<RecallActivity>(Intent(app, RecallActivity::class.java).putExtra("assignmentId", id)).use { screen ->
            waitUntil { var enabled = false; screen.onActivity { enabled = it.findViewById<Button>(R.id.continueWork).isEnabled }; enabled }
            screen.onActivity { it.findViewById<Button>(R.id.continueWork).performClick() }
            onView(withText("核验并续作")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
            waitUntil { app.database.continuations().current(id) != null }
        }
    }
    private fun firstRound(app: PdaApplication, id: String, code: String) {
        app.work.resume(id); waitUntil { app.work.state.value.ready }; assertTrue(app.work.receive("bad", "BROADCAST"))
        waitUntil { app.work.state.value.candidate?.state == "INVALID" }; app.work.acknowledge(app.work.state.value.candidate!!.id)
        waitUntil { app.work.state.value.candidate == null && app.work.state.value.queued == 0 }; assertTrue(app.work.receive(code, "BROADCAST"))
        waitUntil { app.work.state.value.order?.actualCount == 1L && app.work.state.value.queued == 0 }; app.work.pause()
    }
    private fun secondRound(app: PdaApplication, id: String, code: String) {
        app.work.resume(id); waitUntil { app.work.state.value.ready }; assertTrue(app.work.receive(code, "BROADCAST"))
        waitUntil { app.work.state.value.order?.actualCount == 2L && app.work.state.value.queued == 0 }; app.work.pause()
    }
    private fun codes(app: PdaApplication, id: String): List<String> = app.database.openHelper.readableDatabase
        .query("SELECT barcode FROM prepared_bale WHERE assignmentId=? ORDER BY barcode", arrayOf(id)).use { buildList { while (it.moveToNext()) add(it.getString(0)) } }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Continuation state did not arrive", condition())
    }
}
