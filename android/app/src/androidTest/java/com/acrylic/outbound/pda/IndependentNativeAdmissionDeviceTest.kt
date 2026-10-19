package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Admission prerequisite only; no imported key, injected session, credential or business rows. */
class IndependentNativeAdmissionDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "independent-credential-device")

    @Test fun registersNativePublicKeyThroughIsolatedAdminHttp() {
        val input = input()
        val publicKey = app.session.publicKey()
        val web = FaultRestartWebClient(input.getString("baseUrl"), input)
        web.login()
        val device = web.post("devices", JSONObject().put("deviceCode", "independent-${UUID.randomUUID().toString().take(20)}")
            .put("name", "合成独立采集隔离模拟器").put("publicKey", publicKey))
        app.session.settings.bind(input.getString("baseUrl"), device.getString("deviceId"), publicKey)
        assertEquals(publicKey, app.session.publicKey())
        assertNull(app.session.saved())
        assertNull(com.acrylic.outbound.pda.independent.IndependentCredentialVault(app).read())
        File(directory, "admission.json").writeText(JSONObject().put("purpose", "SYNTHETIC_INDEPENDENT_NATIVE")
            .put("deviceId", device.getString("deviceId")).put("devicePublicKey", publicKey)
            .put("realAdminHttp", true).put("nativeKeystore", true).put("sessionInjected", false).toString())
    }

    private fun input(): JSONObject {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("independentNativeAdmission") == "true")
        check(app.session.settings.device.isBlank()) { "Unregistered isolated device required" }
        check(!File(directory, "admission.json").exists()) { "Preserve earlier admission" }
        val input = JSONObject(File(directory, "input.json").readText())
        check(input.getString("purpose") == "SYNTHETIC_INDEPENDENT_NATIVE")
        check(input.getString("baseUrl").matches(Regex("http://127\\.0\\.0\\.1:[1-9][0-9]{3,4}/api/v1/")))
        UUID.fromString(input.getString("warehouseId"))
        return input
    }
}
