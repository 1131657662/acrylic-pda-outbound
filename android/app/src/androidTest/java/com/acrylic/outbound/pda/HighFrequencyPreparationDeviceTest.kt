package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.network.PdaCapabilities
import com.acrylic.outbound.pda.ui.TemporaryPreparationActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real HTTP admission and product preparation UI using explicitly synthetic isolated input. */
class HighFrequencyPreparationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "high-frequency-preparation")

    @Test fun admittedDevicePreparesSignedTemporaryDataThroughProductPage() {
        val input = input()
        val web = FaultRestartWebClient(input.getString("baseUrl"), input)
        web.login()
        register(web, input.getString("baseUrl"))
        app.session.login(input.getString("username"), input.getString("password"))
        assertTrue(PdaCapabilities.temporary(requireNotNull(app.session.saved())))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        ActivityScenario.launch<TemporaryPreparationActivity>(Intent(app, TemporaryPreparationActivity::class.java)).use {
            await { onView(withId(R.id.prepare)).check(matches(isEnabled())) }
            onView(withId(R.id.prepare)).perform(scrollTo(), click())
            await {
                val request = requireNotNull(app.temporary.local())
                assertNotNull(app.temporary.prepared(request)?.ackJson)
                onView(withId(R.id.status)).check(matches(withText("临时资料已保存 · 下载已确认")))
            }
        }
        evidence()
    }

    private fun input(): JSONObject {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("highFrequencyPreparation") == "true")
        check(!BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT && !BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(app.session.settings.device.isBlank()) { "Fresh isolated device required; preserve earlier facts" }
        check(directory.mkdirs()) { "Preserve earlier preparation evidence" }
        val input = JSONObject(File(app.filesDir, "high-frequency/private/input.json").readText())
        check(input.getString("purpose") == "SYNTHETIC_HIGH_FREQUENCY_UI")
        check(input.getString("baseUrl").matches(Regex("http://127\\.0\\.0\\.1:[1-9][0-9]{3,4}/api/v1/")))
        UUID.fromString(input.getString("warehouseId"))
        return input
    }

    private fun register(web: FaultRestartWebClient, base: String) {
        val publicKey = app.session.publicKey()
        val device = web.post("devices", JSONObject().put("deviceCode", "high-frequency-${UUID.randomUUID().toString().take(16)}")
            .put("name", "合成高频UI隔离模拟器").put("publicKey", publicKey))
        val id = device.getString("deviceId")
        web.post("devices/$id/temporary-order", JSONObject().put("expectedVersion", "1")
            .put("enabled", true).put("reason", "隔离合成输入：高频UI临时资料准备"))
        app.session.settings.bind(base, id, publicKey)
    }

    private fun evidence() {
        val request = requireNotNull(app.temporary.local())
        val row = requireNotNull(app.temporary.prepared(request))
        assertEquals(2, row.inventoryRows)
        assertNotNull(row.ackJson)
        File(directory, "result.json").writeText(JSONObject().put("purpose", "SYNTHETIC_HIGH_FREQUENCY_UI")
            .put("deviceId", request.deviceId).put("userId", request.userId).put("warehouseId", request.warehouseId)
            .put("requestKey", request.key).put("authorizationId", row.authorizationId)
            .put("inventoryRows", row.inventoryRows).put("downloadAcknowledged", true)
            .put("productPageUsed", true).put("localDeletionAllowed", false).toString(2))
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45)
        var last: Throwable? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            catch (error: IllegalArgumentException) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Isolated temporary preparation page did not become ready", last)
    }
}
