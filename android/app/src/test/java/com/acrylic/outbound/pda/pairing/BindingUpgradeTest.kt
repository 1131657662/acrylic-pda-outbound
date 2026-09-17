package com.acrylic.outbound.pda.pairing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.network.ConnectionSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class BindingUpgradeTest {
    @Test fun previousPreferencesRemainReadableAndCannotBeRebound() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val id = UUID.randomUUID().toString()
        context.getSharedPreferences("connection", Context.MODE_PRIVATE).edit().putString("server", "https://example.test/api/v1/")
            .putString("device", id).putString("key", "original").commit()
        val settings = ConnectionSettings(context); assertEquals(id, settings.device); settings.verifyKey("original")
        assertThrows(IllegalStateException::class.java) { settings.bind("https://other.test/api/v1/", id, "original") }
        assertThrows(IllegalStateException::class.java) { settings.bind(settings.server, UUID.randomUUID().toString(), "different") }
        settings.bind(settings.server, id, "original")
        assertEquals(id, ConnectionSettings(context).device); assertEquals("https://example.test/api/v1/", ConnectionSettings(context).server)
        assertEquals(id, context.getSharedPreferences("connection", Context.MODE_PRIVATE).getString("device", ""))
    }
    @Test fun newBindingIsCompleteAfterReopeningSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val id = UUID.randomUUID().toString()
        ConnectionSettings(context).bind("https://example.test/api/v1/", id, "key")
        val reopened = ConnectionSettings(context); assertEquals(id, reopened.device); reopened.verifyKey("key")
        assertThrows(IllegalStateException::class.java) { reopened.verifyKey("other") }
    }
    @Test fun unsafeNetworkEndpointsAreRejected() {
        for (address in listOf("http://public.test/api/v1/", "https://name:password@example.test/api/v1/", "https://example.test/api/v1/?other=true"))
            assertThrows(IllegalStateException::class.java) { ConnectionSettings.validateServer(address) }
        assertEquals("https://example.test/api/v1/", ConnectionSettings.validateServer("https://example.test/api/v1"))
    }
}
