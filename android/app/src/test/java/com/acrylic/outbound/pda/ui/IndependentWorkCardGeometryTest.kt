package com.acrylic.outbound.pda.ui

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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class, qualifiers = "mdpi")
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentWorkCardGeometryTest {
    @Test fun originalCardActionWrapsAndRemainsReachableAtAllFourFontScales() {
        IndependentWorkCardUiFixture().use { f ->
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                val config = Configuration(f.activity.resources.configuration).apply { fontScale = scale }
                val context = f.activity.createConfigurationContext(config)
                val binding = ActivityIndependentWorkBinding.inflate(
                    LayoutInflater.from(ContextThemeWrapper(context, R.style.AppTheme)))
                f.activity.setContentView(binding.root)
                binding.workDetailsPanel.visibility = View.VISIBLE
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(509, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, 320, 509)
                val action = binding.workAuthorizeCard
                assertTrue(action.height >= 52); assertUnclipped(action)
                val rect = Rect(0, 0, action.width, action.height)
                binding.root.offsetDescendantRectToMyCoords(action, rect)
                binding.root.scrollTo(0, rect.top)
                assertTrue(action.isShown); assertTrue(rect.bottom - binding.root.scrollY <= 509)
                assertEquals(scale, action.resources.configuration.fontScale)
                println("work-card scale=" + scale + " height=" + action.height +
                    " lines=" + action.lineCount + " scroll=" + binding.root.scrollY)
            }
        }
    }

    private fun assertUnclipped(view: TextView) {
        val layout = checkNotNull(view.layout)
        assertTrue(layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        val width = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        (0 until layout.lineCount).forEach {
            assertEquals(0, layout.getEllipsisCount(it))
            assertTrue(layout.getLineRight(it) <= width + 0.5f)
        }
    }
}
