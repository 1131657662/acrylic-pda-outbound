package com.acrylic.outbound.pda

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.RescueBundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Physical-device SAF evidence; USB host readback is recorded separately from DocumentsUI readback. */
class PhysicalSafCopyDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext

    @Test fun documentsUiCopyRoundTripsOnPhysicalDevice() {
        check(BuildConfig.DEBUG && Build.HARDWARE != "ranchu")
        check(InstrumentationRegistry.getArguments().getString("physicalSaf") == "true")
        val evidence = File(app.filesDir, "physical-saf")
        check(evidence.mkdirs() || evidence.isDirectory)
        val original = sourceBytes()
        val source = File(evidence, "source.pdarescue").apply { writeBytes(original) }
        val result = File(evidence, "result.json").apply { delete() }
        val intent = Intent(app, PhysicalSafCopyActivity::class.java)
            .putExtra(PhysicalSafCopyActivity.EXTRA_SOURCE, source.absolutePath)
            .putExtra(PhysicalSafCopyActivity.EXTRA_RESULT, result.absolutePath)
            .putExtra(PhysicalSafCopyActivity.EXTRA_TITLE, "pda-physical-saf-test.pdarescue")
        ActivityScenario.launch<PhysicalSafCopyActivity>(intent)
        awaitResult(result)
        val body = JSONObject(result.readText())
        assertTrue(body.optBoolean("copied"))
        assertEquals(RescueBundle.sha(original), body.getString("sha256"))
        assertTrue(source.readBytes().contentEquals(original))
        File(evidence, "summary.json").writeText(JSONObject()
            .put("hardware", Build.HARDWARE).put("android", Build.VERSION.RELEASE)
            .put("documentsUiReadback", true).put("usbHostReadback", false)
            .put("targetUri", body.getString("uri")).put("sha256", body.getString("sha256"))
            .put("externalStorageVerified", false).put("businessReceiptVerified", false)
            .put("localDeletionAllowed", false).toString())
    }

    private fun awaitResult(result: File) {
        val deadline = System.nanoTime() + 180_000_000_000L
        while (!result.isFile && System.nanoTime() < deadline) Thread.sleep(250)
        check(result.isFile) { "DocumentsUI did not return a result; preserve device state" }
    }

    private fun sourceBytes(): ByteArray = ByteArray(96_257) { index -> ((index * 31 + 7) % 251).toByte() }
}
