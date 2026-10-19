package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentLargeFontScreenTest {
    @Test fun pendingActionAndLastSaveFitWithoutLongModelAtEveryFontScale() {
        val overflow = mutableListOf<String>()
        listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
            val b = binding(scale)
            val ui = IndependentScreenFixture.ui("沪A12345")
            val saved = ui.latestSaved!!.copy(scan = ui.latestSaved.scan.copy(model = "超长型号".repeat(20)))

            IndependentWorkDisplay(b).render(ui.copy(latestSaved = saved,
                message = "已保存到本机；独立采集待主管对账，不得放行。",
                candidate = IndependentScreenFixture.candidate("WAITING_CONFIRM")), true)
            measure(b)

            listOf(b.workBack, b.workVehicle, b.workWarning, b.workSummary, b.workCandidate, b.workConfirm,
                b.workRecent, b.workPause, b.workDetailsToggle).forEach {
                viewportError(b, it, scale)?.let(overflow::add)
            }
            assertTrue(b.workConfirm.isEnabled)
            assertTrue(b.workRecentDetails.text.contains(saved.scan.model))
            assertFalse(b.workRecent.text.contains(saved.scan.model))
            assertEquals(16f * scale, b.workCandidate.textSize, 0.5f)
            assertTrue(b.workConfirm.height >= 52)
            assertTrue(b.workBack.height >= 48)
            assertEquals("沪A12345", b.workVehicle.text.toString())
            assertEquals("独立采集·待主管对账·不得放行", b.workWarning.text.toString())
        }
        assertTrue(overflow.joinToString("\n"), overflow.isEmpty())
    }

    @Test fun actualErrorsAndOriginalCaptureIdentityRemainAvailable() {
        val b = binding(2f)
        val error = "原条码校验失败；本包未保存。".repeat(10)
        val candidate = IndependentScreenFixture.candidate("INVALID")

        IndependentWorkDisplay(b).render(IndependentScreenFixture.ui().copy(blocked = "存储失败，保全原件",
            candidate = candidate.copy(processing = candidate.processing.copy(error = error))), true, "声音未就绪")
        measure(b)

        assertTrue(b.workCandidate.text.contains(error))
        assertTrue(b.workCandidateDetails.text.contains("原输入序 2 · 手输"))
        assertTrue(b.workBlocked.isShown)
        assertTrue(b.workMessage.text.contains("声音未就绪"))
        assertFalse(b.workConfirm.isShown)
        assertFalse(b.workAcknowledge.isEnabled)
        assertUnclipped(b.workCandidate)
        assertUnclipped(b.workBlocked)
    }

    private fun binding(scale: Float): ActivityIndependentWorkBinding {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val configured = activity.createConfigurationContext(Configuration(activity.resources.configuration).apply { fontScale = scale })
        val b = ActivityIndependentWorkBinding.inflate(LayoutInflater.from(ContextThemeWrapper(configured, R.style.AppTheme)))
        activity.setContentView(b.root)
        return b
    }

    private fun measure(b: ActivityIndependentWorkBinding) {
        b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY))
        b.root.layout(0, 0, 320, 509)
    }

    private fun viewportError(b: ActivityIndependentWorkBinding, view: View, scale: Float): String? {
        val rect = Rect(0, 0, view.width, view.height)
        b.root.offsetDescendantRectToMyCoords(view, rect)
        println("scale=$scale id=${view.id} rect=$rect")
        assertUnclipped(view as TextView)
        return if (view.isShown && rect.top >= 0 && rect.bottom <= 509) null
            else "scale=$scale id=${view.id} below first screen $rect"
    }

    private fun assertUnclipped(view: TextView) {
        val layout = checkNotNull(view.layout)
        val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        assertTrue(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        (0 until layout.lineCount).forEach {
            assertEquals(0, layout.getEllipsisCount(it))
            assertTrue(layout.getLineRight(it) <= width + 0.5f)
        }
    }
}
