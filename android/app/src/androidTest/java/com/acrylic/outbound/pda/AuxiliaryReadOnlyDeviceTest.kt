package com.acrylic.outbound.pda

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acrylic.outbound.pda.ui.DeviceSettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class AuxiliaryReadOnlyDeviceTest {
    @Test fun settingsAndCancelledLogoutPreserveBoundIdentityAndSession() {
        val app = ApplicationProvider.getApplicationContext<PdaApplication>()
        val device = app.session.settings.device
        check(device.isNotBlank()) { "Requires an already bound test device; never create an identity here" }
        val originalSession = app.session.saved().toString()
        val originalPairing = app.pairing.saved().toString()
        val sound = app.workSound.enabled
        ActivityScenario.launch(DeviceSettingsActivity::class.java).use { scenario ->
            try {
                onView(withId(R.id.sound)).perform(scrollTo(), click())
                assertEquals(!sound, app.workSound.enabled)
                onView(withId(R.id.sound)).perform(click())
                onView(withId(R.id.logout)).perform(scrollTo(), click())
                onView(withText("退出当前人员登录？")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText("设备身份、已下载资料和原作业记录保留。退出不会放弃订单或释放占用。"))
                    .inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText("返回")).inRoot(isDialog()).perform(click())
                scenario.onActivity { activity ->
                    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    val result = JSONArray(); snapshot(activity.window.decorView, result)
                    File(app.getExternalFilesDir(null), "r04-settings-native.json").writeText(result.toString(2))
                }
            } finally { app.workSound.enabled = sound }
        }
        assertEquals(device, app.session.settings.device)
        assertEquals(originalPairing, app.pairing.saved().toString())
        assertEquals(originalSession, app.session.saved().toString())
    }

    private fun snapshot(view: View, result: JSONArray) {
        if (view is TextView && view.visibility == View.VISIBLE) {
            val bounds = Rect(); val visible = view.getGlobalVisibleRect(bounds)
            result.put(JSONObject().put("text", view.text.toString()).put("visible", visible)
                .put("bounds", bounds.toShortString()).put("lines", view.lineCount))
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) snapshot(view.getChildAt(index), result)
    }
}
