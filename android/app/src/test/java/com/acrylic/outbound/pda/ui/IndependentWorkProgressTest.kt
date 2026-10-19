package com.acrylic.outbound.pda.ui

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import androidx.core.widget.doAfterTextChanged
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class IndependentWorkProgressTest {
    @Test fun remainingUsesEffectiveSavedCountEvenWithQueuedOrUnconfirmedInputs() {
        val b = binding()
        val ui = IndependentScreenFixture.ui().copy(queued = 64,
            candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM"))

        IndependentWorkDisplay(b).render(ui, scanning = true)

        assertTrue(b.workSummary.text.contains("有效1包"))
        assertTrue(b.workSummary.text.contains("剩余99包"))
    }

    @Test fun zeroExactAndAbovePlanNeverClaimOutboundCompletion() {
        val b = binding()
        val original = IndependentScreenFixture.ui()
        listOf(0L to "剩余100包", 100L to "剩余0包", 101L to "超计划1包").forEach { (count, expected) ->
            val task = original.task!!
            IndependentWorkDisplay(b).render(original.copy(task = task.copy(state = task.state.copy(actualCount = count))), false)
            assertTrue("count=$count text=${b.workSummary.text}", b.workSummary.text.contains(expected))
            assertFalse(b.workSummary.text.contains("已完成"))
        }
    }

    @Test fun largeCountsKeepIntegerPrecision() {
        val b = binding()
        val ui = IndependentScreenFixture.ui()
        val task = ui.task!!

        IndependentWorkDisplay(b).render(ui.copy(task = task.copy(task = task.task.copy(plannedTotal = Long.MAX_VALUE),
            state = task.state.copy(actualCount = 1))), false)

        assertTrue(b.workSummary.text.contains("剩余9223372036854775806包"))
    }

    @Test fun queuedFailedAndPagedInputsLeaveLastDurableCardUntouched() {
        val b = binding()
        val ui = IndependentScreenFixture.ui()
        val display = IndependentWorkDisplay(b)
        display.render(ui, true)
        var rewrites = 0
        b.workRecent.doAfterTextChanged { rewrites++ }

        display.render(ui.copy(queued = 64, after = 50, rows = emptyList(),
            candidate = IndependentScreenFixture.candidate("INVALID")), true)

        assertEquals(0, rewrites)
        assertEquals("已存 003A0000001", b.workRecent.text.toString())
        assertTrue(b.workRecentDetails.text.contains("型号 · 合成品名"))
        assertTrue(b.workRecentDetails.text.contains("1.250 kg / 1250 克"))
    }

    @Test fun voidPreservesLatestIdentityAndRestoresRemainingCount() {
        val b = binding()
        val ui = IndependentScreenFixture.ui()
        val task = ui.task!!

        IndependentWorkDisplay(b).render(ui.copy(latestSaved = ui.latestSaved!!.copy(status = "VOID"),
            task = task.copy(state = task.state.copy(actualCount = 0, weightGrams = 0))), false)

        assertTrue(b.workRecent.text.contains("已撤销 003A0000001"))
        assertFalse(b.workRecent.text.contains("已存"))
        assertTrue(b.workSummary.text.contains("剩余100包"))
    }

    @Test fun emptyOrDifferentTaskCannotShowPreviousTaskCard() {
        val b = binding()
        val display = IndependentWorkDisplay(b)
        val ui = IndependentScreenFixture.ui()
        display.render(ui, true)

        display.render(ui.copy(latestSaved = ui.latestSaved!!.copy(scan = ui.latestSaved.scan.copy(taskId = "other"))), false)
        assertEquals("", b.workRecent.text.toString())
        assertEquals(android.view.View.GONE, b.workRecent.visibility)
        display.render(ui.copy(latestSaved = null), false)
        assertEquals("", b.workRecent.text.toString())
    }

    private fun binding() = ActivityIndependentWorkBinding.inflate(
        LayoutInflater.from(ApplicationProvider.getApplicationContext()))
}
