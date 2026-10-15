package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.temporary.TemporaryVerifier
import com.acrylic.outbound.pda.ui.TemporaryPreparationActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** The retained emulator proves its original key to a new isolated HTTP server. */
class TemporaryPreparationRenewalDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun nextPreparationKeepsExpiredOriginalQualification() {
        val args = InstrumentationRegistry.getArguments()
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("temporaryRenewal") == "true")
        val run = requireNotNull(args.getString("evidenceRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,50}")))
        directory = File(app.filesDir, "temporary-renewal/$run")
        check(directory.mkdirs()) { "Preserve previous evidence" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(directory, "database-before.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        try { renewOriginal() } finally {
            File(directory, "database-after.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        }
    }

    private fun renewOriginal() {
        val original = requireNotNull(app.temporary.local())
        val prepared = requireNotNull(app.temporary.prepared(original))
        val manifest = TemporaryVerifier.manifest(JSONObject(requireNotNull(original.receiptJson)), JSONObject(original.signingKeyJson))
        val binding = File(app.noBackupFilesDir, "device-binding.json").readBytes()
        loginOriginal(original)
        assertTrue(Instant.parse(manifest.getString("expiresAt")).isBefore(app.session.now()))
        renewThroughProduct(original.key)
        assertEquals(original, app.database.temporary().request(original.key))
        assertEquals(prepared, app.temporary.prepared(original))
        assertArrayEquals(binding, File(app.noBackupFilesDir, "device-binding.json").readBytes())
        val next = requireNotNull(app.temporary.local())
        assertEquals(original.key, next.previousKey)
        assertNotEquals(original.key, next.key)
        val nextPrepared = requireNotNull(app.temporary.prepared(next))
        assertNotNull(nextPrepared.ackJson)
        val fresh = TemporaryVerifier.manifest(JSONObject(requireNotNull(next.receiptJson)), JSONObject(next.signingKeyJson))
        assertTrue(Instant.parse(fresh.getString("expiresAt")).isAfter(app.session.now()))
        File(directory, "result.json").writeText(JSONObject().put("originalKey", original.key).put("nextKey", next.key)
            .put("originalAuthorizationId", prepared.authorizationId).put("nextAuthorizationId", nextPrepared.authorizationId)
            .put("originalExpiresAt", manifest.getString("expiresAt")).put("nextExpiresAt", fresh.getString("expiresAt"))
            .put("deviceId", original.deviceId).put("originalUnchanged", true).toString(2))
    }

    private fun renewThroughProduct(originalKey: String) {
        ActivityScenario.launch<TemporaryPreparationActivity>(Intent(app, TemporaryPreparationActivity::class.java)).use {
            await { onView(withId(R.id.nextPreparation)).check(matches(isEnabled())) }
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(swipeUp(), swipeUp())
            settleViewport()
            onView(withId(R.id.nextPreparation)).check(matches(isCompletelyDisplayed())).perform(click())
            await { onView(withText("联网准备新资料")).check(matches(isDisplayed())) }
            onView(withId(android.R.id.button1)).perform(click())
            await {
                val next = requireNotNull(app.temporary.local())
                assertNotEquals(originalKey, next.key)
                assertNotNull(app.temporary.prepared(next)?.ackJson)
                onView(withId(R.id.status)).check(matches(withText("临时资料已保存 · 下载已确认")))
            }
        }
    }

    private fun settleViewport() {
        onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isAssignableFrom(android.widget.ScrollView::class.java)
            override fun getDescription() = "Wait for real swipe animation to stop before touching the button"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                var previous = view.scrollY
                var stable = 0
                repeat(20) {
                    controller.loopMainThreadForAtLeast(100)
                    stable = if (view.scrollY == previous && !view.isLayoutRequested) stable + 1 else 0
                    previous = view.scrollY
                    if (stable >= 3) return
                }
                error("Preparation viewport did not settle")
            }
        })
    }

    private fun loginOriginal(original: com.acrylic.outbound.pda.temporary.TemporaryRequest) {
        val login = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        app.session.login(login.getString("username"), login.getString("password"))
        val session = app.session.requireSession()
        assertEquals(original.userId, session.getString("userId"))
        assertEquals(original.deviceId, session.getString("deviceId"))
        assertEquals(original.warehouseId, session.getString("warehouseId"))
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60)
        var last: Throwable? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(100)
        } while (System.nanoTime() < end)
        throw AssertionError("Original preparation product flow did not finish", last)
    }
}
