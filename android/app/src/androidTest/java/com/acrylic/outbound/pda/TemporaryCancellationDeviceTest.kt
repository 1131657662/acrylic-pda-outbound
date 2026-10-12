package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Retained emulator identity and preparation; creation is a test consumer, cancellation is the product UI. */
class TemporaryCancellationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "temporary-cancel").apply { mkdirs() }
    private val facts get() = JSONObject(File(directory, "corrected-prepared.json").takeIf { it.exists() }?.readText()
        ?: File(directory, "prepared.json").readText())
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("temporaryCancel") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    @Test fun originalNativeSessionCreatesNewZeroDraftWithoutReseedingOldWork() {
        guard()
        check(!File(directory, "prepared.json").exists()) { "Preserve previous one-shot input" }
        val login = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        app.session.login(login.getString("username"), login.getString("password"))
        val store = app.temporaryStore(); val repository = TemporaryDraftRepository(store)
        val id = UUID.randomUUID().toString(); val initial = repository.create(id)
        val row = repository.save(id, TemporaryPlanInput(initial.revision, "本机零作业取消验证车", "", "隔离无人装车",
            store.source(id).products.keys.map { it to "1" }))
        val frozenId = JSONObject(File(app.filesDir, "scan-adjustment/temporary-result.json").readText()).getString("clientOrderId")
        val frozen = checkNotNull(app.database.temporarySubmissions().forOrder(frozenId))
        File(directory, "frozen-original.json").writeText(frozen.payload)
        File(directory, "plan-before.json").writeText(JSONObject().put("planSha256", row.planSha256).put("revision", row.revision).toString())
        File(directory, "prepared.json").writeText(JSONObject().put("clientOrderId", id).put("temporaryNo", row.temporaryNo).put("frozenId", frozenId)
            .put("creatorDeviceId", row.creatorDeviceId).put("operatorId", row.operatorId).put("revision", row.revision)
            .put("planSha256", row.planSha256).put("frozenSha256", frozen.sha256).toString())
        assertEquals(0L, app.database.temporaryScans().total(id).count)
    }
    @Test fun stoppedServiceCancelsThroughOriginalDraftProductButton() {
        guard(); val id = facts.getString("clientOrderId")
        val lines = app.database.temporaryDrafts().lines(id)
        ActivityScenario.launch<TemporaryDraftActivity>(Intent(app, TemporaryDraftActivity::class.java).putExtra("clientOrderId", id)).use {
            await { onView(withText("取消本机零作业草稿")).check(matches(isEnabled())) }
            onView(withText("取消本机零作业草稿")).perform(scrollTo(), click())
            await { onView(withId(R.id.cancelDraft)).check(matches(isEnabled())) }
            onView(withId(R.id.reason)).perform(scrollTo(), replaceText("本车取消，未装车"), closeSoftKeyboard())
            onView(withId(R.id.cancelDraft)).perform(scrollTo(), click())
            onView(withText("确认本机取消")).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("已取消本机草稿")))) }
            onView(withId(R.id.cancelDraft)).check(matches(not(isEnabled())))
            onView(withId(R.id.retry)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本车取消，未装车")))) }
            shot("cancelled-complete")
        }
        assertEquals(lines, app.database.temporaryDrafts().lines(id))
        preserve("cancelled.json")
    }
    @Test fun correctedConsumerCreatesDistinctZeroDraftPreservingFirstCancelledOrder() {
        guard(); check(!File(directory, "corrected-prepared.json").exists())
        val original = facts
        assertEquals("CANCELLED", TemporaryDraftCancellation(app.temporaryStore()).read(original.getString("clientOrderId")).state)
        val store = app.temporaryStore(); val repository = TemporaryDraftRepository(store)
        val id = UUID.randomUUID().toString(); val initial = repository.create(id)
        val row = repository.save(id, TemporaryPlanInput(initial.revision, "本机零作业取消验证车", "", "隔离新输入复验",
            store.source(id).products.keys.map { it to "1" }))
        val corrected = JSONObject(original.toString()).put("clientOrderId", id).put("temporaryNo", row.temporaryNo)
            .put("revision", row.revision).put("planSha256", row.planSha256)
        File(directory, "corrected-prepared.json").writeText(corrected.toString())
        assertEquals("CANCELLED", app.database.temporaryDrafts().get(original.getString("clientOrderId"))?.state)
    }
    @Test fun freshProcessReadsCancelledHistoryAndFrozenOrderKeepsOriginal() {
        guard(); val id = facts.getString("clientOrderId")
        ActivityScenario.launch<TemporaryOrdersActivity>(Intent(app, TemporaryOrdersActivity::class.java)).use {
            val number = checkNotNull(app.database.temporaryDrafts().get(id)).temporaryNo
            await { onView(withText(containsString(number))).check(matches(withText(containsString("已取消本机草稿")))) }
            onView(withText(containsString(number))).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本车取消，未装车")))) }
            onView(withId(R.id.cancelDraft)).check(matches(not(isEnabled())))
            onView(withId(R.id.retry)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本车取消，未装车")))) }
            shot("cancelled-restart-${System.nanoTime()}")
        }
        ActivityScenario.launch<TemporaryCancellationActivity>(Intent(app, TemporaryCancellationActivity::class.java)
            .putExtra("clientOrderId", facts.getString("frozenId"))).use {
            await { onView(withId(R.id.status)).check(matches(withText(containsString("主管处置")))) }
            onView(withId(R.id.cancelDraft)).check(matches(not(isEnabled())))
        }
        assertEquals("CANCELLED", TemporaryDraftCancellation(app.temporaryStore()).read(id).state)
        preserve("restarted.json")
    }
    private fun preserve(name: String) {
        val id = facts.getString("clientOrderId"); val row = checkNotNull(app.database.temporaryDrafts().get(id))
        assertEquals(facts.getString("planSha256"), row.planSha256)
        assertEquals(facts.getLong("revision"), row.revision)
        assertTrue(app.database.temporaryScans().captures(id).isEmpty())
        assertNull(app.database.temporarySubmissions().forOrder(id))
        val event = app.database.temporaryScans().events(id).single { it.type == "TEMP_DRAFT_CANCELLED" }
        assertEquals("本车取消，未装车", JSONObject(event.detail).getString("reason"))
        val frozen = checkNotNull(app.database.temporarySubmissions().forOrder(facts.getString("frozenId")))
        assertEquals(facts.getString("frozenSha256"), frozen.sha256)
        assertEquals(File(directory, "frozen-original.json").readText(), frozen.payload)
        File(directory, "${System.nanoTime()}-$name").writeText(JSONObject().put("clientOrderId", id).put("state", row.state).put("audit", event.detail)
            .put("frozenOriginalUnchanged", true).put("localDeletionAllowed", false).toString())
    }
    private fun await(checker: () -> Unit) {
        var last: Throwable? = null
        repeat(100) { try { checker(); return } catch (error: Throwable) { last = error; Thread.sleep(150) } }
        throw AssertionError("Product state did not settle", last)
    }
    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Preserve actual cancellation layout without disabling FLAG_SECURE"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                val dimensions = listOf(R.id.cancelDraft, R.id.retry, R.id.status, R.id.details).map { id ->
                    val child = view.findViewById<android.view.View>(id); val rect = android.graphics.Rect()
                    "${view.resources.getResourceEntryName(id)}: ${child.getGlobalVisibleRect(rect)} $rect height=${child.height}"
                }
                File(directory, "$name-dimensions.txt").writeText(dimensions.joinToString("\n"))
            }
        })
    }
}
