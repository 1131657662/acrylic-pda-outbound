package com.acrylic.outbound.pda

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId

/** Open secondary inputs through the same explicit controls as the operator. */
internal object IndependentWorkDevicePanels {
    fun reveal(id: Int) {
        when (id) {
            R.id.workManual -> open(R.id.workManualPanel, R.id.workManualToggle)
            R.id.workPassword, R.id.workLocateBarcode, R.id.workReason, R.id.workFinishReason -> details()
        }
    }

    fun details() = open(R.id.workDetailsPanel, R.id.workDetailsToggle)

    private fun open(panel: Int, toggle: Int) {
        var collapsed = false
        onView(withId(panel)).check { view, error ->
            if (error != null) throw error
            collapsed = view.visibility == View.GONE
        }
        if (collapsed) IndependentCredentialDeviceFixture.click(toggle)
    }
}
