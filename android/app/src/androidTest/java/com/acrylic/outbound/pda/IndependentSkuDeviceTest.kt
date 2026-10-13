package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.IndependentSkuRepository
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentSkuActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Catalog-only evidence on the existing emulator; host chooses files through actual DocumentsUI. */
class IndependentSkuDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "independent-sku-device").apply { mkdirs() }
    private val repository get() = IndependentSkuRepository(app.database) { TrustedTime.sample(app) }
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("independentSku") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    @Test fun actualSafSelectionRequiresDuplicateConfirmationBeforeActivation() {
        guard()
        ActivityScenario.launch<IndependentSkuActivity>(Intent(app, IndependentSkuActivity::class.java)).use {
            await { onView(withId(R.id.choose)).check(matches(isEnabled())) }
            onView(withId(R.id.choose)).perform(independentSkuScroll(), click())
            File(directory, "choose-original.txt").writeText("Host must select E06_SKU_ORIGINAL.txt in actual DocumentsUI")
            await(90) { onView(withId(R.id.summary)).check(matches(withText(containsString("完全重复 1 行")))) }
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "before-activation-scroll") }
            onView(withId(R.id.activate)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("请明确确认")))) }
            onView(withId(R.id.confirmDuplicates)).perform(independentSkuScroll(), click())
            onView(withId(R.id.activate)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("资料已启用")))) }
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "activated") }
            it.recreate()
            await { onView(withId(R.id.status)).check(matches(withText(containsString("资料已启用")))) }
        }
        val active = checkNotNull(repository.active())
        assertEquals(2, active.preview!!.rows.size); assertEquals(1, active.preview!!.validRows)
        File(directory, "active.json").writeText(JSONObject().put("importId", active.imported.id).put("sha256", active.imported.sha256)
            .put("actualSaf", true).put("duplicatesConfirmed", active.decision!!.duplicatesConfirmed).toString())
        File(directory, "original.bin").writeBytes(repository.original(active.imported.id))
    }
    @Test fun badFileIsLocatedAndCancelledWithoutReplacingOriginal() {
        guard(); val old = checkNotNull(repository.active())
        ActivityScenario.launch<IndependentSkuActivity>(Intent(app, IndependentSkuActivity::class.java)).use {
            await { onView(withId(R.id.choose)).check(matches(isEnabled())) }
            onView(withId(R.id.choose)).perform(independentSkuScroll(), click())
            File(directory, "choose-bad.txt").writeText("Host must select E06_SKU_BAD.txt in actual DocumentsUI")
            await(90) { onView(withId(R.id.summary)).check(matches(withText(containsString("错误 1 行")))) }
            onView(withId(R.id.activate)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            onView(withId(R.id.issuesOnly)).perform(independentSkuScroll(), click())
            onView(withId(R.id.rows)).check(matches(withText(containsString("原第 2 行"))))
            onView(withId(R.id.rows)).perform(independentSkuScroll())
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "blocked-original-line") }
            onView(withId(R.id.cancel)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本次预检已取消")))) }
        }
        val cancelled = checkNotNull(repository.latest())
        assertEquals("CANCELLED", cancelled.decision!!.outcome)
        assertEquals(old.imported.id, repository.active()!!.imported.id)
        File(directory, "cancelled.json").writeText(JSONObject().put("importId", cancelled.imported.id)
            .put("activeImportId", old.imported.id).put("sha256", cancelled.imported.sha256).toString())
        File(directory, "bad.bin").writeBytes(repository.original(cancelled.imported.id))
    }
    @Test fun newProcessReadsOriginalAndSystemPickerCancelPreservesCatalog() {
        guard(); val expected = JSONObject(File(directory, "active.json").readText())
        val cancelled = JSONObject(File(directory, "cancelled.json").readText())
        ActivityScenario.launch<IndependentSkuActivity>(Intent(app, IndependentSkuActivity::class.java)).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本次预检已取消")))) }
            onView(withId(R.id.active)).perform(independentSkuScroll(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("资料已启用")))) }
            onView(withId(R.id.choose)).perform(independentSkuScroll(), click())
            File(directory, "cancel-picker.txt").writeText("Host must press actual system Back")
            await(90) { onView(withId(R.id.status)).check(matches(withText(containsString("未选择文件")))) }
        }
        assertEquals(expected.getString("importId"), repository.active()!!.imported.id)
        assertEquals(cancelled.getString("importId"), repository.latest()!!.imported.id)
        assertArrayEquals(File(directory, "original.bin").readBytes(), repository.original(expected.getString("importId")))
        assertArrayEquals(File(directory, "bad.bin").readBytes(), repository.original(cancelled.getString("importId")))
        File(directory, "restart.json").writeText(JSONObject().put("newProcess", true).put("originalBytesPreserved", true)
            .put("pickerCancelPreserved", true).put("localDeletionAllowed", false).toString())
    }
    @Test fun actualScreenAllowsLargeTextWithoutHorizontalOverflow() {
        guard()
        ActivityScenario.launch<IndependentSkuActivity>(Intent(app, IndependentSkuActivity::class.java)).use {
            await { onView(withId(R.id.choose)).check(matches(isEnabled())) }
            val scale = app.resources.configuration.fontScale.toString()
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "layout-$scale-top") }
            onView(withId(R.id.back)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "layout-$scale-bottom") }
        }
    }
    private fun await(seconds: Int = 30, assertion: () -> Unit) {
        val end = System.nanoTime() + seconds * 1_000_000_000L
        var failure: Throwable? = null
        while (System.nanoTime() < end) {
            try { assertion(); return } catch (error: Throwable) { failure = error; Thread.sleep(200) }
        }
        throw AssertionError("Product state did not settle", failure)
    }
}
