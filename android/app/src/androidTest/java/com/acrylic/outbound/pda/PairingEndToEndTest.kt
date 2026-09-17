package com.acrylic.outbound.pda

import android.graphics.Rect
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.network.ConnectionSettings
import com.acrylic.outbound.pda.pairing.PairingRepository
import com.acrylic.outbound.pda.pairing.PairingVault
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.ui.PairingActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PairingEndToEndTest {
    @Test fun shortCodePairingRecoversLostResponsesAndLocalWriteFailureThenAuthenticates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val app = context.applicationContext as PdaApplication
        val file = File(context.filesDir, "pairing-fixture.json"); val fixture = JSONObject(file.readText()); file.delete()
        assertEquals("", app.session.settings.device); val originalKey = app.session.publicKey()
        val admin = PairingAdmin(fixture)
        SubmissionProxy(fixture.getString("baseUrl")).use { proxy ->
            proxy.offlinePairingChallenge.set(true)
            assertThrows(Exception::class.java) { app.pairing.start(proxy.base) }
            val originalId = app.pairing.saved()!!.getJSONObject("payload").getString("applicationId")
            val reopened = PairingRepository(PairingVault(context), app.session.settings, DeviceKeys())
            proxy.losePairingCreateResponse.set(true)
            assertThrows(Exception::class.java) { reopened.resume() }
            val first = reopened.resume(); assertEquals(originalId, first.getJSONObject("result").getString("applicationId"))
            assertEquals("WAITING", first.getJSONObject("result").getString("status"))
            admin.approve(first.getJSONObject("result").getString("shortCode")); val pending = reopened.resume()
            assertEquals("PENDING", pending.getJSONObject("result").getString("status")); assertEquals("", app.session.settings.device)
            verifySmallScreen(pending.getJSONObject("result").getString("shortCode"))
            proxy.losePairingConfirmResponse.set(true)
            assertThrows(Exception::class.java) { reopened.confirm() }
            failLocalBindingThenRecover(app, reopened)
            val device = app.session.settings.device; assertTrue(device.isNotBlank()); assertEquals(originalKey, app.session.publicKey())
            assertEquals(device, reopened.resume().getJSONObject("result").getString("deviceId"))
            app.session.login(fixture.getString("username"), fixture.getString("password"))
            assertEquals(device, app.session.call("pda/devices/me").getString("id"))
            val evidence = JSONObject().put("applicationId", originalId).put("deviceId", device).put("keyPreserved", true)
                .put("offlineStartRecovered", true).put("lostCreateRecovered", true).put("lostConfirmRecovered", true).put("localWriteFailureRecovered", true)
                .put("reopenedIdentityVerified", true).put("realDeviceProofLogin", true).put("smallScreenControlsVerified", true)
            File(context.filesDir, "pairing-e2e-evidence.json").writeText(evidence.toString())
        }
    }
    private fun failLocalBindingThenRecover(app: PdaApplication, repository: PairingRepository) {
        val blocker = File(app.noBackupFilesDir, "device-binding.json.new"); assertTrue(blocker.mkdir())
        try {
            assertThrows(Exception::class.java) { repository.resume() }
            assertEquals("", ConnectionSettings(app).device)
            assertEquals("COMPLETED", repository.saved()!!.getJSONObject("result").getString("status"))
        } finally { assertTrue(blocker.delete()) }
        val restored = PairingRepository(PairingVault(app), ConnectionSettings(app), DeviceKeys()).resume()
        assertEquals(restored.getJSONObject("result").getString("deviceId"), ConnectionSettings(app).device)
    }
    private fun verifySmallScreen(code: String) {
        ActivityScenario.launch(PairingActivity::class.java).use { screen ->
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.pairingCode))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText(code.chunked(4).joinToString(" "))))
            screen.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                for (id in listOf(R.id.pairingCode, R.id.pairingStatus, R.id.confirmPairing)) {
                    val view = activity.findViewById<android.view.View>(id); val visible = Rect()
                    assertTrue(view.getGlobalVisibleRect(visible)); assertEquals(view.height, visible.height())
                }
            }
        }
    }
}
