package com.acrylic.m71demo

import android.app.Application
import android.content.*
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.acrylic.m71demo.device.ScannerReceiver
import com.acrylic.m71demo.device.Telemetry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ScannerReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val received = mutableListOf<Triple<String, String, String?>>()
    private val receiver = ScannerReceiver(context, Telemetry(context)) { raw, source, id ->
        received.add(Triple(raw, source, id))
    }

    @Before fun register() {
        // Robolectric does not automatically grant the merged signature permission on API 28.
        val permission = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(permission))
        shadowOf(context as Application).grantPermissions(permission)
        receiver.register()
    }
    @After fun unregister() { receiver.unregister() }

    @Test fun vendorBroadcastCannotMasqueradeAsSoftwareInjection() {
        context.sendBroadcast(Intent(ScannerReceiver.ACTION)
            .putExtra(ScannerReceiver.BARCODE_KEY, "003B0051601")
            .putExtra(ScannerReceiver.TEST_ID_KEY, "spoofed-test-id"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(Triple("003B0051601", "BROADCAST", null)), received)
    }

    @Test fun privateBroadcastPreservesCompleteTextAndExplicitTestSource() {
        context.sendBroadcast(softwareIntent("003B0051601"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(Triple("003B0051601", "TEST_INJECTION", "test-id")), received)
    }

    @Test fun softwareBroadcastWithoutIdIsRejected() {
        val input = softwareIntent("003B0051601").apply { removeExtra(ScannerReceiver.TEST_ID_KEY) }
        context.sendBroadcast(input)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
    }

    @Test fun stoppedReceiverDoesNotAcceptSoftwareOrVendorInput() {
        receiver.unregister()
        context.sendBroadcast(softwareIntent("003B0051601"))
        context.sendBroadcast(Intent(ScannerReceiver.ACTION).putExtra(ScannerReceiver.BARCODE_KEY, "003B0051601"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received.isEmpty())
    }

    private fun softwareIntent(raw: String) = Intent(ScannerReceiver.SOFTWARE_ACTION)
        .setPackage(context.packageName).putExtra(ScannerReceiver.BARCODE_KEY, raw)
        .putExtra(ScannerReceiver.TEST_ID_KEY, "test-id")
}
