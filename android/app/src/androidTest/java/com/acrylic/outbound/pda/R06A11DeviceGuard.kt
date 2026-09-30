package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal object R06A11DeviceGuard {
    fun require(avd: String) {
        assertTrue("Only an emulator is authorized", android.os.Build.MODEL.startsWith("sdk_gphone"))
        assertEquals("com.acrylic.outbound.pda.staging",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        assertEquals(avd, InstrumentationRegistry.getArguments().getString("r06a11Avd"))
    }
}
