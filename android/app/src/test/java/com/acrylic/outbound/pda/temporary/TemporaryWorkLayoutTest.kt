package com.acrylic.outbound.pda.temporary

import android.content.Context
import android.content.res.Configuration
import android.view.*
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.databinding.*
import com.acrylic.outbound.pda.ui.TemporaryWorkRenderer
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class, qualifiers = "w320dp-h400dp-mdpi")
class TemporaryWorkLayoutTest {
    @Test fun draftKeepsStartSaveAndLongFieldsScrollableWithLargeFonts() {
        contexts().forEach { context ->
            val b = ActivityTemporaryDraftBinding.inflate(LayoutInflater.from(context))
            b.status.text = "TMP-00000000-0000-4000-8000-000000000001\n临时资格已到期，原草稿和扫码保留，请联系主管"
            measure(b.root)
            assertTrue(b.start.height >= 56); assertTrue(b.save.height >= 56)
            assertTrue(b.root.getChildAt(0).height > 400)
            assertTrue(b.status.layout.height <= b.status.height - b.status.compoundPaddingTop - b.status.compoundPaddingBottom)
            assertTrue(b.start.right <= b.root.getChildAt(0).width)
        }
    }
    @Test fun workPageAlwaysShowsTemporaryAndNeverEnablesDispatch() {
        contexts().forEach { context ->
            val b = ActivityWorkBinding.inflate(LayoutInflater.from(context))
            TemporaryWorkRenderer(b).render(TemporaryWorkUi(ready = true, blocked = "资格已到期，原记录保留"), "")
            measure(b.root)
            assertTrue(b.vehicle.text.contains("临时")); assertTrue(b.message.text.contains("不得放行"))
            assertFalse(b.closeOrder.isEnabled); assertEquals(View.VISIBLE, b.recall.visibility); assertFalse(b.recall.isEnabled)
            assertTrue(b.confirm.height >= 48)
        }
    }
    private fun contexts(): List<Context> {
        val original = ApplicationProvider.getApplicationContext<Context>()
        return listOf(1f, 2f).map { scale -> original.createConfigurationContext(Configuration(original.resources.configuration).apply { fontScale = scale }) }
    }
    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 320, 400)
    }
}
