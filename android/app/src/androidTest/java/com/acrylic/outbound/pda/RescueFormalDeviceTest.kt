package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64

/** Consumes the retained registered FORMAL emulator identity, never a new synthetic signer. */
class RescueFormalDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "rescue-formal-device").apply { mkdirs() }
    private val ownPassword = "synthetic-own-rescue-passphrase"
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("rescueExport") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        assertNull(app.session.saved())
    }
    @Test fun stoppedServiceExportsOriginalSyncedFormalThroughProductAndSaf() {
        guard()
        val input = JSONObject(File(app.filesDir, "scan-adjustment/result.json").readText())
        val id = input.getString("assignmentId")
        val original = checkNotNull(app.database.submissions().forOrder(id))
        assertEquals("SYNCED", original.state)
        File(directory, "submission-before.json").writeText(original.payload)
        File(directory, "receipt-before.json").writeText(checkNotNull(original.receipt))
        val intent = Intent(app, RescueExportActivity::class.java).putExtra("source", "FORMAL").putExtra("localOrderId", id)
        ActivityScenario.launch<RescueExportActivity>(intent).use {
            unlock()
            onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.summary)).check(matches(withText(containsString("存在原冻结批次"))))
            onView(withId(R.id.packagePassword)).perform(scrollTo(), replaceText("synthetic-package-passphrase-only"), closeSoftKeyboard())
            onView(withId(R.id.generate)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
            onView(withId(R.id.copy)).perform(scrollTo(), click())
            File(directory, "ready-for-saf.txt").writeText("Actual chooser pending; target not verified")
            await(120) { onView(withId(R.id.copyState)).check(matches(withText(containsString("目标写入与回读一致")))) }
            preserve(id)
        }
        assertEquals(original, app.database.submissions().forOrder(id))
        assertNull(app.session.saved())
    }
    @Test fun processRestartRecoversOriginalFormalPackageWithoutExternalSuccess() {
        guard()
        val original = JSONObject(File(directory, "result.json").readText())
        val id = original.getString("assignmentId")
        val submission = checkNotNull(app.database.submissions().forOrder(id))
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use {
            unlock()
            onView(withId(R.id.recoverPackage)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("原私有包回读通过")))) }
            onView(withId(R.id.copyState)).check(matches(withText(containsString("目标与电脑副本尚未核验"))))
        }
        val recovered = RescueExportFiles(app).recover(original.getString("packageId"), access())
        assertEquals(original.getString("sha256"), recovered.sha256)
        assertEquals(submission, app.database.submissions().forOrder(id))
        File(directory, "restart.json").writeText(JSONObject().put("samePrivateBytes", true)
            .put("newExternalCopyVerified", false).put("businessUnchanged", true).toString())
    }
    @Test fun hostKillsProcessDuringDurableGeneration() {
        guard()
        val input = JSONObject(File(directory, "result.json").readText())
        val grant = access()
        val snapshot = RescueRoomSnapshot(app.database).read(RescueTarget("FORMAL", input.getString("assignmentId")), grant)
        val keys = DeviceKeys()
        val signer = RescueSigner(Base64.getDecoder().decode(keys.publicKey(false))) { text ->
            keys.sign(text)
            File(directory, "generation-awaiting-kill.txt").writeText("Real GENERATING journal saved; host must force-stop this process")
            Thread.sleep(120_000)
            error("Host did not interrupt generation; synthetic test must not pass")
        }
        RescueExportFiles(app).generate(snapshot, grant to signer, "synthetic-package-passphrase-only".toCharArray())
    }
    @Test fun interruptedGenerationRemainsIncompleteAfterNewProcess() {
        guard()
        val input = JSONObject(File(directory, "result.json").readText())
        val originalId = input.getString("packageId")
        val journals = File(app.noBackupFilesDir, "rescue-exports").listFiles { file -> file.extension == "json" }.orEmpty()
            .map { JSONObject(it.readText()) }
        val interrupted = journals.single { it.getString("state") == "GENERATING" }
        assertFalse(interrupted.has("sha256"))
        assertFalse(RescueExportFiles(app).available(access()).any { it.id == interrupted.getString("id") })
        assertEquals(input.getString("sha256"), RescueExportFiles(app).recover(originalId, access()).sha256)
        val original = checkNotNull(app.database.submissions().forOrder(input.getString("assignmentId")))
        assertEquals(File(directory, "submission-before.json").readText(), original.payload)
        assertEquals(File(directory, "receipt-before.json").readText(), original.receipt)
        assertEquals("SYNCED", original.state)
        File(directory, "generation-interrupted.json").writeText(JSONObject().put("interruptedId", interrupted.getString("id"))
            .put("advertisedAsGenerated", false).put("originalPackagePreserved", true).put("businessUnchanged", true).toString())
    }
    @Test fun hostKillsProcessDuringTargetReadback() {
        guard()
        val input = JSONObject(File(directory, "result.json").readText())
        val files = RescueExportFiles(app); val grant = access()
        val generated = files.recover(input.getString("packageId"), grant)
        files.copy(generated, android.net.Uri.parse("content://com.acrylic.outbound.pda.debug.test.interrupted-copy/encrypted-copy"), grant)
        error("Host did not interrupt target readback; this stage must not finish")
    }
    @Test fun interruptedCopyRemainsUnverifiedAfterNewProcess() {
        guard()
        val input = JSONObject(File(directory, "result.json").readText())
        val journal = JSONObject(File(app.noBackupFilesDir, "rescue-exports/${input.getString("packageId")}.json").readText())
        assertEquals("COPYING", journal.getString("state"))
        assertTrue(journal.getString("targetUri").contains("interrupted-copy"))
        val generated = RescueExportFiles(app).recover(input.getString("packageId"), access())
        assertEquals(input.getString("sha256"), generated.sha256)
        val original = checkNotNull(app.database.submissions().forOrder(input.getString("assignmentId")))
        assertEquals(File(directory, "submission-before.json").readText(), original.payload)
        assertEquals(File(directory, "receipt-before.json").readText(), original.receipt)
        File(directory, "copy-interrupted.json").writeText(JSONObject().put("state", journal.getString("state"))
            .put("newTargetVerified", false).put("originalPackagePreserved", true).put("businessUnchanged", true).toString())
    }
    private fun access(): RescueReadGrant {
        val provision = JSONObject(File(app.filesDir, "rescue-device/provision.json").readText())
        return RescueCredentials.shared(app, app.session).unlock(provision.getString("operatorId"), ownPassword.toCharArray())
    }
    private fun unlock() {
        await { onView(withId(R.id.unlock)).check(matches(isEnabled())) }
        onView(withId(R.id.credential)).perform(scrollTo(), replaceText(ownPassword), closeSoftKeyboard())
        onView(withId(R.id.unlock)).perform(scrollTo(), click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("只读范围核验通过")))) }
    }
    private fun preserve(id: String) {
        val files = RescueExportFiles(app)
        val generated = files.recover(files.available(access()).first().id, access())
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "result.json").writeText(JSONObject().put("packageId", generated.id).put("sha256", generated.sha256)
            .put("assignmentId", id).put("source", "FORMAL").put("serviceStopped", true).put("loggedOut", true)
            .put("targetReadBackVerified", true).put("computerVerified", false).put("localDeletionAllowed", false).toString())
    }
    private fun await(seconds: Long = 30, check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds)
        var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(150) } while (System.nanoTime() < end)
        throw AssertionError("FORMAL rescue product did not reach expected state", last)
    }
}
