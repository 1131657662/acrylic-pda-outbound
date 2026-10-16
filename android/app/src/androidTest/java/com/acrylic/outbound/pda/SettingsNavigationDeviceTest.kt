package com.acrylic.outbound.pda

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.MainActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Original isolated app/session; no new identity, binding, order or recovery intent. */
class SettingsNavigationDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val run get() = InstrumentationRegistry.getArguments().getString("evidenceRun")!!
    private val directory get() = File(app.filesDir, "settings-evidence/$run").apply { mkdirs() }

    @Test fun settingsNavigationSoundOfflineDiagnosisAndUpdateRetainOriginalState() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("settingsNavigation") == "true")
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        val sound = app.workSound.enabled
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use {
            await { onView(withId(R.id.myPage)).check(matches(isEnabled())) }
            tap(R.id.myPage); tap(R.id.deviceSettings)
            await { onView(withId(R.id.checkConnection)).check(matches(isEnabled())) }
            shot("settings-top"); toggleSound(sound)
            tap(R.id.checkConnection)
            await { onView(withId(R.id.checkConnection)).check(matches(isEnabled())) }
            reveal(R.id.status, 40); onView(withId(R.id.status)).check(matches(withText(containsString("原作业记录保留"))))
            shot("diagnosis"); details()
            updateAndIdentity()
            cancelLogout()
            tap(R.id.back)
            await { onView(withId(R.id.myPage)).check(matches(isEnabled())) }
        }
        assertEquals(sound, app.workSound.enabled)
        File(directory, "completed.txt").writeText("Actual navigation, sound restored, offline diagnosis, details, update, logout cancelled.\n")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    private fun updateAndIdentity() {
        tap(R.id.appUpdate)
        reveal(R.id.currentVersion, 40); onView(withId(R.id.currentVersion)).check(matches(isDisplayed()))
        shot("update"); tap(R.id.back)
        tap(R.id.advanced)
        reveal(R.id.server, 40); onView(withId(R.id.server)).check(matches(isDisplayed()))
        shot("identity-details")
        tap(R.id.advanced)
        onView(withId(R.id.server)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    private fun cancelLogout() {
        tap(R.id.logout)
        onView(withText("退出当前人员登录？")).check(matches(isDisplayed()))
        onView(withText("返回")).perform(click())
    }

    private fun toggleSound(original: Boolean) {
        tap(R.id.sound)
        assertEquals(!original, app.workSound.enabled)
        onView(withId(R.id.sound)).perform(click())
        assertEquals(original, app.workSound.enabled)
    }

    private fun details() {
        tap(R.id.diagnosticDetailsToggle)
        reveal(R.id.diagnosticDetails, 40); onView(withId(R.id.diagnosticDetails)).check(matches(isDisplayed()))
        shot("diagnostic-details")
        tap(R.id.diagnosticDetailsToggle)
        onView(withId(R.id.diagnosticDetails)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    private fun tap(id: Int) { reveal(id); onView(withId(id)).perform(click()) }

    private fun reveal(id: Int, percent: Int = 100) {
        repeat(25) {
            settle()
            if (runCatching { onView(withId(id)).check(matches(isDisplayingAtLeast(percent))) }.isSuccess) return
            var below = true
            onView(withId(id)).perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints() = withEffectiveVisibility(Visibility.VISIBLE)
                override fun getDescription() = "Read target position without invoking its listener"
                override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                    val point = IntArray(2); view.getLocationOnScreen(point); below = point[1] > view.rootView.height / 2
                }
            })
            onView(isAssignableFrom(ScrollView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,
                { view -> point(view, if (below) 0.8f else 0.25f) },
                { view -> point(view, if (below) 0.25f else 0.8f) }, Press.FINGER))
        }
        onView(withId(id)).check(matches(isDisplayingAtLeast(percent)))
    }

    private fun point(view: View, fraction: Float): FloatArray {
        val position = IntArray(2); view.getLocationOnScreen(position)
        return floatArrayOf(position[0] + view.width * 0.5f, position[1] + view.height * fraction)
    }

    private fun settle() {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Wait for actual ScrollView animation to settle"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) { controller.loopMainThreadForAtLeast(1000) }
        })
    }

    private fun shot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Inspect live layout and retain screenshot without changing FLAG_SECURE"
            override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                inspect(view, view.width)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        })
    }

    private fun inspect(view: View, width: Int) {
        if (!view.isShown) return
        assertTrue("Horizontal overflow: ${view.javaClass.simpleName}", view.width <= width)
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) assertEquals("Clipped text: ${view.text}", 0, view.layout.getEllipsisCount(line))
            assertTrue("Text exceeds view: ${view.text}", view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i), width)
    }

    private fun await(check: () -> Unit) {
        val end = android.os.SystemClock.elapsedRealtime() + 30000
        var failure: Throwable? = null
        while (android.os.SystemClock.elapsedRealtime() < end) {
            try { check(); return } catch (error: Throwable) { failure = error }
            Thread.sleep(100)
        }
        throw checkNotNull(failure)
    }
}
