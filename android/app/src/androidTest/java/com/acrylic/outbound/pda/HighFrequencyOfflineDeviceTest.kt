package com.acrylic.outbound.pda

import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Existing isolated signed preparation, new original local IDs; no fabricated device identity. */
class HighFrequencyOfflineDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "high-frequency").apply { mkdirs() }
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("highFrequency") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    @Test fun realHomeNavigationKeepsMaintenanceOutsideWorkPage() {
        guard()
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            await { onView(withId(R.id.workPage)).check(matches(isEnabled())) }
            onView(withId(R.id.workPage)).check(matches(isDisplayed()))
            onView(withId(R.id.appUpdate)).check(matches(not(isDisplayed())))
            onView(withId(R.id.myPage)).perform(click())
            onView(withId(R.id.appUpdate)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.myPage)).perform(scrollTo())
            onView(withId(R.id.dataPage)).perform(click())
            await { onView(withId(R.id.historySection)).check(matches(isEnabled())) }
            onView(withId(R.id.temporaryPreparation)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.dataPage)).perform(scrollTo())
            onView(withId(R.id.workPage)).perform(click())
            await { onView(withId(R.id.workPage)).check(matches(isEnabled())) }
            onView(withId(R.id.appUpdate)).check(matches(not(isDisplayed())))
            it.onActivity { activity ->
                val removed = activity.resources.getIdentifier("handover", "id", activity.packageName)
                assertTrue(removed == 0 || activity.findViewById<android.view.View>(removed) == null)
            }
            shot("home-work")
        }
    }
    @Test fun independentDraftSearchEditThenManualConfirmPauseAndReturnPreserveFacts() {
        guard()
        val store = app.temporaryStore(); val id = UUID.randomUUID().toString()
        TemporaryDraftRepository(store).create(id)
        val codes = store.source(id).bales.keys.filter { app.database.temporaryScans().guard(it) == null }.take(1)
        assertEquals(1, codes.size)
        File(directory, "attempt-order-id.txt").writeText(id)
        editDraft(id, codes)
        scan(id, codes)
        assertEquals(1L, store.total(id).count)
        assertEquals("SCANNING", app.database.temporaryDrafts().get(id)?.state)
        assertNull(app.database.temporarySubmissions().forOrder(id))
        File(directory, "new-order-id.txt").writeText(id)
    }
    private fun editDraft(id: String, codes: List<String>) {
        ActivityScenario.launch<TemporaryDraftActivity>(Intent(app, TemporaryDraftActivity::class.java).putExtra("clientOrderId", id)).use {
            await { onView(withId(R.id.add)).check(matches(isEnabled())) }
            onView(withId(R.id.vehicle)).perform(scrollTo(), replaceText("高频隔离车"), closeSoftKeyboard())
            onView(withId(R.id.reason)).perform(scrollTo(), replaceText("合成离线高频验证"), closeSoftKeyboard())
            codes.map { it.take(3) }.distinct().forEach { code ->
                onView(withId(R.id.productSearch)).perform(scrollTo(), replaceText(code), pressImeActionButton())
                shot("draft-search")
                onView(isRoot()).perform(swipeUp())
                onView(withId(R.id.count)).check(matches(isDisplayed()))
                onView(withId(R.id.count)).perform(replaceText("1"), pressImeActionButton())
                it.onActivity { activity ->
                    val spinner = activity.findViewById<android.widget.Spinner>(R.id.product)
                    File(directory, "before-add.json").writeText(JSONObject().put("selectedPosition", spinner.selectedItemPosition)
                        .put("selectedItem", spinner.selectedItem.toString()).put("count", activity.findViewById<android.widget.EditText>(R.id.count).text.toString()).toString(2))
                }
                onView(withId(R.id.add)).perform(scrollTo(), click())
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                shot("draft-added")
                onView(withText("修改 $code 数量")).check(matches(withText("修改 $code 数量")))
            }
            val code = codes.first().take(3)
            onView(withText("修改 $code 数量")).perform(scrollTo(), click())
            onView(isAssignableFrom(android.widget.EditText::class.java)).perform(replaceText("2"), closeSoftKeyboard())
            onView(withText("保存页面修改")).perform(click())
            onView(withId(R.id.dirty)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.save)).perform(scrollTo(), click())
            await { onView(withId(R.id.save)).check(matches(isEnabled())) }
            onView(withId(R.id.dirty)).check(matches(not(isDisplayed())))
            shot("draft-saved")
            onView(withId(R.id.start)).perform(scrollTo(), click())
            await { assertTrue(app.temporaryWork.state.value.ready) }
        }
    }
    private fun scan(id: String, codes: List<String>) {
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)).use { scenario ->
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.queued == 0) }
            onView(withId(R.id.manualToggle)).perform(scrollTo(), click())
            onView(withId(R.id.manual)).perform(scrollTo(), replaceText(codes.first()))
            onView(withId(R.id.validate)).perform(scrollTo(), click())
            await { onView(withId(R.id.confirm)).check(matches(isEnabled())) }
            onView(withId(R.id.message)).check(matches(withText(containsString("待确认输入 1"))))
            assertEquals(0L, app.temporaryStore().total(id).count)
            shot("manual-candidate"); dimensions("manual-candidate")
            scenario.onActivity { activity ->
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            await { assertEquals(1L, app.temporaryStore().total(id).count) }
            await { onView(withId(R.id.recentSaved)).check(matches(withText(containsString(codes.first())))) }
            pauseAndResume(id, codes.last())
            shot("saved-two")
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }
    private fun pauseAndResume(id: String, code: String) {
        val count = app.database.temporaryScans().captures(id).size
        onView(withId(R.id.pauseScan)).perform(scrollTo(), click())
        await { assertFalse(app.temporaryWork.state.value.ready) }
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1L, app.temporaryStore().total(id).count)
        assertEquals(count, app.database.temporaryScans().captures(id).size)
        onView(withId(R.id.pauseScan)).perform(scrollTo(), click())
        await { assertTrue(app.temporaryWork.state.value.ready) }
        app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
        await { assertEquals(count + 1, app.database.temporaryScans().captures(id).size) }
        await { onView(withId(R.id.confirm)).check(matches(withText(containsString("本包未保存")))) }
        assertEquals(1L, app.temporaryStore().total(id).count)
        onView(withId(R.id.confirm)).perform(scrollTo(), click())
    }
    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(100) } while (System.nanoTime() < end)
        throw AssertionError("High-frequency product page did not reach state", last)
    }
    private fun dimensions(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Record actual themed view dimensions"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val result = JSONObject().put("width", view.width).put("height", view.height).put("density", view.resources.displayMetrics.density)
                listOf(R.id.vehicle, R.id.source, R.id.totals, R.id.message, R.id.candidate, R.id.confirm, R.id.manualToggle, R.id.recentSaved, R.id.moreActions).forEach { id ->
                    val child = view.findViewById<android.view.View>(id); val location = IntArray(2); child.getLocationOnScreen(location)
                    result.put(view.resources.getResourceEntryName(id), JSONObject().put("height", child.height).put("y", location[1]))
                }
                File(directory, "$name-dimensions.json").writeText(result.toString(2))
            }
        })
    }
    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Record synthetic isolated product screen"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        })
    }
}
