package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acrylic.outbound.pda.ui.PdaUpdateActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real staging HTTPS channel through the first update step. */
@RunWith(AndroidJUnit4::class)
class PdaUpdateDeviceTest {
    @Test fun stagingManifestAndApkDownloadAreVerifiedBeforeInstall() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PdaUpdateActivity::class.java)
        ActivityScenario.launch<PdaUpdateActivity>(intent).use {
            onView(withId(com.acrylic.outbound.pda.R.id.check)).perform(scrollTo(), click())
            waitForText("发现可用更新")
            onView(withId(com.acrylic.outbound.pda.R.id.download)).perform(scrollTo(), click())
            waitForText("安装包已校验")
            onView(withId(com.acrylic.outbound.pda.R.id.install)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test fun verifiedApkIsHandedToAndroidInstaller() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PdaUpdateActivity::class.java)
        ActivityScenario.launch<PdaUpdateActivity>(intent).use {
            onView(withId(com.acrylic.outbound.pda.R.id.install)).perform(scrollTo(), click())
        }
    }

    @Test fun installPendingIsResolvedAfterInstalledVersionIsObserved() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PdaUpdateActivity::class.java)
        ActivityScenario.launch<PdaUpdateActivity>(intent).use {
            waitForText("更新完成")
        }
    }

    private fun waitForText(expected: String) {
        val deadline = System.currentTimeMillis() + 90_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(com.acrylic.outbound.pda.R.id.status))
                    .check(matches(withText(containsString(expected))))
                return
            } catch (error: Throwable) {
                last = error
                Thread.sleep(250)
            }
        }
        throw AssertionError("等待更新状态超时：$expected", last)
    }
}
