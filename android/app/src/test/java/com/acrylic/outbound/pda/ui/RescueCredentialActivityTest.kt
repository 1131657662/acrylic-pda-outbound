package com.acrylic.outbound.pda.ui

import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSupervisorFileActivityTest.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueCredentialActivityTest {
    @Test fun absentSetupShowsBusinessNextStepWithoutCreatingCredential() {
        val app = ApplicationProvider.getApplicationContext<PdaApplication>()
        val before = app.noBackupFilesDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        val controller = Robolectric.buildActivity(RescueCredentialActivity::class.java).setup()
        try {
            val status = controller.get().findViewById<TextView>(R.id.status)
            await { status.text.contains("未发现此人") }
            assertTrue(status.text.contains("故障前联网登录并设置"))
            assertTrue(status.text.contains("不影响普通领取和扫码"))
            assertEquals(before, app.noBackupFilesDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet())
        } finally { controller.pause().stop().destroy(); app.database.close() }
    }

    private fun await(condition: () -> Boolean) {
        repeat(500) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(10) }
        fail("credential status did not settle")
    }
}
