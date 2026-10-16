package com.acrylic.outbound.pda.queue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.*
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.security.SessionVault
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.TemporaryWorkActivity
import com.acrylic.outbound.pda.ui.WorkActivity
import com.acrylic.outbound.pda.update.UpdateGate
import org.hamcrest.Matchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual product Activities/application wiring, with an explicit synthetic session and clock anchor. */
class WorkActivityLifecycleDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val args get() = InstrumentationRegistry.getArguments()
    private lateinit var input: JSONObject
    private lateinit var directory: File
    private lateinit var source: String
    private lateinit var phase: String
    private val id get() = input.getString("id")
    private val captureId get() = input.getString("captureId")
    private val formal get() = source == "formal"
    private val ready get() = if (formal) app.work.state.value.ready else app.temporaryWork.state.value.ready
    private val queued get() = if (formal) app.work.state.value.queued else app.temporaryWork.state.value.queued
    private val candidate get() = if (formal) app.work.state.value.candidate?.id else app.temporaryWork.state.value.candidate?.id

    @Test fun pendingInputSurvivesActualActivityLifecycle() {
        prepare()
        val target = if (formal) WorkActivity::class.java else TemporaryWorkActivity::class.java
        val intent = Intent(app, target).putExtra(if (formal) "assignmentId" else "clientOrderId", id)
        ActivityScenario.launch<ComponentActivity>(intent).use { scenario ->
            awaitPending()
            assertThrows(Exception::class.java) { UpdateGate.beginInstall() }
            pause(scenario)
            scenario.moveToState(Lifecycle.State.RESUMED); awaitPending()
            scenario.recreate(); awaitPending()
            if (phase == "consume") consume(scenario)
        }
        await { !ready && queued == 0 }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        File(directory, "completed.json").writeText(JSONObject().put("source", source).put("phase", phase)
            .put("pid", android.os.Process.myPid()).put("captureId", captureId).put("id", id)
            .put("saved", saved()).put("captures", captures()).put("syntheticOnly", true)
            .put("actualProductActivity", true).put("realLoginOrServer", false).toString(2))
    }

    private fun prepare() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("activityLifecycle") == "synthetic-only")
        input = JSONObject(File(app.filesDir, "activity-lifecycle-input/input.json").readText())
        check(input.getBoolean("syntheticOnly"))
        source = input.getString("source"); check(source in setOf("formal", "temporary"))
        check(app.packageName == input.getString("testApplicationId"))
        check(app.packageName.matches(Regex("com\\.acrylic\\.outbound\\.pda\\.debug\\.lifecycle" +
            (if (formal) "fm" else "tm") + "[0-9]{2}")))
        phase = checkNotNull(args.getString("lifecyclePhase")).also { check(it in setOf("retain", "consume")) }
        directory = File(app.filesDir, "activity-lifecycle-evidence/$phase")
        check(directory.mkdirs()) { "Preserve existing lifecycle evidence" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val expected = input.getJSONObject("session")
        if (phase == "retain") {
            check(app.session.saved() == null)
            expected.put("timeAnchor", TrustedTime.anchor(input.getString("now"), TrustedTime.sample(app)))
            SessionVault(app).save(expected)
        }
        val actual = app.session.requireSession()
        for (key in listOf("userId", "deviceId", "warehouseId", "localSessionId"))
            assertEquals(expected.getString(key), actual.getString(key))
        assertEquals(0L, saved()); assertEquals(1, captures())
    }

    private fun awaitPending() {
        await { ready && queued == 0 && candidate == captureId }
        assertEquals("WAITING_CONFIRM", state(captureId))
        onView(withId(R.id.confirm)).check(matches(isEnabled()))
        onView(withId(R.id.candidate)).check(matches(withText(containsString("手输校验通过，确认后保存"))))
        assertEquals(0L, saved())
    }

    private fun pause(scenario: ActivityScenario<ComponentActivity>) {
        scenario.moveToState(Lifecycle.State.CREATED)
        await { !ready && queued == 0 }
        val before = captureRow()
        UpdateGate.enterBackground().close()
        UpdateGate.beginInstall(); UpdateGate.endInstall()
        broadcast()
        assertEquals(before, captureRow())
        assertEquals(1, captures()); assertEquals(0L, saved())
    }

    private fun consume(scenario: ActivityScenario<ComponentActivity>) {
        onView(withId(R.id.confirm)).perform(click())
        await { queued == 0 && saved() == 1L && candidate == null }
        assertEquals("COMMITTED", state(captureId))
        onView(withId(R.id.totals)).check(matches(withText(containsString("已保存 1"))))
        broadcast()
        await { queued == 0 && candidate != null }
        assertEquals(2, captures()); assertEquals("INVALID", state(checkNotNull(candidate)))
        onView(withId(R.id.candidate)).check(matches(withText(containsString("该条码已在本机订单中保存，不能重复装车"))))
        onView(withId(R.id.confirm)).perform(click())
        await { queued == 0 && candidate == null }
        scenario.moveToState(Lifecycle.State.CREATED)
        await { !ready && queued == 0 }; broadcast()
        assertEquals(1L, saved()); assertEquals(2, captures())
    }

    private fun state(capture: String): String? = if (formal) app.database.captures().get(capture)?.state
        else app.database.temporaryScans().capture(capture)?.state
    private fun captures() = if (formal) app.database.captures().page(id, 0, 10).size
        else app.database.temporaryScans().captures(id).size
    private fun saved() = if (formal) app.database.scans().total(id).count else app.temporaryStore().total(id).count
    private fun captureRow() = if (formal) app.database.captures().get(captureId).toString()
        else app.database.temporaryScans().capture(captureId).toString()

    private fun broadcast() {
        val completed = CountDownLatch(1)
        val last = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { completed.countDown() }
        }
        app.sendOrderedBroadcast(Intent(ScannerReceiver.ACTION).setPackage(app.packageName)
            .putExtra(ScannerReceiver.BARCODE_KEY, input.getString("barcode")), null, last, null, 0, null, null)
        check(completed.await(10, TimeUnit.SECONDS))
    }

    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition()) { check(System.nanoTime() < until) { "Product Activity lifecycle condition timed out" }; Thread.sleep(10) }
    }
}
