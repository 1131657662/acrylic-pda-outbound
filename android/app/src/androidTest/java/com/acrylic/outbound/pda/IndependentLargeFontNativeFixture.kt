package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.IndependentTaskCommand
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

internal class IndependentLargeFontNativeFixture {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val index = checkNotNull(InstrumentationRegistry.getArguments().getString("largeFontIndex")).toInt().also { check(it in 1..4) }
    val barcode = "003A000001$index"
    val directory = File(F.app.filesDir, "independent-large-font/font-$index")
    private val repository = IndependentScanDeviceFixture.repository
    val taskId: String

    init {
        IndependentScanDeviceFixture.guard()
        check(InstrumentationRegistry.getArguments().getString("independentLargeFont") == "true")
        check(!directory.exists() && directory.mkdirs())
        val prepared = JSONObject(File(directory.parentFile, "renewal.json").readText())
        assertEquals(prepared.getInt("bootCount"), TrustedTime.sample(F.app).boot)
        val active = repository.activeProducts()
        val command = IndependentTaskCommand(UUID.randomUUID().toString(), active.first.id,
            "沪A12345", "合成大字号验证，不代表实物放行", mapOf(active.second.single().deliveryCode to 100L))
        taskId = repository.create(command, F.credentials.unlock(F.password.toCharArray())).task.id
        assertEquals(0L, task().state.actualCount)
    }

    fun launch() = ActivityScenario.launch<IndependentWorkActivity>(Intent(F.app, IndependentWorkActivity::class.java).putExtra("taskId", taskId))
    fun task() = repository.read(taskId)
    fun captures() = F.app.database.independentScans().captures(taskId)
    fun ime(scenario: ActivityScenario<IndependentWorkActivity>, expected: Boolean) = F.await {
        scenario.onActivity {
            assertEquals(expected, checkNotNull(ViewCompat.getRootWindowInsets(it.window.decorView)).isVisible(WindowInsetsCompat.Type.ime()))
        }
    }

    fun captureFirstScreen(scenario: ActivityScenario<IndependentWorkActivity>, phase: String) {
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val root = activity.findViewById<ScrollView>(R.id.workScroll)
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
            assertFalse(insets.isVisible(WindowInsetsCompat.Type.ime()))
            assertEquals("product must return to top without test scrolling", 0, root.scrollY)
            val ids = listOf(R.id.workBack, R.id.workVehicle, R.id.workWarning, R.id.workSummary,
                R.id.workPause, R.id.workDetailsToggle) + if (phase == "pending") listOf(R.id.workCandidate, R.id.workConfirm) else listOf(R.id.workRecent)
            val rows = JSONArray()
            ids.forEach { rows.put(visibleRect(root, activity.findViewById(it))) }
            IndependentSkuDeviceEvidence.capture(activity, directory, phase)
            File(directory, "$phase-viewport.json").writeText(JSONObject().put("views", rows)
                .put("fontScale", activity.resources.configuration.fontScale).put("rootHeight", root.height)
                .put("paddingTop", root.paddingTop).put("paddingBottom", root.paddingBottom).put("scrollY", root.scrollY).toString(2))
        }
    }

    private fun visibleRect(root: ScrollView, view: TextView): JSONObject {
        val rect = Rect(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, rect)
        assertTrue("${view.id} outside usable viewport $rect", view.isShown && rect.top >= root.paddingTop && rect.bottom <= root.height - root.paddingBottom)
        val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        (0 until view.layout.lineCount).forEach {
            assertEquals(0, view.layout.getEllipsisCount(it))
            assertTrue(view.layout.getLineRight(it) <= available + 1)
        }
        return JSONObject().put("id", view.resources.getResourceEntryName(view.id)).put("text", view.text)
            .put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom)
    }

    fun record() {
        val capture = captures().single()
        File(directory, "result.json").writeText(JSONObject().put("taskId", taskId).put("barcode", barcode)
            .put("actualCount", task().state.actualCount).put("captureId", capture.capture.id)
            .put("state", capture.processing.state).put("nativeIme", true).put("touchConfirmation", true)
            .put("testScrollAfterManual", false).put("timeInjected", false).put("physicalKeysVerified", false).toString(2))
    }
}
