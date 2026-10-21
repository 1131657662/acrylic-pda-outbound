package com.acrylic.outbound.pda.ui

import android.provider.Settings
import com.acrylic.outbound.pda.data.LocalRecoveryControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentCardBoundaryTest {
    @Test fun replacedCardCannotVerifyCurrentCredential() {
        IndependentCardUiFixture().use { f ->
            f.provision(); f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.provision(f.base.authority.password) }; f.base.request()
            f.choose(); assertTrue(f.model.state.value.message.contains("未通过"))
            runBlocking(Dispatchers.IO) { f.base.credentials.verifyLocally(f.base.authority.password) }
            assertEquals(2, f.base.server.requestCount)
        }
    }
    @Test fun revocationCannotBeUndoneByReadingOriginalCard() {
        IndependentCardUiFixture().use { f ->
            f.provision(); f.base.credentials.revoke(); f.choose()
            assertTrue(f.model.state.value.message.contains("未通过")); assertTrue(f.base.credentialFile.exists())
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun forgedPasswordUsesOriginalPersistentAttemptLimit() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.card.readText()
            f.card.writeText(JSONObject(original).put("credential", "x".repeat(32)).toString())
            repeat(5) { f.choose(); assertTrue(f.model.state.value.message.contains("未通过")) }
            f.card.writeText(original); f.recreate(); f.choose()
            assertTrue(f.model.state.value.message.contains("未通过"))
            runBlocking(Dispatchers.IO) {
                assertThrows(IllegalStateException::class.java) { f.base.credentials.verifyLocally(f.password()) }
            }
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun rebootCannotExtendTrustedCollectionTime() {
        IndependentCardUiFixture().use { f ->
            f.provision(); Settings.Global.putInt(f.base.app.contentResolver, Settings.Global.BOOT_COUNT, 6); f.choose()
            assertTrue(f.model.state.value.message.contains("未通过")); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun expiredSignedCredentialCannotBeRefreshedByCard() {
        IndependentCardUiFixture().use { f ->
            f.provision(f.base.authority.claims().put("expiresAt", f.base.authority.issued.plusSeconds(60).toString()))
            ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); f.choose()
            assertTrue(f.model.state.value.message.contains("未通过")); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun recoveryFenceRejectsCardAndPreservesOriginal() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.base.credentialFile.readBytes()
            runBlocking(Dispatchers.IO) { f.base.app.database.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "RECONCILING")) }
            f.choose(); assertTrue(f.model.state.value.message.contains("未通过"))
            assertArrayEquals(original, f.base.credentialFile.readBytes()); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun logoutDoesNotTurnIndependentCardIntoOfflineRescueCard() {
        IndependentCardUiFixture().use { f ->
            f.provision(); f.base.vault.clear()
            f.activity.findViewById<android.widget.Button>(com.acrylic.outbound.pda.R.id.verifyCard).performClick()
            assertNull(org.robolectric.Shadows.shadowOf(f.activity).nextStartedActivityForResult)
            assertTrue(f.model.state.value.message.contains("先联网")); assertEquals(1, f.base.server.requestCount)
        }
    }
}
