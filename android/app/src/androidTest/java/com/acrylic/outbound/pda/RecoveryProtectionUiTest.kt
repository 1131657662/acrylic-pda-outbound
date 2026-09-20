package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.*
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Explicit device checks on preserved data, using the actual local API and UI login. */
class RecoveryProtectionUiTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var evidence: RecoveryUiEvidence
    private lateinit var proxy: SubmissionProxy

    @Before fun connectRetainedFixture() {
        ArchiveRecoveryFacts(app).requireIsolated()
        evidence = RecoveryUiEvidence(app); evidence.requireFixture()
        proxy = SubmissionProxy("http://10.0.2.2:18088/api/v1/", java.net.URI(app.session.settings.server).port)
        assertEquals(app.session.settings.server, proxy.base)
    }

    @After fun preserveFixture() {
        try { evidence.assertPreserved() } finally { proxy.close() }
    }

    @Test fun homeShowsPersistentRecoveryWarning() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { onView(withId(R.id.details)).check(matches(withText(containsString("服务器正在恢复对账")))) }
            onView(withId(R.id.details)).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.onActivity { evidence.screen(it, "home", listOf(R.id.details, R.id.identity)) }
        }
        evidence.completed("home")
    }

    @Test fun workPageKeepsRecoveryWarningWithFrozenRecords() {
        ActivityScenario.launch(MainActivity::class.java).use {
            await { onView(withId(R.id.work)).check(matches(isEnabled())) }
            onView(withId(R.id.work)).perform(scrollTo(), click())
            await { assertNotNull(app.work.state.value.order) }
            assertTrue(app.work.state.value.blocked.contains("服务器正在恢复对账"))
            app.sendBroadcast(Intent(com.acrylic.outbound.pda.device.ScannerReceiver.ACTION).setPackage(app.packageName)
                .putExtra(com.acrylic.outbound.pda.device.ScannerReceiver.BARCODE_KEY, "TEST0000001"))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(app.work.receive("TEST0000001", "MANUAL"))
            onView(withId(R.id.workControls)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.voidLast)).check(matches(org.hamcrest.CoreMatchers.not(isEnabled())))
            recordResumed("work", listOf(R.id.message, R.id.closeOrder))
            onView(withId(R.id.message)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.message)).check(matches(withText(containsString("服务器正在恢复对账"))))
            pressBack()
        }
        evidence.completed("work")
    }

    @Test fun historicalReceiptDoesNotImplyCurrentRecoveryComplete() {
        ActivityScenario.launch(MainActivity::class.java).use {
            await { onView(withId(R.id.work)).check(matches(isEnabled())) }
            onView(withId(R.id.work)).perform(scrollTo(), click())
            await { assertNotNull(app.work.state.value.order) }
            onView(withId(R.id.closeOrder)).perform(scrollTo(), click())
            await { onView(withId(R.id.notice)).check(matches(withText(containsString("冻结正文和明细保留")))) }
            recordResumed("receipt", listOf(R.id.status, R.id.notice, R.id.freeze, R.id.upload))
            onView(withId(R.id.freeze)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.upload)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.status)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.status)).check(matches(withText(containsString("待对账"))))
            pressBack(); pressBack()
        }
        evidence.completed("receipt")
    }

    @Test fun logoutAndRealLoginRetainRecoveryBarrier() {
        val file = File(app.filesDir, "recovery-login.json")
        val login = JSONObject(file.readText()); file.delete()
        val originalUser = app.session.requireSession().getString("userId")
        ActivityScenario.launch(DeviceSettingsActivity::class.java).use { scenario ->
            await { onView(withId(R.id.logout)).check(matches(isEnabled())) }
            onView(withId(R.id.logout)).perform(scrollTo(), click())
            onView(withText("退出登录")).perform(click())
            await { onView(withId(R.id.status)).check(matches(withText("已退出登录；原资料和占用保留。"))) }
            assertNull(app.session.saved()); evidence.assertPreserved()
            scenario.onActivity { evidence.screen(it, "logout", listOf(R.id.identity, R.id.status)) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { onView(withId(R.id.loginPanel)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            onView(withId(R.id.username)).perform(scrollTo(), replaceText(login.getString("username")))
            onView(withId(R.id.password)).perform(scrollTo(), replaceText(login.getString("password")), closeSoftKeyboard())
            onView(withId(R.id.login)).perform(scrollTo(), click())
            await { onView(withId(R.id.details)).check(matches(withText(containsString("服务器正在恢复对账")))) }
            assertEquals(originalUser, app.session.requireSession().getString("userId"))
            assertEquals("2", app.session.requireSession().getString("serverRecoveryEpoch"))
            scenario.onActivity { evidence.screen(it, "relogin", listOf(R.id.identity, R.id.status, R.id.details)) }
        }
        evidence.completed("relogin")
    }

    private fun recordResumed(name: String, ids: List<Int>) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            evidence.screen(activity, name, ids)
        }
    }

    private fun await(check: () -> Unit) {
        val deadline = System.currentTimeMillis() + 30_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { check(); return } catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(200)
        }
        throw AssertionError("Recovery UI did not reach the expected state", last)
    }
}
