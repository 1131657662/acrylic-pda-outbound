package com.acrylic.outbound.pda

import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher

/** Position the actual ScrollView before clicks; fully visible controls remain a requirement. */
internal fun independentSkuScroll(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
    override fun getDescription() = "scroll actual view rectangle immediately, then verify visibility"
    override fun perform(controller: UiController, view: View) {
        var parent = view.parent
        while (parent != null && parent !is ScrollView) parent = parent.parent
        val scroll = checkNotNull(parent as? ScrollView) { "Expected the product ScrollView" }
        val rect = Rect(0, 0, view.width, view.height)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        scroll.scrollTo(0, maxOf(0, rect.top - scroll.height / 3))
        controller.loopMainThreadUntilIdle()
        check(isDisplayingAtLeast(90).matches(view)) { "Control is not actually fully displayed after scroll" }
    }
}
