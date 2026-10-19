package com.acrylic.outbound.pda.ui

import android.view.LayoutInflater
import android.app.Activity
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentWorkPanelsTest {
    @Test fun closingDetailsClearsOnlySecretAndRetainsCorrectionInputs() {
        val b = binding()
        b.workDetailsToggle.performClick()
        b.workPassword.setText("synthetic-secret")
        b.workReason.setText("核对未装车")
        b.workLocateBarcode.setText("003A0000001")
        b.workFinishReason.setText("短装原因")

        b.workDetailsToggle.performClick()
        b.workDetailsToggle.performClick()

        assertEquals("", b.workPassword.text.toString())
        assertEquals("核对未装车", b.workReason.text.toString())
        assertEquals("003A0000001", b.workLocateBarcode.text.toString())
        assertEquals("短装原因", b.workFinishReason.text.toString())
        assertTrue(b.workReason.isShown)
    }

    @Test fun manualTogglePreservesLeadingZeroInput() {
        val b = binding()
        b.workManualToggle.performClick()
        b.workManual.setText("003A0000001")

        b.workManualToggle.performClick()
        assertFalse(b.workManual.isShown)
        b.workManualToggle.performClick()

        assertEquals("003A0000001", b.workManual.text.toString())
        assertTrue(b.workValidate.isShown)
    }

    @Test fun queueRenderingPreservesExpandedInputsAndOperatorEdits() {
        val b = binding()
        b.workDetailsToggle.performClick()
        b.workManualToggle.performClick()
        b.workManual.setText("003A0000001")
        b.workPassword.setText("synthetic-secret")
        b.workReason.setText("未装车")
        val display = IndependentWorkDisplay(b)

        (1..64).forEach { display.render(IndependentScreenFixture.ui().copy(queued = it), scanning = true) }

        assertTrue(b.workManual.isShown)
        assertTrue(b.workPassword.isShown)
        assertEquals("003A0000001", b.workManual.text.toString())
        assertEquals("synthetic-secret", b.workPassword.text.toString())
        assertEquals("未装车", b.workReason.text.toString())
    }

    @Test fun queuedOrBlockedCandidateKeepsConfirmationDisabled() {
        val b = binding()
        val display = IndependentWorkDisplay(b)
        val ui = IndependentScreenFixture.ui().copy(candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM"))
        display.render(ui, scanning = true)
        assertTrue(b.workConfirm.isEnabled)

        listOf(ui.copy(queued = 1), ui.copy(blocked = "存储失败，请保全原件")).forEach {
            display.render(it, scanning = true)
            assertFalse(b.workConfirm.isEnabled)
            assertFalse(b.workDiscard.isEnabled)
        }
        display.render(ui, scanning = false)
        assertFalse(b.workConfirm.isEnabled)
    }

    @Test fun discardReasonRemainsReachableForManualCandidate() {
        val b = binding()
        IndependentWorkDisplay(b).render(IndependentScreenFixture.ui().copy(
            candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM")), scanning = true)

        b.workDetailsToggle.performClick()

        assertTrue(b.workReason.isShown)
        assertTrue(b.workDiscard.isShown)
        assertTrue(b.workDiscard.isEnabled)
    }

    @Test fun finishRequiresPauseAndEmptyQueueWithoutCandidate() {
        val b = binding()
        val display = IndependentWorkDisplay(b)
        val ui = IndependentScreenFixture.ui()
        b.workDetailsToggle.performClick()

        display.render(ui, scanning = true)
        assertFalse(b.workFinish.isEnabled)
        display.render(ui.copy(queued = 1), scanning = false)
        assertFalse(b.workFinish.isEnabled)
        display.render(ui.copy(candidate = IndependentScreenFixture.candidate("INVALID")), scanning = false)
        assertFalse(b.workFinish.isEnabled)
        display.render(ui.copy(blocked = "资格失效"), scanning = false)
        assertFalse(b.workFinish.isEnabled)
        display.render(ui, scanning = false)
        assertTrue(b.workFinish.isEnabled)
    }

    @Test fun sealedTaskKeepsOriginalReadableWithoutWriteActions() {
        val b = binding()
        val ui = IndependentScreenFixture.ui()
        val sealed = ui.copy(task = ui.task!!.copy(state = ui.task.state.copy(state = "SEALED")))

        IndependentWorkDisplay(b).render(sealed, scanning = false)
        b.workDetailsToggle.performClick()

        assertTrue(b.workOriginal.text.contains("已结束封存，仍待主管对账"))
        assertTrue(b.workOriginal.isShown)
        listOf(b.workManual, b.workValidate, b.workAuthorize, b.workPause, b.workFinish,
            b.workFinishReason, b.workFinishConfirmed, b.workVoid).forEach { assertFalse(it.isEnabled) }
    }

    private fun binding(): ActivityIndependentWorkBinding {
        val activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.AppTheme)
        val host = activity.setup().get()
        val b = ActivityIndependentWorkBinding.inflate(LayoutInflater.from(host))
        host.setContentView(b.root)
        IndependentWorkPanels(b).bind()
        return b
    }
}
