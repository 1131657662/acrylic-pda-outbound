package com.acrylic.outbound.pda

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Test
import com.acrylic.outbound.pda.ui.IndependentWorkActivity
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

class IndependentLargeFontNativeTest {
    @Test fun nativeImeReturnsToFirstScreenForTouchConfirmationAtConfiguredFont() {
        val fixture = IndependentLargeFontNativeFixture()
        fixture.launch().use { scenario ->
            F.await { onView(withId(R.id.workVehicle)).check(matches(withText("沪A12345"))) }
            IndependentScanDeviceFixture.authorize()
            F.click(R.id.workDetailsToggle)
            F.click(R.id.workManualToggle)
            onView(withId(R.id.workManual)).perform(independentWorkNativeScroll(), replaceText(fixture.barcode), click())
            fixture.ime(scenario, true)

            onView(withId(R.id.workValidate)).perform(independentWorkNativeScroll(), click())
            F.await { onView(withId(R.id.workConfirm)).check(matches(isEnabled())) }
            fixture.ime(scenario, false)
            assertPendingInput(fixture, scenario)
            fixture.captureFirstScreen(scenario, "pending")
            onView(withId(R.id.workConfirm)).perform(click())
            F.await { assertEquals(1L, fixture.task().state.actualCount) }
            F.await { onView(withId(R.id.workRecent)).check(matches(withText(containsString(fixture.barcode)))) }
            assertEquals(1, fixture.captures().size)
            fixture.captureFirstScreen(scenario, "saved")
            fixture.record()
        }
    }

    private fun assertPendingInput(fixture: IndependentLargeFontNativeFixture, scenario: ActivityScenario<IndependentWorkActivity>) {
        assertEquals(0L, fixture.task().state.actualCount)
        assertEquals("WAITING_CONFIRM", fixture.captures().single().processing.state)
        scenario.onActivity {
            assertEquals(View.GONE, it.findViewById<View>(R.id.workManualPanel).visibility)
            assertEquals("", it.findViewById<EditText>(R.id.workManual).text.toString())
        }
    }
}
