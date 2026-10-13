package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.RescueExportActivity
import com.acrylic.outbound.pda.ui.SubmissionActivity
import com.acrylic.outbound.pda.ui.WorkActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Host-controlled stages retain one real emulator identity; no local capture or success seeding. */
class OriginalRelayFrozenDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "original-relay-frozen").apply { mkdirs() }
    private val assignment get() = JSONObject(File(directory, "frozen.json").readText()).getString("assignmentId")
    private val password = "synthetic-own-rescue-passphrase"

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("originalRelayFrozen") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        assertNull(app.session.saved())
    }

    @Test fun registeredOriginalDeviceCapturesAndFreezesThroughProductWithoutUploading() {
        guard()
        val input = JSONObject(File(directory, "input.json").readText())
        app.session.settings.verifyKey(app.session.publicKey())
        app.session.login(input.getString("username"), input.getString("password"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val order = app.session.call("pda/orders/${input.getString("orderId")}/claim-preview")
        val prepared = app.repository.prepare(app.repository.claim(order, "隔离原件中转")) { }
        app.sync.refreshOrders()
        scan(prepared.assignmentId, input)
        freeze(prepared.assignmentId)
        val local = checkNotNull(app.database.submissions().forOrder(prepared.assignmentId))
        assertEquals("FROZEN", local.state); assertNull(local.receipt); assertFalse(local.autoRetry)
        assertEquals(listOf(1L, 2L), app.database.scans().page(prepared.assignmentId, 0, 20).map { it.orderScanSeq })
        File(directory, "payload-before.json").writeText(local.payload)
        File(directory, "frozen.json").writeText(JSONObject().put("assignmentId", prepared.assignmentId)
            .put("submissionId", local.id).put("orderId", prepared.orderId).put("sha256", local.sha256)
            .put("nativeKeystore", true).put("productCaptured", true).put("uploaded", false).toString())
        app.session.logout()
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        assertNull(app.session.saved()); assertEquals(local, app.database.submissions().forOrder(prepared.assignmentId))
    }

    @Test fun loggedOutProductExportsOriginalFrozenBatchWithoutChangingState() {
        guard(); val before = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("FROZEN", before.state); assertNull(before.receipt)
        ActivityScenario.launch<RescueExportActivity>(intent()).use {
            unlock(); onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.packagePassword)).perform(scrollTo(), replaceText("synthetic-original-relay-package"), closeSoftKeyboard())
            onView(withId(R.id.generate)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
        }
        val provision = JSONObject(File(app.filesDir, "rescue-device/provision.json").readText())
        val grant = RescueCredentials.shared(app, app.session).unlock(provision.getString("operatorId"), password.toCharArray())
        val files = RescueExportFiles(app)
        val candidate = files.available(grant).first { it.summary.contains(assignment) }
        val generated = files.recover(candidate.id, grant)
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "export.json").writeText(JSONObject().put("packageId", generated.id).put("sha256", generated.sha256)
            .put("assignmentId", assignment).put("source", "FORMAL").put("nativeKeystore", true).put("productPage", true).toString())
        assertEquals(before, app.database.submissions().forOrder(assignment)); assertNull(app.session.saved())
    }

    @Test fun actualDocumentPickerConsumesNewSuccessForOriginalFrozenBatch() {
        guard(); val before = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("FROZEN", before.state); assertNull(before.receipt)
        ActivityScenario.launch<RescueExportActivity>(intent()).use {
            unlock(); onView(withId(R.id.importReceipt)).perform(scrollTo(), click())
            File(directory, "picker-ready.txt").writeText("Real OpenDocument pending; select actual PC result")
            await(120) { onView(withId(R.id.status)).check(matches(withText(containsString("原成功回执已验签并保存")))) }
        }
        val after = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("SYNCED", after.state); assertEquals(before.payload, after.payload); assertEquals(before.sha256, after.sha256)
        val raw = File(directory, "returned.pda-receipt.json").readBytes()
        val returned = JSONObject(raw.toString(Charsets.UTF_8))
        assertEquals(returned.getJSONObject("businessReceipt").toString(), JSONObject(checkNotNull(after.receipt)).toString())
        assertArrayEquals(raw, File(app.noBackupFilesDir, "original-relay-receipts/${RescueBundle.sha(raw)}.json").readBytes())
        assertFalse(returned.getBoolean("localDeletionAllowed")); assertNull(app.session.saved())
        File(directory, "import.json").writeText(JSONObject().put("actualOpenDocument", true).put("fromFrozen", true)
            .put("originalPayloadUnchanged", true).put("state", after.state).put("localDeletionAllowed", false).toString())
    }

    @Test fun newProcessShowsImportedSuccessAndRetainsOriginalBytes() {
        guard(); val local = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("SYNCED", local.state)
        assertEquals(File(directory, "payload-before.json").readText(), local.payload)
        val raw = File(directory, "returned.pda-receipt.json").readBytes()
        assertArrayEquals(raw, File(app.noBackupFilesDir, "original-relay-receipts/${RescueBundle.sha(raw)}.json").readBytes())
        assertEquals(JSONObject(raw.toString(Charsets.UTF_8)).getJSONObject("businessReceipt").toString(), JSONObject(checkNotNull(local.receipt)).toString())
        ActivityScenario.launch<SubmissionActivity>(Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", assignment)).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("云端已确认出库")))) }
            onView(withId(R.id.freeze)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
        }
        assertEquals(local, app.database.submissions().forOrder(assignment)); assertNull(app.session.saved())
        File(directory, "restart.json").writeText(JSONObject().put("newProcess", true).put("sameReturnedBytes", true)
            .put("originalPayloadUnchanged", true).put("productSuccessVisible", true).toString())
    }

    private fun scan(id: String, input: JSONObject) {
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", id)).use {
            await { assertTrue(app.work.state.value.ready && app.work.state.value.queued == 0) }
            val barcodes = input.getJSONArray("barcodes")
            for (index in 0 until barcodes.length()) {
                app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, barcodes.getString(index)))
                await { assertEquals((index + 1).toLong(), app.work.state.value.order?.actualCount); assertEquals(0, app.work.state.value.queued) }
            }
        }
    }

    private fun freeze(id: String) {
        ActivityScenario.launch<SubmissionActivity>(Intent(app, SubmissionActivity::class.java).putExtra("assignmentId", id)).use {
            await { onView(withId(R.id.freeze)).check(matches(isEnabled())) }
            onView(withId(R.id.freeze)).perform(scrollTo(), click())
            onView(withText("确认冻结")).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("尚未上传"))))
                assertEquals("FROZEN", app.database.submissions().forOrder(id)?.state) }
        }
    }

    private fun intent() = Intent(app, RescueExportActivity::class.java).putExtra("source", "FORMAL").putExtra("localOrderId", assignment)
    private fun unlock() {
        await { onView(withId(R.id.unlock)).check(matches(isEnabled())) }
        onView(withId(R.id.credential)).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
        onView(withId(R.id.unlock)).perform(scrollTo(), click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("只读范围核验通过")))) }
    }
    private fun await(seconds: Long = 30, check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(150) } while (System.nanoTime() < end)
        throw AssertionError("Frozen original relay product did not reach expected state", last)
    }
}
