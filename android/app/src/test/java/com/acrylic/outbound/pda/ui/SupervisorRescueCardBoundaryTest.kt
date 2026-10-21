package com.acrylic.outbound.pda.ui

import android.provider.Settings
import com.acrylic.outbound.pda.rescue.RescueCredentials
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
class SupervisorRescueCardBoundaryTest {
    @Test fun replacedPasswordRejectsOldCardButReplacementStillUnlocksOriginalScope() {
        SupervisorRescueCardUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.importSupervisor(f.token, f.base.authority.password) }
            f.base.assertAuthorityGet(); f.choose(); assertFalse(f.model.state.value.unlocked)
            f.base.credentials.unlockChoice("supervisor:" + f.base.authority.grantId, f.base.authority.password).check(f.base.selected())
            assertEquals(2, f.base.server.requestCount)
        }
    }
    @Test fun signedRevocationCannotBeUndoneByReadingOriginalCard() {
        SupervisorRescueCardUiFixture().use { f ->
            val old = f.grant(); val card = f.card.readBytes()
            val revoked = f.base.authority.token(f.base.authority.revocation(f.token).put("deviceKeySha256", f.base.digest))
            f.base.vault.clear(); f.base.server.shutdown()
            f.base.credentials.importSupervisor(revoked, charArrayOf()); f.choose()
            assertFalse(f.model.state.value.unlocked); assertThrows(IllegalStateException::class.java) { old.recheck() }
            assertArrayEquals(card, f.card.readBytes()); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun forgedPasswordStillUsesPersistentOriginalAttemptLimitAcrossRecreation() {
        SupervisorRescueCardUiFixture().use { f ->
            val original = f.card.readText(); f.card.writeText(JSONObject(original).put("credential", "x".repeat(32)).toString())
            repeat(5) { f.choose(); assertFalse(f.model.state.value.unlocked) }
            f.card.writeText(original); f.recreate(); f.choose(); assertFalse(f.model.state.value.unlocked)
            assertThrows(IllegalStateException::class.java) { RescueCredentials(f.base.app, f.base.app.session)
                .unlockChoice("supervisor:" + f.base.authority.grantId, f.password()) }
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun finiteAuthorizationCannotBeExtendedByRebootAndCardRead() {
        SupervisorRescueCardUiFixture(expiring = true).use { f ->
            Settings.Global.putInt(f.base.app.contentResolver, Settings.Global.BOOT_COUNT, 2)
            f.base.vault.clear(); f.choose(); assertFalse(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun finiteAuthorizationExpiryStillAppliesToCard() {
        SupervisorRescueCardUiFixture(expiring = true).use { f ->
            ShadowSystemClock.advanceBy(Duration.ofSeconds(61)); f.choose()
            assertFalse(f.model.state.value.unlocked); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun existingPermanentReadonlyScopeSurvivesRebootWithoutNewAuthority() {
        SupervisorRescueCardUiFixture().use { f ->
            Settings.Global.putInt(f.base.app.contentResolver, Settings.Global.BOOT_COUNT, 2)
            f.base.vault.clear(); f.base.server.shutdown(); f.choose()
            assertTrue(f.model.state.value.unlocked); f.grant().check(f.base.selected())
            assertEquals(1, f.base.server.requestCount)
        }
    }
}
