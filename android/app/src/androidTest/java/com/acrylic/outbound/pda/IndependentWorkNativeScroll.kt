package com.acrylic.outbound.pda

import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher

/** The native IME consumes padding inside the edge-to-edge ScrollView. */
internal fun independentWorkNativeScroll(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
    override fun getDescription() = "scroll within the viewport remaining above native IME"
    override fun perform(controller: UiController, view: View) {
        var parent = view.parent
        while (parent != null && parent !is ScrollView) parent = parent.parent
        val scroll = checkNotNull(parent as? ScrollView)
        val rect = Rect(0, 0, view.width, view.height)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
        check(view.height <= viewport) { "Control exceeds the actual native viewport" }
        scroll.scrollTo(0, maxOf(0, rect.top - scroll.paddingTop - (viewport - view.height) / 2))
        controller.loopMainThreadUntilIdle()
        check(isDisplayingAtLeast(90).matches(view)) { "Control remains clipped above native IME" }
    }
}
