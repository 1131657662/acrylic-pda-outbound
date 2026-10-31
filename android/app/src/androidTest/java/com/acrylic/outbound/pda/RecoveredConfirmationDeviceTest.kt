package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.HandoverActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("R07取消旧接收者确认/激活链；历史 fixture 保留但不再作为产品验收")
class RecoveredConfirmationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    @Test fun actualProductPageRetainsOneIntentAfterLostCreationResponse() {
        RecoveredConfirmationUiFixture(app).use { f ->
            f.prepare()
            if (InstrumentationRegistry.getArguments().getString("recoveredConfirmationUiPhase") == "restart") reopen(f)
            else receive(f)
            f.preserve()
            File(f.directory, "${InstrumentationRegistry.getArguments().getString("recoveredConfirmationUiPhase")}-completed.txt")
                .writeText("Actual emulator product page, original LOGIN/development key, actual MySQL GET/POST; no original hardware claim.\n")
        }
    }
    private fun screen() = ActivityScenario.launch<HandoverActivity>(Intent(app, HandoverActivity::class.java))
    private fun receive(f: RecoveredConfirmationUiFixture) {
        screen().use { scenario ->
            onView(withId(R.id.caseId)).perform(replaceText(f.caseId),
                androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            onView(withId(R.id.receive)).perform(scrollTo(), click())
            await(190000) { onView(withText("核对实物并确认接收")).check(matches(isEnabled())) }
            open("核对实物并确认接收"); shotDialog(f)
            onView(withText("返回核对")).perform(scrollTo(), click())
            assertNull(app.database.temporaryRecoveredConfirmations().get(f.caseId)); assertEquals(0, f.calls.get())
            shot(scenario, f, "cancelled")
            open("核对实物并确认接收")
            onView(withText("已核对，确认接收")).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("原资料与请求保留")))) }
            val original = app.database.temporaryRecoveredConfirmations().get(f.caseId)!!
            assertEquals("PENDING", original.state); assertNull(app.database.temporaryRecoveredConfirmations().receipt(f.caseId))
            assertTrue(f.calls.get() >= 1); shot(scenario, f, "lost-first-response")
            f.allowSuccess.set(true); open("恢复原接收确认")
            onView(withText("已核对，确认接收")).perform(scrollTo(), click())
            await { assertEquals("CONFIRMED", app.database.temporaryRecoveredConfirmations().get(f.caseId)!!.state) }
            assertEquals(original.commandJson, app.database.temporaryRecoveredConfirmations().get(f.caseId)!!.commandJson)
            await { onView(withText("核对已保存的接收回执")).check(matches(isEnabled())) }
            shot(scenario, f, "confirmed-still-stopped"); back()
        }
        changedLogin(f)
    }
    private fun changedLogin(f: RecoveredConfirmationUiFixture) {
        f.otherLogin(); screen().use { scenario ->
            await { onView(withText("核对本人资格与原资料")).check(matches(isNotEnabled())) }
            shot(scenario, f, "changed-login-blocked"); back()
        }; f.restoreLogin()
    }
    private fun reopen(f: RecoveredConfirmationUiFixture) {
        assertEquals("CONFIRMED", app.temporaryRecoveredConfirmations.read(f.caseId)!!.state)
        screen().use { scenario ->
            await { onView(withText("核对已保存的接收回执")).check(matches(isEnabled())) }
            shot(scenario, f, "process-reopened"); back()
        }
        assertEquals(0, f.calls.get())
    }
    private fun open(label: String) {
        await { onView(withText(label)).check(matches(isEnabled())) }; reveal(label)
        onView(withText(label)).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isCompletelyDisplayed()
            override fun getDescription() = "Record the actual touch target without invoking its listener"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                check(view.isEnabled && view.isClickable && view.hasOnClickListeners())
                val position = IntArray(2); view.getLocationOnScreen(position)
                val target = org.json.JSONObject().put("label", label).put("x", position[0]).put("y", position[1])
                    .put("width", view.width).put("height", view.height).put("listenerPresent", true)
                File(app.filesDir, "recovered-confirmation-ui/touch-${System.nanoTime()}.json").writeText(target.toString())
            }
        })
        onView(withText(label)).perform(click())
        await { onView(withText("核对恢复交接实物与资料")).check(matches(isDisplayed())) }
    }
    private fun back() { reveal("返回（资料保留）"); onView(withId(R.id.back)).perform(click()) }
    private fun reveal(text: String) {
        repeat(20) {
            settle()
            if (runCatching { onView(withText(text)).check(matches(isCompletelyDisplayed())) }.isSuccess) return
            val down = text == "返回（资料保留）"
            onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,
                { view -> point(view, if (down) 0.25f else 0.75f) },
                { view -> point(view, if (down) 0.75f else 0.25f) }, Press.FINGER))
        }
        onView(withText(text)).check(matches(isCompletelyDisplayed()))
    }
    private fun point(view: android.view.View, fraction: Float): FloatArray {
        val position = IntArray(2); view.getLocationOnScreen(position)
        return floatArrayOf(position[0] + view.width * 0.5f, position[1] + view.height * fraction)
    }
    private fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for scrolling to settle before an actual touch"
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                controller.loopMainThreadForAtLeast(1500)
            }
        })
    }
    private fun shot(scenario: ActivityScenario<HandoverActivity>, f: RecoveredConfirmationUiFixture, name: String) {
        scenario.onActivity { activity ->
            saveImage(activity.window.decorView, f, name)
        }
    }
    private fun shotDialog(f: RecoveredConfirmationUiFixture) {
        onView(isRoot()).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = isRoot()
                override fun getDescription() = "Capture actual checklist dialog without removing FLAG_SECURE"
                override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) {
                    saveImage(view, f, "checklist")
                }
            })
    }
    private fun saveImage(root: android.view.View, f: RecoveredConfirmationUiFixture, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            File(f.directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
    private fun await(timeoutMs: Long = 20000, check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs; var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }; Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
