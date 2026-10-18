package com.acrylic.outbound.pda

import android.content.Intent
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.ScanAdjustmentActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Synthetic signed preparation; fixture scans use the real writer, never fabricated Room rows. */
class AdjustmentFilterDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "adjustment-filter")

    @Test fun oldRowsCanBeFilteredWithoutAddingOrChangingCaptures() {
        guard(); check(directory.mkdirs()) { "Preserve earlier facts" }
        val id = createOriginals()
        File(directory, "order.txt").writeText(id)
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-before.json").writeText(before)
        exercise(id, "first")
        val after = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        File(directory, "database-after.json").writeText(after)
        assertEquals(before, after)
        assertEquals(27, app.database.temporaryScans().captures(id).size)
        assertEquals(27, app.database.temporaryScans().allScans(id).size)
    }

    @Test fun retainedOriginalFiltersSurviveNewProcessAndFontChanges() {
        guard()
        val id = File(directory, "order.txt").readText().trim()
        val before = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        val scale = app.resources.configuration.fontScale.toString().replace('.', '-')
        val run = InstrumentationRegistry.getArguments().getString("evidenceRun") ?: "font-$scale"
        require(run.matches(Regex("[a-zA-Z0-9-]{1,60}")))
        exercise(id, run)
        assertEquals(before, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("adjustmentFilter") == "true")
        check(!BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT && !BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    private fun createOriginals(): String {
        val id = UUID.randomUUID().toString(); val store = app.temporaryStore()
        val repository = TemporaryDraftRepository(store); val draft = repository.create(id)
        val codes = store.source(id).bales.keys.toList()
        val plan = codes.map { it.take(3) }.distinct().map { it to "30" }
        val saved = repository.save(id, TemporaryPlanInput(draft.revision, "筛选合成长车号123456789", "", "隔离筛选验证", plan))
        repository.start(id, saved.revision)
        repeat(27) { index ->
            app.temporaryWork.resume(id)
            await { assertTrue(app.temporaryWork.state.value.ready) }
            assertTrue(app.temporaryWork.receive(codes[index % codes.size], "MANUAL"))
            await { assertEquals("WAITING_CONFIRM", app.temporaryWork.state.value.candidate?.state) }
            app.temporaryWork.confirm(app.temporaryWork.state.value.candidate!!.id)
            await { assertEquals((index + 1), app.database.temporaryScans().allScans(id).size) }
            app.temporaryWork.pause()
            if (index < 26) runBlocking {
                app.temporaryWork.adjust(id, app.database.temporaryScans().allScans(id).last().captureId,
                    "合成输入未装车，保留原序号用于跨页查询").await()
            }
        }
        return id
    }

    private fun exercise(id: String, label: String) {
        val intent = Intent(app, ScanAdjustmentActivity::class.java).putExtra("clientOrderId", id)
        ActivityScenario.launch<ScanAdjustmentActivity>(intent).use { scenario ->
            try { exercisePage(scenario, id); capture(scenario, label) }
            finally { capture(scenario, "$label-final") }
        }
    }

    private fun exercisePage(scenario: ActivityScenario<ScanAdjustmentActivity>, id: String) {
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
            onView(isRoot()).perform(closeSoftKeyboard())
            assertRows(scenario, (1L..20L).toList())
            reachNextPage()
            await { assertRows(scenario, (21L..27L).toList()) }
            applyFilter("1", "")
            await { assertRows(scenario, listOf(1L)) }
            scenario.recreate()
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())); assertRows(scenario, listOf(1L)) }
            val code = app.database.temporaryScans().allScans(id).first().deliveryCode
            applyFilter("", code)
            await { assertRows(scenario, (1L..27L step 2).toList()) }
            applyFilter("", "%_'")
            await { assertRows(scenario, emptyList()) }
            onView(withId(R.id.status)).check(matches(withText(containsString("没有匹配"))))
            applyFilter("0", "")
            await { assertRows(scenario, emptyList()) }
            onView(withId(R.id.status)).check(matches(withText(containsString("有效原序号"))))
            scenario.recreate()
            await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())); assertRows(scenario, emptyList()) }
            onView(withId(R.id.status)).check(matches(withText(containsString("有效原序号"))))
            onView(withId(R.id.clearFilter)).perform(independentSkuScroll(), click())
            await { assertRows(scenario, (1L..20L).toList()) }
            applyFilter("27", "")
            await { assertRows(scenario, listOf(27L)) }
            onView(withText(containsString("27. "))).perform(independentSkuScroll(), click())
            AdjustmentFilterDialogEvidence.save(directory)
            onView(withText("返回核对")).perform(click())
    }

    private fun reachNextPage() {
        repeat(30) {
            if (runCatching { onView(withId(R.id.next)).check(matches(isCompletelyDisplayed())) }.isSuccess) {
                onView(withId(R.id.next)).perform(click()); return
            }
            onView(isRoot()).perform(androidx.test.espresso.action.GeneralSwipeAction(
                androidx.test.espresso.action.Swipe.FAST,
                { view -> val p = IntArray(2); view.getLocationOnScreen(p); floatArrayOf(p[0] + view.width * .5f, p[1] + view.height * .65f) },
                { view -> val p = IntArray(2); view.getLocationOnScreen(p); floatArrayOf(p[0] + view.width * .5f, p[1] + view.height * .3f) },
                androidx.test.espresso.action.Press.FINGER))
        }
        onView(withId(R.id.next)).check(matches(isCompletelyDisplayed()))
    }

    private fun applyFilter(sequence: String, product: String) {
        onView(withId(R.id.sequence)).perform(independentSkuScroll(), replaceText(sequence), closeSoftKeyboard())
        onView(withId(R.id.productFilter)).perform(independentSkuScroll(), replaceText(product), closeSoftKeyboard())
        onView(withId(R.id.filterRecords)).perform(independentSkuScroll(), click())
        await { onView(withId(R.id.filterRecords)).check(matches(isEnabled())) }
    }

    private fun assertRows(scenario: ActivityScenario<ScanAdjustmentActivity>, expected: List<Long>) {
        scenario.onActivity { activity ->
            val rows = activity.findViewById<LinearLayout>(R.id.rows)
            val actual = (0 until rows.childCount).map { (rows.getChildAt(it) as android.widget.Button).text.toString().substringBefore('.').toLong() }
            assertEquals(expected, actual)
        }
    }

    private fun capture(scenario: ActivityScenario<ScanAdjustmentActivity>, label: String) {
        scenario.onActivity { activity ->
            val root = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            File(directory, "$label.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val next = activity.findViewById<android.view.View>(R.id.next)
            val visible = android.graphics.Rect(); next.getGlobalVisibleRect(visible)
            val scroll = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
            File(directory, "$label.json").writeText(JSONObject().put("fontScale", activity.resources.configuration.fontScale)
                .put("width", root.width).put("height", root.height).put("originalCount", 27)
                .put("nextHeight", next.height).put("nextVisibleHeight", visible.height())
                .put("scrollY", scroll.scrollY).put("scrollHeight", scroll.height)
                .put("contentHeight", scroll.getChildAt(0).height).put("bottomPadding", scroll.paddingBottom)
                .put("localDeletionAllowed", false).put("purpose", "SYNTHETIC_FILTER_L1").toString(2))
        }
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(50)
        } while (System.nanoTime() < end)
        throw AssertionError("Original filter page did not reach expected state", last)
    }
}
