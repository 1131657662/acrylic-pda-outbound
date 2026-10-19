package com.acrylic.outbound.pda

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.IndependentWorkRuntime
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Reopen one retained failed task; record native UI without replacing authorization or task state. */
class IndependentWorkNativeDiagnosticTest {
    @Test fun retainedPendingTaskObservesPlatformEnter() {
        IndependentScanDeviceFixture.guard()
        val args = InstrumentationRegistry.getArguments()
        val taskId = checkNotNull(args.getString("retainedTaskId"))
        val run = checkNotNull(args.getString("diagnosticRun"))
        check(run.matches(Regex("diagnostic-key-[1-9]")))
        val directory = File(F.app.filesDir, "independent-work-native/$run")
        check(!directory.exists() && directory.mkdirs())
        val intent = Intent(F.app, IndependentWorkActivity::class.java).putExtra("taskId", taskId)
        ActivityScenario.launch<IndependentWorkActivity>(intent).use { scenario ->
            F.await { assertEquals(taskId, IndependentWorkRuntime.shared(F.app).state.value.task?.task?.id) }
            IndependentScanDeviceFixture.text(R.id.workPassword, F.password)
            F.click(R.id.workAuthorize)
            F.await {
                val state = IndependentWorkRuntime.shared(F.app).state.value
                assertEquals("", state.blocked); assertEquals("WAITING_CONFIRM", state.candidate?.processing?.state)
            }
            record(scenario, directory, "before-enter")
            traceKeys(scenario, directory)
            heldEnter()
            try {
                F.await { assertEquals(1L, IndependentScanDeviceFixture.task(taskId).state.actualCount) }
            } finally {
                record(scenario, directory, "after-enter")
            }
        }
    }

    @Test fun retainedTaskAuthorizationObservesNativeClick() {
        IndependentScanDeviceFixture.guard()
        val args = InstrumentationRegistry.getArguments()
        val taskId = checkNotNull(args.getString("retainedTaskId"))
        val run = checkNotNull(args.getString("diagnosticRun"))
        check(run.matches(Regex("diagnostic-auth-[1-9]")))
        val directory = File(F.app.filesDir, "independent-work-native/$run")
        check(!directory.exists() && directory.mkdirs())
        val intent = Intent(F.app, IndependentWorkActivity::class.java).putExtra("taskId", taskId)
        ActivityScenario.launch<IndependentWorkActivity>(intent).use { scenario ->
            F.await { assertEquals(taskId, IndependentWorkRuntime.shared(F.app).state.value.task?.task?.id) }
            IndependentScanDeviceFixture.text(R.id.workPassword, F.password)
            record(scenario, directory, "before-click")
            F.click(R.id.workAuthorize)
            record(scenario, directory, "after-click")
            try {
                F.await { assertTrue(IndependentWorkRuntime.shared(F.app).state.value.ready) }
            } finally {
                record(scenario, directory, "settled")
            }
        }
    }

    private fun record(scenario: ActivityScenario<IndependentWorkActivity>, directory: File, name: String) {
        scenario.onActivity { activity ->
            val engine = IndependentWorkRuntime.shared(F.app)
            val state = engine.state.value
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
            val password = activity.findViewById<EditText>(R.id.workPassword)
            val button = activity.findViewById<View>(R.id.workAuthorize)
            File(directory, "$name-state.json").writeText(JSONObject().put("taskId", state.task?.task?.id)
                .put("taskState", state.task?.state?.state).put("actualCount", state.task?.state?.actualCount)
                .put("ready", state.ready).put("blocked", state.blocked).put("message", state.message)
                .put("queued", state.queued).put("scanning", engine.scanning)
                .put("candidate", state.candidate?.processing?.state)
                .put("imeVisible", insets.isVisible(WindowInsetsCompat.Type.ime()))
                .put("imeBottom", insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                .put("passwordCharacters", password.text.length).put("authorizeEnabled", button.isEnabled)
                .put("authorizeText", activity.findViewById<TextView>(R.id.workAuthorize).text).toString(2))
            IndependentSkuDeviceEvidence.capture(activity, directory, name)
        }
    }

    private fun heldEnter() {
        val time = android.os.SystemClock.uptimeMillis()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        listOf(android.view.KeyEvent(time, time, 0, 66, 0),
            android.view.KeyEvent(time, time + 30, 0, 66, 1),
            android.view.KeyEvent(time, time + 60, 1, 66, 0)).forEach { instrumentation.sendKeySync(it) }
        instrumentation.waitForIdleSync()
    }

    private fun traceKeys(scenario: ActivityScenario<IndependentWorkActivity>, directory: File) = scenario.onActivity { activity ->
        val previous = activity.window.callback
        val events = org.json.JSONArray()
        activity.window.callback = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(android.view.Window.Callback::class.java)) { _, method, arguments ->
            val event = arguments?.firstOrNull() as? android.view.KeyEvent
            val result = method.invoke(previous, *(arguments ?: emptyArray()))
            if (method.name == "dispatchKeyEvent" && event != null) {
                events.put(JSONObject().put("key", event.keyCode).put("action", event.action)
                    .put("flags", event.flags).put("source", event.source).put("repeat", event.repeatCount)
                    .put("imeVisible", ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime())).put("consumed", result)
                    .put("activityImeOpen", activity.javaClass.getDeclaredField("imeOpen")
                        .also { it.isAccessible = true }.getBoolean(activity)))
                File(directory, "key-events.json").writeText(events.toString(2))
            }
            result
        } as android.view.Window.Callback
    }
}
