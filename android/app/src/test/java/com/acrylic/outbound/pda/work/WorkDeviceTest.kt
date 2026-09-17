package com.acrylic.outbound.pda.work

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.device.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class WorkDeviceTest {
    @Test fun receiverOnlyAcceptsRegisteredForegroundFullStringsWithoutDebouncingDuplicates() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val inputs = ArrayList<String>(); val failures = ArrayList<String>()
        val receiver = ScannerReceiver(context, { inputs.add(it) }, { failures.add(it) })
        fun broadcast(value: String) { context.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, value)); shadowOf(Looper.getMainLooper()).idle() }
        broadcast("003A0000001"); assertTrue(inputs.isEmpty())
        receiver.register(); broadcast("003A0000001"); broadcast("003A0000001"); broadcast("a".repeat(129))
        context.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra("com.android.decode.intentwedge.barcode_data", byteArrayOf(1)))
        shadowOf(Looper.getMainLooper()).idle(); receiver.unregister(); broadcast("003A0000002")
        assertEquals(listOf("003A0000001", "003A0000001"), inputs); assertEquals(2, failures.size)
    }

    @Test fun physicalKeyCycleOnlyConfirmsOnceAndScannerTriggersAreNotBusinessCommands() {
        val keys = WorkKeys("Mobydata/m71/m71:9/M01.04.04.20230626/jenkins06261456:user/dev-keys")
        val down = key(66, 28, KeyEvent.ACTION_DOWN); val up = key(66, 28, KeyEvent.ACTION_UP)
        assertEquals("CONFIRM", keys.command(down)); assertTrue(keys.firstDown(down))
        assertFalse(keys.firstDown(down)); assertFalse(keys.firstDown(key(66, 28, KeyEvent.ACTION_DOWN, 1)))
        assertFalse(keys.firstDown(up)); assertTrue(keys.firstDown(down))
        assertNull(keys.command(key(293, 317, KeyEvent.ACTION_DOWN)))
        assertEquals("VALIDATE", keys.command(key(288, 315, KeyEvent.ACTION_DOWN)))
        assertNull(WorkKeys("different-firmware").command(key(288, 315, KeyEvent.ACTION_DOWN)))
    }
    private fun key(code: Int, scan: Int, action: Int, repeat: Int = 0) = KeyEvent(1, 2, action, code, repeat, 0, 5, scan, 0, 257)
}
