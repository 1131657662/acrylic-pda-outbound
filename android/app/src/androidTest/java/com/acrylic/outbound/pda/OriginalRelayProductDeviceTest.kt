package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Existing emulator identity and Keystore only; host transfers actual exported/returned bytes. */
class OriginalRelayProductDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "original-relay-product").apply { mkdirs() }
    private val assignment get() = JSONObject(File(app.filesDir, "rescue-formal-device/result.json").readText()).getString("assignmentId")
    private val password = "synthetic-own-rescue-passphrase"

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("originalRelayProduct") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        assertNull(app.session.saved())
    }

    @Test fun productGeneratesNewOriginalWithExistingKeystoreAndFrozenFacts() {
        guard(); val before = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("SYNCED", before.state)
        ActivityScenario.launch<RescueExportActivity>(intent()).use {
            unlock()
            onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.packagePassword)).perform(scrollTo(), replaceText("synthetic-original-relay-package"), closeSoftKeyboard())
            onView(withId(R.id.generate)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
        }
        val provision = JSONObject(File(app.filesDir, "rescue-device/provision.json").readText())
        val grant = RescueCredentials.shared(app, app.session).unlock(provision.getString("operatorId"), password.toCharArray())
        val files = RescueExportFiles(app); val generated = files.recover(files.available(grant).first().id, grant)
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "payload-before.json").writeText(before.payload)
        File(directory, "business-before.json").writeText(checkNotNull(before.receipt))
        File(directory, "export.json").writeText(JSONObject().put("packageId", generated.id).put("sha256", generated.sha256)
            .put("assignmentId", assignment).put("source", "FORMAL").put("nativeKeystore", true).put("productPage", true).toString())
        assertEquals(before, app.database.submissions().forOrder(assignment))
    }

    @Test fun productOpensActualDocumentPickerAndConsumesReturnedOriginal() {
        guard(); val before = checkNotNull(app.database.submissions().forOrder(assignment))
        ActivityScenario.launch<RescueExportActivity>(intent()).use {
            unlock()
            onView(withId(R.id.importReceipt)).perform(scrollTo(), click())
            File(directory, "picker-ready.txt").writeText("Real OpenDocument pending; host selects actual downloaded file")
            await(120) { onView(withId(R.id.status)).check(matches(withText(containsString("原成功回执已验签并保存")))) }
        }
        val after = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("SYNCED", after.state); assertEquals(before.payload, after.payload)
        assertEquals(JSONObject(checkNotNull(before.receipt)).toString(), JSONObject(checkNotNull(after.receipt)).toString())
        assertEquals(before, after); assertNull(app.session.saved())
        val retained = File(app.noBackupFilesDir, "original-relay-receipts").listFiles().orEmpty()
        assertTrue(retained.isNotEmpty())
        File(directory, "import.json").writeText(JSONObject().put("actualOpenDocument", true).put("fixedPreparedKey", true)
            .put("nativeKeystore", true).put("originalPayloadUnchanged", true).put("localDeletionAllowed", false).toString())
    }

    @Test fun newProcessReadsOriginalSuccessAndRetainedRelayFile() {
        guard(); val local = checkNotNull(app.database.submissions().forOrder(assignment))
        assertEquals("SYNCED", local.state)
        assertEquals(File(directory, "payload-before.json").readText(), local.payload)
        assertEquals(JSONObject(File(directory, "business-before.json").readText()).toString(), JSONObject(checkNotNull(local.receipt)).toString())
        ActivityScenario.launch<RescueExportActivity>(intent()).use {
            unlock(); onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.summary)).check(matches(withText(containsString("存在原冻结批次")))) }
        }
        val receipt = JSONObject(File(directory, "returned.pda-receipt.json").readText())
        // Host records exact returned bytes; parsing/reserialization must never define the retained file hash.
        val raw = File(directory, "returned.pda-receipt.json").readBytes()
        assertArrayEquals(raw, File(app.noBackupFilesDir, "original-relay-receipts/${RescueBundle.sha(raw)}.json").readBytes())
        assertFalse(receipt.getBoolean("localDeletionAllowed"))
        File(directory, "restart.json").writeText(JSONObject().put("newProcess", true).put("sameReturnedBytes", true)
            .put("originalPayloadUnchanged", true).put("originalSuccessUnchanged", true).toString())
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
        throw AssertionError("Original relay product did not reach expected state", last)
    }
}
