package com.acrylic.outbound.pda

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Real native credential/time and Room; only account, catalog and vehicle facts are synthetic. */
internal class IndependentWorkNativeFixture {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    val run = checkNotNull(args.getString("evidenceRun")).also { check(it.matches(Regex("(?:retry0[1234]-)?font-[1-4]"))) }
    val index = run.last().digitToInt()
    val barcode = "003A000000$index"
    val directory = File(F.app.filesDir, "independent-work-native/$run")
    private val repository = IndependentScanDeviceFixture.repository
    val taskId: String

    init {
        IndependentScanDeviceFixture.guard()
        check(args.getString("independentWorkNative") == "true")
        check(!directory.exists() && directory.mkdirs())
        val prepared = JSONObject(File(directory.parentFile, "preparation.json").readText())
        assertEquals(prepared.getInt("bootCount"), TrustedTime.sample(F.app).boot)
        val active = repository.activeProducts()
        val command = IndependentTaskCommand(UUID.randomUUID().toString(), active.first.id,
            "UI-NATIVE-$index", "合成隔离原生界面组合，不代表实物放行", mapOf(active.second.single().deliveryCode to 2L))
        taskId = repository.create(command, F.credentials.unlock(F.password.toCharArray())).task.id
        assertEquals(0L, task().state.actualCount)
    }

    fun launch() = ActivityScenario.launch<IndependentWorkActivity>(Intent(F.app, IndependentWorkActivity::class.java)
        .putExtra("taskId", taskId))
    fun task() = repository.read(taskId)
    fun captures() = F.app.database.independentScans().captures(taskId)
    fun ime(scenario: ActivityScenario<IndependentWorkActivity>, expected: Boolean) = F.await {
        scenario.onActivity { assertEquals(expected, imeVisible(it)) }
    }
    fun imeVisible(activity: IndependentWorkActivity) = ViewCompat.getRootWindowInsets(activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun key(code: Int) { instrumentation.sendKeyDownUpSync(code); instrumentation.waitForIdleSync() }
    fun heldEnter() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent --longpress KEYCODE_ENTER").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        instrumentation.waitForIdleSync()
    }

    fun traceKeys(scenario: ActivityScenario<IndependentWorkActivity>) = scenario.onActivity { activity ->
        val previous = activity.window.callback
        val events = org.json.JSONArray()
        activity.window.callback = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(android.view.Window.Callback::class.java)) { _, method, arguments ->
            val event = arguments?.firstOrNull() as? KeyEvent
            val result = method.invoke(previous, *(arguments ?: emptyArray()))
            if (method.name == "dispatchKeyEvent" && event != null) {
                events.put(JSONObject().put("key", event.keyCode).put("action", event.action)
                    .put("flags", event.flags).put("source", event.source).put("repeat", event.repeatCount)
                    .put("imeVisible", imeVisible(activity)).put("consumed", result)
                    .put("activityImeOpen", activity.javaClass.getDeclaredField("imeOpen").also { it.isAccessible = true }.getBoolean(activity)))
                File(directory, "key-events.json").writeText(events.toString(2))
            }
            result
        } as android.view.Window.Callback
    }

    fun top(scenario: ActivityScenario<IndependentWorkActivity>) {
        scenario.onActivity { (it.findViewById<View>(R.id.workBack).parent.parent as android.widget.ScrollView).scrollTo(0, 0) }
        instrumentation.waitForIdleSync()
    }
    fun capture(scenario: ActivityScenario<IndependentWorkActivity>, phase: String) {
        scenario.onActivity {
            val root = it.findViewById<View>(R.id.workBack).parent.parent as android.widget.ScrollView
            val ime = ViewCompat.getRootWindowInsets(root)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            File(directory, "$phase-insets.json").writeText(JSONObject().put("imeVisible", imeVisible(it))
                .put("imeBottom", ime).put("rootHeight", root.height).put("paddingBottom", root.paddingBottom)
                .put("visibleViewport", root.height - root.paddingTop - root.paddingBottom).toString(2))
            IndependentSkuDeviceEvidence.capture(it, directory, phase)
        }
    }
    fun typeWithIme() {
        F.click(R.id.workManualToggle)
        onView(withId(R.id.workManual)).perform(independentSkuScroll(), replaceText(barcode), click())
    }
    fun record() {
        val capture = captures().single()
        File(directory, "result.json").writeText(JSONObject().put("taskId", taskId).put("barcode", barcode)
            .put("actualCount", task().state.actualCount).put("captureId", capture.capture.id)
            .put("state", capture.processing.state).put("nativeIme", true).put("injectedPlatformKeys", true)
            .put("physicalKeysVerified", false).put("realHttpCredential", true).put("timeInjected", false)
            .put("localDeletionAllowed", false).toString(2))
    }
}
