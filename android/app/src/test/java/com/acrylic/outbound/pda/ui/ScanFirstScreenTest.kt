package com.acrylic.outbound.pda.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.*
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.temporary.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class ScanFirstScreenTest {
    private fun binding(scale: Float = 1f): ActivityWorkBinding {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = scale })
        return ActivityWorkBinding.inflate(LayoutInflater.from(context))
    }
    private fun measure(b: ActivityWorkBinding) {
        b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY))
        b.root.layout(0, 0, 320, 509)
    }
    private fun bottom(b: ActivityWorkBinding, view: View): Int {
        val rect = Rect(0, 0, view.width, view.height)
        b.root.offsetDescendantRectToMyCoords(view, rect); return rect.bottom - b.root.scrollY
    }
    private fun candidate() = WorkCapture("capture", CaptureContext("assignment", "order", "device", "operator", "warehouse",
        1, 1, "003A0000001", "003A0000001", "MANUAL", "MANUAL", 1, 1, 1, "snapshot", 1,
        "epoch", "auth", "epoch", 1), state = "WAITING_CONFIRM")

    @Test fun formalManualCandidateKeepsConfirmationAndExceptionEntryOnFirstScreen() {
        val b = binding()
        WorkRenderer(b).render(WorkUi(order = WorkOrder("assignment", "order", "苏A12345", 10, 1),
            candidate = candidate(), pending = 1, ready = true, message = "请确认手输包"), "")
        measure(b)
        listOf(b.vehicle, b.source, b.totals, b.candidate, b.confirm, b.recentSaved, b.moreActions).forEach {
            assertEquals(View.VISIBLE, it.visibility); assertTrue("${it.id} bottom ${bottom(b, it)} below first screen", bottom(b, it) <= 509)
        }
        assertTrue(b.totals.text.contains("已保存 0")); assertTrue(b.confirm.isEnabled)
        assertEquals(View.GONE, b.secondaryActions.visibility)
    }
    @Test fun idleQueueDoesNotShowTechnicalZeroCounters() {
        val b = binding()
        WorkRenderer(b).render(WorkUi(order = WorkOrder("assignment", "order", "车", 10, 1), ready = true, message = "扫码校验后保存"), "")
        assertFalse(b.message.text.contains("待落盘 0")); assertFalse(b.message.text.contains("待处理 0"))
        assertEquals(View.GONE, b.confirm.visibility)
    }
    @Test fun durableFailureBlocksNewInputWithoutClaimingCurrentPackageSaved() {
        val b = binding()
        WorkRenderer(b).render(WorkUi(order = WorkOrder("assignment", "order", "车", 10, 1),
            blocked = "存储失败，原输入保留", queued = 2), "声音未就绪")
        assertFalse(b.validate.isEnabled); assertFalse(b.adjustPackages.isEnabled)
        assertTrue(b.message.text.contains("存储失败")); assertTrue(b.message.text.contains("待落盘 2"))
        assertFalse(b.recentSaved.text.contains("最近本机已保存："))
    }
    @Test fun temporarySourceAndSavedCountStayOnFirstScreen() {
        val b = binding()
        val draft = TemporaryDraft("order", "device", "operator", "warehouse", "auth", "TEMP", "date", vehicleNo = "苏A12345", state = "SCANNING")
        TemporaryWorkRenderer(b).render(TemporaryWorkUi(draft = draft, total = ScanTotal(3, 123456), ready = true, message = "已保存到本机"), "")
        measure(b)
        assertTrue(b.source.text.contains("临时单")); assertTrue(b.totals.text.contains("已保存 3"))
        assertTrue(bottom(b, b.moreActions) <= 509); assertFalse(b.message.text.contains("待落盘 0"))
    }
    @Test fun largeFontsPreserveLongVehicleCandidateAndReachableActions() {
        listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
            val b = binding(scale)
            WorkRenderer(b).render(WorkUi(order = WorkOrder("assignment", "order", "沪A合成长车号123456789", Long.MAX_VALUE, 1),
                candidate = candidate(), pending = 1, ready = true, message = "请核对原输入"), "")
            measure(b)
            listOf(b.vehicle, b.totals, b.candidate, b.confirm).forEach { view ->
                assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                for (line in 0 until view.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
            }
            b.root.scrollTo(0, b.root.getChildAt(0).height)
            assertTrue(bottom(b, b.moreActions) <= 509)
        }
    }
}
