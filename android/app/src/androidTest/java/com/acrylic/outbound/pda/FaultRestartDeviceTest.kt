package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.ui.WorkActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Fresh emulator device, actual product repositories/engine and real HTTP/MySQL. */
class FaultRestartDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "fault-restart")
    private val base = "http://127.0.0.1:19434/api/v1/"
    private fun input(): JSONObject {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("faultRestart") == "true")
        val root = JSONObject(File(directory, "private/input.json").readText())
        check(root.getString("purpose") == "SYNTHETIC_FAULT_RESTART_CONSUMERS")
        return root.getJSONObject("android")
    }
    @Test fun newDeviceClaimsDownloadsScansFreezesAndVerifiesOwnSuccess() {
        val f = input(); val web = FaultRestartWebClient(base, f); web.login(); web.withdrawAndPublish()
        assertTrue(app.session.settings.device.isBlank())
        val publicKey = DeviceKeys().publicKey()
        val registered = web.post("devices", JSONObject().put("deviceCode", UUID.randomUUID().toString())
            .put("name", "故障重发隔离新模拟器").put("publicKey", publicKey))
        app.session.settings.bind(base, registered.getString("deviceId"), publicKey)
        app.session.login(f.getString("username"), f.getString("password"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val assignment = app.repository.claim(web.detail().getJSONObject("order"), "隔离新机从头核对")
        assertNotEquals(f.getJSONObject("oldAssignment").getString("id"), assignment.getString("id"))
        assertNotEquals(f.getJSONObject("oldAssignment").getString("deviceId"), assignment.getString("deviceId"))
        assertEquals("1", assignment.getString("startOrderScanSeq")); assertEquals("2", assignment.getString("workRound"))
        val prepared = app.repository.prepare(assignment) { }
        assertNotNull(prepared.ackAt)
        app.sync.refreshOrders()
        val barcodes = (0 until f.getJSONObject("oldBody").getJSONArray("scans").length()).map {
            f.getJSONObject("oldBody").getJSONArray("scans").getJSONObject(it).getString("barcode") }
        scan(prepared.assignmentId, barcodes)
        val frozen = app.freezer.freeze(prepared.assignmentId, "")
        assertEquals("2", JSONObject(frozen.payload).getString("workRound"))
        assertEquals("2", JSONObject(frozen.payload).getString("expectedCount"))
        val success = app.submissions.upload(frozen.id)
        assertEquals("SYNCED", success.state); assertEquals(frozen.payload, success.payload)
        File(directory, "result.json").writeText(JSONObject().put("assignmentId", prepared.assignmentId).put("submissionId", success.id)
            .put("sha256", success.sha256).put("state", success.state).put("deviceId", prepared.deviceId)
            .put("productDownload", true).put("productEngine", true).put("localDeletionAllowed", false).toString())
    }
    private fun scan(assignment: String, barcodes: List<String>) {
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", assignment)).use {
            await { app.work.state.value.ready && app.work.state.value.queued == 0 }
            assertEquals(0L, app.work.state.value.order!!.actualCount)
            barcodes.forEachIndexed { index, barcode ->
                assertTrue(app.work.receive(barcode, "BROADCAST"))
                await { app.work.state.value.order?.actualCount == (index + 1).toLong() && app.work.state.value.queued == 0 }
            }
        }
    }
    @Test fun forceStoppedProcessRetainsVerifiedResultAndOriginalFrozenBytes() {
        input(); val result = JSONObject(File(directory, "result.json").readText())
        val local = checkNotNull(app.database.submissions().get(result.getString("submissionId")))
        assertEquals("SYNCED", local.state); assertEquals(result.getString("sha256"), local.sha256)
        assertNotNull(local.receipt); assertEquals(result.getString("deviceId"), app.session.requireSession().getString("deviceId"))
        File(directory, "restart.json").writeText(JSONObject().put("submissionId", local.id).put("state", local.state)
            .put("sha256", local.sha256).put("newProcess", true).put("localDeletionAllowed", false).toString())
    }
    @Test fun scanningDeviceConsumesSignedWithdrawalAndRetainsItsLocalOrder() {
        val f = input().getJSONObject("revocation"); val web = FaultRestartWebClient(base, f)
        web.login(); web.withdrawAndPublish()
        val assignment = app.repository.claim(web.detail().getJSONObject("order"), "隔离原机撤回消费")
        val prepared = app.repository.prepare(assignment) { }
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", prepared.assignmentId)).use {
            await { app.work.state.value.ready && app.work.state.value.queued == 0 }
            web.post("orders/${f.getString("orderId")}/change-requests", JSONObject().put("type", "FAULT_WITHDRAWAL")
                .put("expectedVersion", web.detail().getJSONObject("order").getString("rowVersion")).put("reason", "隔离原机扫码中撤回，无STOP"))
            assertEquals("WITHDRAWN", app.recalls.check(prepared.assignmentId)!!.state)
            assertEquals("RECALLED", app.database.work().order(prepared.assignmentId)!!.state)
            assertNotNull(app.database.preparations().order(prepared.assignmentId))
            assertThrows(IllegalStateException::class.java) { app.freezer.freeze(prepared.assignmentId, "") }
            File(directory, "revocation.json").writeText(JSONObject().put("assignmentId", prepared.assignmentId)
                .put("state", "WITHDRAWN").put("workState", "RECALLED").put("originalDeviceStopReceived", false).toString())
        }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(100)
        assertTrue("Product engine did not reach expected durable state: ${app.work.state.value}", condition())
    }
    @Test fun retainedWithdrawalReplaysIntoStoppedStateAfterNewProductProcess() {
        input(); val previous = JSONObject(File(directory, "revocation.json").readText())
        val assignment = previous.getString("assignmentId")
        val prepared = checkNotNull(app.database.preparations().order(assignment))
        val receipt = app.recalls.check(assignment)
        assertEquals("WITHDRAWN", receipt!!.state)
        assertEquals("RECALLED", app.database.work().order(assignment)!!.state)
        assertEquals(prepared, app.database.preparations().order(assignment))
        File(directory, "revocation-restart.json").writeText(JSONObject().put("assignmentId", assignment)
            .put("state", receipt.state).put("newProcess", true).put("originalDeviceStopReceived", false).toString())
    }
}
