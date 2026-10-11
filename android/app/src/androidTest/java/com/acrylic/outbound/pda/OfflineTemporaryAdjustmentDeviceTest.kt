package com.acrylic.outbound.pda

import android.content.Intent
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
import java.io.File
import java.util.UUID

/** Uses the earlier real signed preparation while the isolated server is stopped. */
class OfflineTemporaryAdjustmentDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "scan-adjustment")
    @Test fun validOfflineDraftKeepsDetailEntryAndOldPackageAdjustmentUsable() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("scanAdjustment") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val store = app.temporaryStore(); val drafts = TemporaryDraftRepository(store); val id = UUID.randomUUID().toString()
        val initial = drafts.create(id)
        val codes = store.source(id).bales.keys.filter { app.database.temporaryScans().guard(it) == null }.take(2)
        assertEquals(2, codes.size)
        val saved = drafts.save(id, TemporaryPlanInput(initial.revision, "完全断网调整车", "", "隔离服务停止，使用原签名资料",
            codes.map { it.take(3) to "1" }))
        drafts.start(id, saved.revision)
        scan(id, codes)
        adjustFromDetails(id, codes.first())
        assertEquals(listOf("VOID", "ACTIVE"), app.database.temporaryScans().allScans(id).map { it.status })
        assertEquals(1, app.database.temporaryScans().events(id).count { it.type == "VOID" })
        File(directory, "offline-adjustment-id.txt").writeText(id)
    }
    private fun scan(id: String, codes: List<String>) {
        ActivityScenario.launch<TemporaryWorkActivity>(Intent(app, TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)).use {
            await { assertTrue(app.temporaryWork.state.value.ready && app.temporaryWork.state.value.queued == 0) }
            codes.forEachIndexed { index, code ->
                app.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, code))
                await { assertEquals((index + 1).toLong(), app.temporaryWork.state.value.total.count) }
            }
        }
    }
    private fun adjustFromDetails(id: String, code: String) {
        ActivityScenario.launch<TemporaryDetailsActivity>(Intent(app, TemporaryDetailsActivity::class.java).putExtra("clientOrderId", id)).use {
            await { onView(withId(R.id.create)).check(matches(isEnabled())) }
            onView(withId(R.id.create)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("每页最多20条")))) }
            onView(allOf(isDescendantOfA(withId(R.id.rows)), withText(containsString(code)))).perform(scrollTo(), click())
            onView(withHint("实物核对及调整原因")).perform(replaceText("合成实物核对：未装车，保留原件"), closeSoftKeyboard())
            onView(withText("确认调整")).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("调整已保存到本机")))) }
            onView(withId(R.id.back)).perform(scrollTo(), click())
        }
    }
    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(100) } while (System.nanoTime() < end)
        throw AssertionError("Offline temporary page did not reach expected state", last)
    }
}
