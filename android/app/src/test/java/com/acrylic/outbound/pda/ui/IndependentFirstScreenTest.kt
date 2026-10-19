package com.acrylic.outbound.pda.ui

import android.content.Context
import android.content.res.Configuration
import android.app.Activity
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.independent.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi", application = android.app.Application::class)
class IndependentFirstScreenTest {
    @Test fun idleScreenKeepsSecondaryInputsCollapsedAtEveryFontScale() {
        scales.forEach { scale ->
            val b = binding(scale)

            IndependentWorkDisplay(b).render(IndependentScreenFixture.ui(), scanning = false)
            measure(b)

            listOf(b.workManual, b.workPassword, b.workReason, b.workFinish).forEach {
                assertFalse("scale=$scale secondary control ${it.id} shown", it.isShown)
            }
            listOf(b.workCandidate, b.workConfirm, b.workAcknowledge, b.workDiscard).forEach {
                assertFalse("scale=$scale empty candidate control ${it.id} shown", it.isShown)
            }
            assertReachable(b, b.workBack)
            assertReachable(b, b.workPause)
        }
    }

    @Test fun manualConfirmationIsVisibleWithExceptionEntryOnNormalFirstScreen() {
        val b = binding()
        val ui = IndependentScreenFixture.ui().copy(candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM"), message = "请核对本包")

        IndependentWorkDisplay(b).render(ui, scanning = true)
        measure(b)
        printGeometry(b)

        assertTrue(b.workConfirm.isEnabled)
        listOf(b.workSummary, b.workRecent, b.workCandidate, b.workConfirm, b.workPause, b.workBack, b.workDetailsToggle).forEach {
            assertTrue("${it.id} bottom=${bottom(b, it)} below first screen", bottom(b, it) <= 509)
        }
        assertFalse(b.workAcknowledge.isShown)
    }

    @Test fun longVehicleAndCandidateWrapWithoutClippingAtEveryFontScale() {
        scales.forEach { scale ->
            val b = binding(scale)
            val ui = IndependentScreenFixture.ui("沪A合成长车号12345678901234567890").copy(
                candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM"))

            IndependentWorkDisplay(b).render(ui, scanning = true)
            measure(b)

            listOf(b.workSummary, b.workRecent, b.workCandidate, b.workConfirm, b.workDetailsToggle).forEach {
                assertUnclipped(it)
                assertReachable(b, it)
            }
            assertEquals(scale, b.root.resources.configuration.fontScale)
            // TextView resolves sp to whole pixels on this Android API.
            assertEquals(16f * scale, b.workCandidate.textSize, 0.5f)
            println("scale=$scale candidatePx=${b.workCandidate.textSize} summaryLines=${b.workSummary.lineCount}")
        }
    }

    private fun assertUnclipped(view: TextView) {
        val layout = checkNotNull(view.layout)
        val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        assertTrue(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        (0 until view.lineCount).forEach {
            assertEquals(0, layout.getEllipsisCount(it))
            assertTrue("${view.id} line $it overflows $width", layout.getLineRight(it) <= width + 0.5f)
        }
    }

    @Test fun failedCaptureKeepsAcknowledgementReachableAtEveryFontScale() {
        scales.forEach { scale ->
            val b = binding(scale)
            val ui = IndependentScreenFixture.ui().copy(candidate = IndependentScreenFixture.candidate("INVALID"), message = "本包未保存，请核对")

            IndependentWorkDisplay(b).render(ui, scanning = true)
            measure(b)

            assertFalse(b.workConfirm.isShown)
            assertTrue(b.workAcknowledge.isEnabled)
            assertReachable(b, b.workAcknowledge)
            assertReachable(b, b.workBack)
        }
    }

    private fun binding(scale: Float = 1f): ActivityIndependentWorkBinding {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configured = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = scale })
        val context = ContextThemeWrapper(configured, R.style.AppTheme)
        return ActivityIndependentWorkBinding.inflate(LayoutInflater.from(context)).also {
            val activity = Robolectric.buildActivity(Activity::class.java)
            activity.get().setTheme(R.style.AppTheme)
            activity.setup().get().setContentView(it.root)
        }
    }

    private fun printGeometry(b: ActivityIndependentWorkBinding) {
        val content = b.root.getChildAt(0) as android.view.ViewGroup
        (0 until content.childCount).map { content.getChildAt(it) }.filter { it.isShown }.forEach {
            val text = it as? TextView
            println("geometry id=${it.id} top=${it.top} bottom=${it.bottom} height=${it.height} " +
                "lines=${text?.lineCount} px=${text?.textSize} text=${text?.text}")
        }
    }

    private fun measure(b: ActivityIndependentWorkBinding) {
        b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY))
        b.root.layout(0, 0, 320, 509)
    }

    private fun assertReachable(b: ActivityIndependentWorkBinding, view: View) {
        val rect = Rect(0, 0, view.width, view.height)
        b.root.offsetDescendantRectToMyCoords(view, rect)
        b.root.scrollTo(0, rect.top)
        assertTrue("${view.id} not visible after scrolling", view.isShown && bottom(b, view) > 0)
        assertTrue("${view.id} not fully reachable", bottom(b, view) <= 509)
    }

    private fun bottom(b: ActivityIndependentWorkBinding, view: View): Int {
        val rect = Rect(0, 0, view.width, view.height)
        b.root.offsetDescendantRectToMyCoords(view, rect)
        return rect.bottom - b.root.scrollY
    }

    private val scales = listOf(1f, 1.3f, 1.5f, 2f)
}
