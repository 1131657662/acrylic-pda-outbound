package com.acrylic.outbound.pda.ui

import android.content.Context
import android.content.res.Configuration
import android.view.*
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityScanAdjustmentBinding
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class AdjustmentLayoutTest {
    @Test fun largeFontsKeepFullOriginalRowAndNavigationReachableByScrolling() {
        val original = ApplicationProvider.getApplicationContext<Context>()
        listOf(1f, 1.3f, 1.5f, 2f).forEach { scale ->
            val context = original.createConfigurationContext(Configuration(original.resources.configuration).apply { fontScale = scale })
            val b = ActivityScanAdjustmentBinding.inflate(LayoutInflater.from(context))
            b.title.text = "正式单 · 沪A合成长车号123456789"
            val row = Button(context).apply { text = "123456. 003A0000001\n合成长产品名称型号123456789 · 300.123 kg · 已保存"; textSize = 16f; isAllCaps = false }
            b.rows.addView(row)
            repeat(19) { index -> b.rows.addView(Button(context).apply { text = "${index + 2}. 003A0000002\n合成长产品名称 · 300.123 kg · 已保存"; textSize = 16f; isAllCaps = false }) }
            b.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY))
            b.root.layout(0, 0, 320, 509)
            assertTrue(b.root.getChildAt(0).height > 509)
            b.root.scrollTo(0, b.root.getChildAt(0).height)
            assertTrue(b.root.scrollY > 0)
            assertTrue(row.layout.height <= row.height - row.compoundPaddingTop - row.compoundPaddingBottom)
            assertTrue(b.previous.right <= b.root.width); assertTrue(b.next.right <= b.root.width)
            assertTrue(b.back.height >= 48); assertTrue(b.locate.height >= 48)
        }
    }
}
