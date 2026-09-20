package com.acrylic.outbound.pda.temporary

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.ActivityTemporaryPreparationBinding
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, qualifiers = "w320dp-h400dp-mdpi")
class TemporaryLayoutTest {
    @Test fun shortScreenAndLargeFontKeepFullRecoveryActionScrollable() {
        val original = ApplicationProvider.getApplicationContext<Context>()
        for (scale in listOf(1f, 2f)) {
            val context = original.createConfigurationContext(Configuration(original.resources.configuration).apply { fontScale = scale })
            val view = ActivityTemporaryPreparationBinding.inflate(LayoutInflater.from(context))
            view.status.text = "服务器正在恢复对账，原资料和准备请求已保留，请联系主管核对"
            view.details.text = "有效至：2026-09-22 08:00:00\n库存 12639 包 · 状态 14000 包"
            view.prepare.text = "联网恢复原准备请求"; view.prepare.isEnabled = true
            view.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.root.layout(0, 0, 320, 400)
            assertTrue(view.prepare.height >= 56); assertTrue(view.back.height >= 48)
            assertTrue(view.prepare.layout.height <= view.prepare.height - view.prepare.compoundPaddingTop - view.prepare.compoundPaddingBottom)
            assertTrue(view.status.layout.height <= view.status.height - view.status.compoundPaddingTop - view.status.compoundPaddingBottom)
            assertTrue(view.root.getChildAt(0).height > 400)
            assertTrue(view.prepare.right <= view.root.getChildAt(0).width)
        }
    }
}
