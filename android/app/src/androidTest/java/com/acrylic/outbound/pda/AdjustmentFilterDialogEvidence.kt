package com.acrylic.outbound.pda

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import java.io.File

internal object AdjustmentFilterDialogEvidence {
    fun save(directory: File) {
        onView(isRoot()).inRoot(isDialog()).perform(object : ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "Verify real adjustment dialog buttons and retain secure view rendering"
            override fun perform(controller: UiController, view: View) {
                listOf(android.R.id.button1, android.R.id.button2).forEach { id ->
                    check(isCompletelyDisplayed().matches(view.findViewById<View>(id))) { "Dialog action is clipped" }
                }
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val scale = view.resources.configuration.fontScale.toString().replace('.', '-')
                val name = "dialog-final-$scale-${System.nanoTime()}.png"
                File(directory, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        })
    }
}
