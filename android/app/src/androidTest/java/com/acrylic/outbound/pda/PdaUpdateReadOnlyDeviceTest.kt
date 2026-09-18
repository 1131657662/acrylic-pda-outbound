package com.acrylic.outbound.pda

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acrylic.outbound.pda.ui.PdaUpdateActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only checks on the installed release; run separately from the staged upgrade tests. */
@RunWith(AndroidJUnit4::class)
class PdaUpdateReadOnlyDeviceTest {
    @Test fun currentReleaseHasNoInstallActionAndControlsRemainReachable() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PdaUpdateActivity::class.java)
        ActivityScenario.launch<PdaUpdateActivity>(intent).use { scenario ->
            waitFor("更新完成")
            onView(withId(R.id.check)).perform(scrollTo(), click())
            waitFor("当前已是最新版本")
            onView(withId(R.id.download)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.install)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.status)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.back)).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                for (id in listOf(R.id.back, R.id.currentVersion, R.id.status, R.id.lastCheck)) {
                    val view = activity.findViewById<TextView>(id)
                    assertTrue(view.width <= activity.window.decorView.width)
                    assertTrue("Text clipped: ${activity.resources.getResourceEntryName(id)}", view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                    assertEquals(View.VISIBLE, view.visibility)
                }
            }
        }
    }

    private fun waitFor(expected: String) {
        val deadline = System.currentTimeMillis() + 30000
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { onView(withId(R.id.status)).check(matches(withText(containsString(expected)))); return }
            catch (error: AssertionError) { last = error; Thread.sleep(200) }
        }
        throw AssertionError("等待更新状态超时：$expected", last)
    }
}
