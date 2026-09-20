package com.acrylic.outbound.pda

import android.content.Intent
import android.os.Build
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acrylic.outbound.pda.ui.PdaUpdateActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only UI check after the isolated first-install smoke test. */
@RunWith(AndroidJUnit4::class)
class FirstInstallUpdateUiTest {
    @Test fun firstInstalledCandidateShowsCurrentWithoutInstallAction() {
        check(Build.MODEL.startsWith("sdk_gphone"))
        check(BuildConfig.PDA_UPDATE_CHANNEL == "staging")
        check(BuildConfig.PDA_SERVER_URL == "https://outbound-test.nbsmzwl.cn/api/v1/")
        val intent = Intent(ApplicationProvider.getApplicationContext(), PdaUpdateActivity::class.java)
        ActivityScenario.launch<PdaUpdateActivity>(intent).use { scenario ->
            onView(withId(R.id.currentVersion)).check(matches(withText(containsString(BuildConfig.VERSION_NAME))))
            onView(withId(R.id.check)).perform(scrollTo(), click())
            awaitCurrentVersion()
            onView(withId(R.id.download)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.install)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.status)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.back)).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                for (id in listOf(R.id.back, R.id.currentVersion, R.id.status, R.id.lastCheck)) {
                    val view = activity.findViewById<TextView>(id)
                    assertTrue(view.width <= activity.window.decorView.width)
                    assertTrue("Text clipped: ${activity.resources.getResourceEntryName(id)}",
                        view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                }
            }
        }
    }

    private fun awaitCurrentVersion() {
        val deadline = System.currentTimeMillis() + 30_000
        var last: AssertionError? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.status)).check(matches(withText(containsString("当前已是最新版本"))))
                return
            } catch (error: AssertionError) { last = error; Thread.sleep(200) }
        }
        throw AssertionError("首次安装后的联网更新检查未成功", last)
    }
}
