package com.acrylic.outbound.pda.pairing

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.ui.PairingActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = PdaApplication::class)
class PairingRecoveryUiTest {
    @Test fun unreadableOriginalApplicationCannotBecomeNewRegistration() {
        val app = ApplicationProvider.getApplicationContext<PdaApplication>()
        val vault = File(app.noBackupFilesDir, "pairing.enc")
        val original = byteArrayOf(1, 2, 3, 4)
        vault.writeBytes(original)
        val controller = Robolectric.buildActivity(PairingActivity::class.java).setup()
        val activity = controller.get()
        try {
            awaitMessage(activity)
            assertEquals(View.GONE, activity.findViewById<Button>(R.id.startPairing).visibility)
            val retry = activity.findViewById<Button>(R.id.retryPairing)
            assertEquals("重试读取本机申请", retry.text.toString())
            retry.performClick()
            awaitMessage(activity)
            assertArrayEquals(original, vault.readBytes())
            assertEquals("", app.session.settings.device)
        } finally { controller.pause().stop().destroy() }
    }

    private fun awaitMessage(activity: PairingActivity) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (activity.findViewById<TextView>(R.id.pairingStatus).text.contains("无法读取本机登记申请")) return
            Thread.sleep(10)
        }
        fail("Original application read failure must be shown with a recovery action")
    }
}
