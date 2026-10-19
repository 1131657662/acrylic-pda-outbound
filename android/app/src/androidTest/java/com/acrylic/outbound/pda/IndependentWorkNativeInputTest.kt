package com.acrylic.outbound.pda

import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

class IndependentWorkNativeInputTest {
    @Test fun nativeImeHandsManualInputToExplicitConfirmationAtConfiguredFont() {
        val fixture = IndependentWorkNativeFixture()
        fixture.launch().use { scenario ->
            fixture.traceKeys(scenario)
            F.await { onView(withId(R.id.workSummary)).check(matches(withText(containsString("UI-NATIVE")))) }
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.workManualPanel).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.workDetailsPanel).visibility)
            }
            fixture.top(scenario); fixture.capture(scenario, "initial")
            IndependentScanDeviceFixture.authorize()
            F.click(R.id.workDetailsToggle); fixture.top(scenario)
            fixture.typeWithIme(); fixture.ime(scenario, true); fixture.capture(scenario, "keyboard")
            fixture.key(KeyEvent.KEYCODE_F1)
            assertTrue(fixture.captures().isEmpty())
            onView(withId(R.id.workValidate)).perform(independentWorkNativeScroll(), click())
            F.await { onView(withId(R.id.workConfirm)).check(matches(isEnabled())) }
            fixture.ime(scenario, false)
            assertPending(fixture, scenario)
            fixture.top(scenario); fixture.capture(scenario, "pending")
            if (fixture.index % 2 == 0) fixture.heldEnter() else F.click(R.id.workConfirm)
            F.await { assertEquals(1L, fixture.task().state.actualCount) }
            F.await { onView(withId(R.id.workRecent)).check(matches(withText(containsString(fixture.barcode)))) }
            assertEquals(1, fixture.captures().size)
            fixture.top(scenario); fixture.capture(scenario, "saved"); fixture.record()
        }
    }

    private fun assertPending(fixture: IndependentWorkNativeFixture,
        scenario: androidx.test.core.app.ActivityScenario<com.acrylic.outbound.pda.ui.IndependentWorkActivity>) {
        assertEquals(0L, fixture.task().state.actualCount)
        val pending = fixture.captures().single()
        assertEquals("WAITING_CONFIRM", pending.processing.state)
        assertEquals(fixture.barcode, pending.capture.rawInput)
        assertEquals("MANUAL", pending.capture.inputSource)
        scenario.onActivity { activity ->
            assertEquals(View.GONE, activity.findViewById<View>(R.id.workManualPanel).visibility)
            assertFalse(activity.findViewById<EditText>(R.id.workManual).hasFocus())
            assertEquals("", activity.findViewById<EditText>(R.id.workManual).text.toString())
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, activity.findViewById<TextView>(R.id.workQueue).accessibilityLiveRegion)
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<TextView>(R.id.workMessage).accessibilityLiveRegion)
        }
    }
}
