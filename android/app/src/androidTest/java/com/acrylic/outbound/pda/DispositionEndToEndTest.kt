package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DispositionEndToEndTest {
    @Test fun unloadActivationSurvivesLostResponseAndRollbackThenSubmitsEffectiveBales() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val app = context.applicationContext as PdaApplication
        val file = File(context.filesDir, "disposition-fixture.json"); val fixture = JSONObject(file.readText()); file.delete()
        val admin = RecallAdmin(fixture)
        SubmissionProxy(fixture.getString("baseUrl")).use { proxy ->
            val key = app.session.publicKey(); val device = admin.register(key)
            app.session.settings.bind(proxy.base, device, key); app.session.login(fixture.getString("username"), fixture.getString("password"))
            val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
            val a = app.repository.claim(order, "实物处置模拟器验证"); val prepared = app.repository.prepare(a) {}; val id = prepared.assignmentId
            val codes = codes(app, id); scan(app, id, codes.first(), 1)
            val first = app.freezer.freeze(id, "待主管核验卸货"); app.submissions.upload(first.id); admin.review(first, "REJECT"); app.submissions.upload(first.id)
            admin.recall(prepared.orderId); app.recalls.check(id)
            app.recoveryFreezer.freeze(id, app.recoveryFreezer.fingerprint(app.recoveryFreezer.unresolved(id))); app.recalls.upload(id)
            val oldScan = app.database.scans().page(id, 0, 100).single(); val recovery = app.database.recalls().get(id)!!
            admin.continueWork(prepared.orderId, first.id, "RELEASE"); proxy.loseContinuationResponse.set(true)
            assertThrows(Exception::class.java) { app.continuations.activate(id) }; assertEquals(1L, app.database.work().order(id)!!.actualCount)
            val db = app.database.openHelper.writableDatabase
            db.execSQL("CREATE TRIGGER fail_disposition BEFORE INSERT ON local_continuation BEGIN SELECT RAISE(ABORT,'test storage fault'); END")
            try {
                assertThrows(Exception::class.java) { app.continuations.activate(id) }
                assertTrue(app.database.dispositions().all(id).isEmpty()); assertEquals(1L, app.database.work().order(id)!!.actualCount)
                assertEquals("RECALLED", app.database.work().order(id)!!.state)
            } finally { db.execSQL("DROP TRIGGER fail_disposition") }
            app.continuations.activate(id)
            assertEquals(0L, app.database.work().order(id)!!.actualCount); assertEquals(oldScan, app.database.scans().get(oldScan.captureId))
            assertNotNull(app.database.scans().guard(oldScan.barcode)); assertEquals(first.payload, app.database.submissions().get(first.id)!!.payload)
            assertThrows(IllegalStateException::class.java) { app.freezer.freeze(id, "零有效包不能出库") }
            scan(app, id, codes.last(), 1); val second = app.freezer.freeze(id, "原包已按凭据卸下，当前有效装载短装出库")
            val body = JSONObject(second.payload); assertEquals(2, body.getJSONArray("scans").length()); assertEquals("1", body.getString("expectedCount"))
            assertEquals(1, body.getJSONArray("dispositions").length()); proxy.loseSubmissionResponse.set(true)
            runCatching { app.submissions.upload(second.id) }; app.submissions.upload(second.id)
            admin.review(second, "APPROVE"); assertEquals("SYNCED", app.submissions.upload(second.id).state)
            assertEquals("COMPLETED", admin.status(prepared.orderId)); app.work.pause(); app.database.close()
            val reopened = PdaDatabase.open(context)
            try {
                assertEquals(1L, reopened.scans().total(id).count); assertEquals(1, reopened.dispositions().all(id).size)
                assertEquals(oldScan, reopened.scans().get(oldScan.captureId)); assertEquals(second.payload, reopened.submissions().get(second.id)!!.payload)
            } finally { reopened.close() }
            File(context.filesDir, "disposition-evidence.json").writeText(JSONObject().put("orderId", prepared.orderId).put("workRound", 2)
                .put("effectiveCount", 1).put("historicalScanCount", 2).put("originalFactsPreserved", true).put("guardPreserved", true)
                .put("lostActivationRecovered", true).put("localRollbackVerified", true).put("zeroBaleRejected", true)
                .put("signedSuccessVerified", true).put("databaseReopenVerified", true).put("recoverySha256", recovery.sha256).toString())
        }
    }
    private fun scan(app: PdaApplication, id: String, code: String, count: Long) {
        app.work.resume(id); waitUntil { app.work.state.value.ready }; assertTrue(app.work.receive(code, "BROADCAST"))
        waitUntil { app.work.state.value.order?.actualCount == count && app.work.state.value.queued == 0 }; app.work.pause()
    }
    private fun codes(app: PdaApplication, id: String): List<String> = app.database.openHelper.readableDatabase
        .query("SELECT barcode FROM prepared_bale WHERE assignmentId=? ORDER BY barcode", arrayOf(id)).use { buildList { while (it.moveToNext()) add(it.getString(0)) } }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Disposition state did not arrive", condition())
    }
}
