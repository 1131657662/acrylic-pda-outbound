package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import com.acrylic.outbound.pda.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RescueOrderItemTest {
    @Test fun selectedAndExpandedItemsWrapLongBusinessDetailsAtSmallWidth() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val label = "正式 · 资料保存 2026-10-23 09:02:03\n车号 沪A12345\n订单号 SYNTHETIC-LONG-ORDER-20261023-001-EXTENDED-FOR-NARROW-SCREEN\n记录编号 01a13d4f-71b4-7caf-bf9d-000000000001"
            val adapter = ArrayAdapter(activity, R.layout.item_rescue_order, listOf(label)).apply {
                setDropDownViewResource(R.layout.item_rescue_order)
            }
            val parent = android.widget.FrameLayout(activity)
            val views = listOf(adapter.getView(0, null, parent), adapter.getDropDownView(0, null, parent))
            views.forEach { view ->
                val text = view as TextView
                text.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                text.layout(0, 0, text.measuredWidth, text.measuredHeight)
                assertEquals(label, text.text.toString()); assertNull(text.ellipsize)
                assertTrue("lineCount=${text.lineCount}, width=${text.measuredWidth}, layout=${text.layout?.width}", text.lineCount >= 4)
                assertEquals(label.length, text.layout.getLineEnd(text.lineCount - 1))
                assertTrue(text.measuredHeight >= text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
            }
        } finally { controller.pause().stop().destroy() }
    }
}
