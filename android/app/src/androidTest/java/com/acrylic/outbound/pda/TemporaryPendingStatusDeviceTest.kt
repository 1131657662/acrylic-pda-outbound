package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.DeviceSettingsActivity
import com.acrylic.outbound.pda.ui.TemporaryWorkActivity
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Retained synthetic native identity; normal product connection check restores trusted time. */
class TemporaryPendingStatusDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "temporary-pending-status")

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("temporaryPendingStatus") == "true")
        check(app.session.settings.server == "http://127.0.0.1:19781/api/v1/")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    @Test fun originalIdentityRevalidatesTimeThroughProductConnectionCheck() {
        guard(); check(directory.mkdirs()) { "Preserve previous attempt" }
        val before = app.session.requireSession()
        val key = app.session.publicKey()
        val database = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "revalidate-database-before.json").writeText(database)
        ActivityScenario.launch<DeviceSettingsActivity>(Intent(app, DeviceSettingsActivity::class.java)).use {
            await { onView(withId(R.id.checkConnection)).check(matches(isEnabled())) }
            onView(withId(R.id.checkConnection)).perform(independentSkuScroll(), click())
            await { assertTrue(runCatching { app.session.now() }.isSuccess) }
        }
        val after = app.session.requireSession()
        listOf("deviceId", "userId", "warehouseId", "localSessionId").forEach {
            assertEquals(before.getString(it), after.getString(it))
        }
        assertEquals(key, app.session.publicKey())
        assertEquals(database, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(directory, "revalidate-result.json").writeText(JSONObject().put("originalIdentityPreserved", true)
            .put("roomAndOriginalPreparationUnchanged", true).put("productConnectionCheck", true)
            .put("trustedTimeAvailable", true).put("localDeletionAllowed", false).toString(2))
    }

    @Test fun pendingManualInputStaysDistinctFromSavedCountOnActualWorkPage() {
        guard(); check(File(directory, "revalidate-result.json").isFile)
        val marker = File(directory, "order.txt"); check(!marker.exists()) { "Preserve original input" }
        val id = UUID.randomUUID().toString(); marker.writeText(id)
        File(directory, "database-before.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        val code = prepare(id)
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java)
            .putExtra("clientOrderId", id)).use { scenario ->
            exercise(id, code)
            scenario.recreate()
            await { onView(withId(R.id.recentSaved)).check(matches(withText(containsString(code)))) }
            onView(withId(R.id.message)).check(matches(not(withText(containsString("待确认输入 1")))))
        }
        assertEquals(1, app.database.temporaryScans().captures(id).size)
        assertEquals(1, app.database.temporaryScans().allScans(id).size)
        assertNull(app.database.temporarySubmissions().forOrder(id))
        File(directory, "database-after.json").writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
        File(directory, "result.json").writeText(JSONObject().put("clientOrderId", id).put("barcode", code)
            .put("pendingCountBeforeSave", 1).put("savedCountBeforeConfirmation", 0).put("savedCountAfterConfirmation", 1)
            .put("newCaptures", 1).put("noSubmission", true).put("localDeletionAllowed", false).toString(2))
    }

    private fun prepare(id: String): String {
        val store = app.temporaryStore(); val repo = TemporaryDraftRepository(store); val draft = repo.create(id)
        val code = store.source(id).bales.keys.first {
            app.database.temporaryScans().guard(it) == null && app.database.scans().guard(it) == null
        }
        val saved = repo.save(id, TemporaryPlanInput(draft.revision, "待确认状态隔离车", "", "合成状态验证", listOf(code.take(3) to "1")))
        repo.start(id, saved.revision)
        return code
    }

    private fun exercise(id: String, code: String) {
        await { assertTrue(app.temporaryWork.state.value.ready) }
        onView(withId(R.id.manualToggle)).perform(scrollTo(), click())
        onView(withId(R.id.manual)).perform(scrollTo(), replaceText(code), closeSoftKeyboard())
        onView(withId(R.id.validate)).perform(scrollTo(), click())
        await {
            assertEquals("WAITING_CONFIRM", app.temporaryWork.state.value.candidate?.state)
            onView(withId(R.id.message)).check(matches(withText(containsString("待确认输入 1"))))
        }
        assertEquals(0L, app.temporaryStore().total(id).count)
        onView(withId(R.id.totals)).check(matches(withText(containsString("已保存 0 / 计划 1"))))
        onView(withId(R.id.message)).check(matches(not(withText(containsString("待处理 0")))))
        onView(withId(R.id.confirm)).perform(scrollTo(), click())
        await { assertEquals(1L, app.temporaryStore().total(id).count) }
        await { onView(withId(R.id.recentSaved)).check(matches(withText(containsString(code)))) }
        onView(withId(R.id.totals)).check(matches(withText(containsString("已保存 1 / 计划 1"))))
        onView(withId(R.id.message)).check(matches(not(withText(containsString("待确认输入 1")))))
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(50)
        } while (System.nanoTime() < end)
        throw AssertionError("Temporary status did not reach expected state", last)
    }
}
