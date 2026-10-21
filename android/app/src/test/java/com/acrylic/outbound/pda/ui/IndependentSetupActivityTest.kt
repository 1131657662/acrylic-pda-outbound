package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.widget.CheckBox
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.OwnRescueCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentSetupActivityTest {
    @Test fun savedReadbackAndCustodyUseOriginalSignedPostWithoutUploadingPassword() {
        IndependentSetupUiFixture().use { f ->
            f.open(); assertTrue(f.dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val card = f.save(); val material = JSONObject(card.readText()); val password = material.getString("credential")
            assertEquals(32, password.length); assertFalse(f.credentialFile.exists()); assertEquals(0, f.server.requestCount)
            assertFalse(material.has("accessToken")); assertFalse(material.has("localSessionId"))
            assertEquals("acrylic-independent-capture-card-v1", material.getString("format"))
            f.verify(card); f.reply(); f.confirm(); assertFalse(f.dialog.isShowing)
            val request = f.request(); assertEquals("POST", request.method)
            assertEquals("/api/v1/pda/independent-credentials", request.path)
            val body = request.body.readUtf8(); assertFalse(body.contains(password))
            assertEquals(setOf("deviceId", "deviceKeySha256"), JSONObject(body).keys().asSequence().toSet())
            assertFalse(request.getHeader("Idempotency-Key").isNullOrBlank())
            assertFalse(f.credentialFile.readBytes().toString(Charsets.ISO_8859_1).contains(password))
            f.server.shutdown(); runBlocking(Dispatchers.IO) { assertTrue(f.credentials.verifyLocally(password.toCharArray()).contains("待对账")) }
            assertEquals(1, f.server.requestCount); assertEquals(password, JSONObject(card.readText()).getString("credential"))
            assertThrows(IllegalStateException::class.java) { OwnRescueCard.read(card.inputStream()) }
        }
    }
    @Test fun cancelledSaveAndDialogNeverPrepareOrSendAnyRequest() {
        IndependentSetupUiFixture().use { f ->
            f.open(); f.choose(R.id.saveCard, null, Intent.ACTION_CREATE_DOCUMENT)
            assertTrue(f.status.contains("已取消")); assertFalse(f.credentialFile.exists())
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            f.await { !f.dialog.isShowing }; assertFalse(f.dialog.isShowing); assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun wrongOrCancelledReadbackCannotEnableCustodyOrProvision() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val card = f.save(); card.writeText("synthetic-wrong-card")
            f.choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(f.dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isEnabled)
            f.choose(R.id.verifyCard, null, Intent.ACTION_OPEN_DOCUMENT)
            assertEquals(0, f.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }
    @Test fun changedLoginAfterVerifiedCardRejectsBeforeAnyPost() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card)
            f.vault.save(f.app.session.requireSession().put("localSessionId", "synthetic-other-login"))
            f.confirm(); assertTrue(f.status.contains("未确认完成"))
            assertEquals(0, f.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }
    @Test fun expectedGuardRejectsChangedVersionAndClearsPasswordBeforeAnyPost() {
        IndependentSetupUiFixture().use { f ->
            val expected = f.app.session.requireSession().put("deviceKeySha256", f.authority.binding.second)
            f.vault.save(f.app.session.requireSession().put("userAuthVersion", "2"))
            val password = f.authority.password
            runBlocking(Dispatchers.IO) {
                assertThrows(IllegalStateException::class.java) { f.credentials.provision(password, expected) }
            }
            assertTrue(password.all { it.code == 0 }); assertEquals(0, f.server.requestCount)
            assertFalse(f.credentialFile.exists())
        }
    }
    @Test fun cancelledNewSetupRetainsExistingOriginalAndCredential() {
        IndependentSetupUiFixture().use { f ->
            f.reply(); runBlocking(Dispatchers.IO) { f.credentials.provision(f.authority.password) }; f.request()
            val original = f.credentialFile.readBytes()
            f.open(); val card = f.save(); f.verify(card)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.await { !f.dialog.isShowing }
            assertFalse(f.dialog.isShowing)
            assertArrayEquals(original, f.credentialFile.readBytes())
            runBlocking(Dispatchers.IO) { f.credentials.verifyLocally(f.authority.password) }
            assertEquals(1, f.server.requestCount)
        }
    }
    @Test fun newOnlineSetupReplacesOldCredentialWithoutChangingOriginalDeadlineRules() {
        IndependentSetupUiFixture().use { f ->
            f.reply(); runBlocking(Dispatchers.IO) { f.credentials.provision(f.authority.password) }; f.request()
            val old = runBlocking(Dispatchers.IO) { f.credentials.unlock(f.authority.password) }
            f.open(); val card = f.save(); f.verify(card); f.reply(); f.confirm(); f.request()
            val password = JSONObject(card.readText()).getString("credential")
            runBlocking(Dispatchers.IO) {
                assertThrows(IllegalStateException::class.java) { old.recheck() }
                val current = f.credentials.unlock(password.toCharArray())
                assertEquals(f.authority.issued.plusSeconds(48 * 3600), current.recheck().expires)
                f.credentials.revoke(); assertThrows(IllegalStateException::class.java) { current.recheck() }
            }
            assertTrue(f.credentialFile.exists()); assertEquals(2, f.server.requestCount)
        }
    }
    @Test fun wrongSignedScopeCannotBecomeActiveCredential() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card)
            f.reply(f.authority.claims().put("warehouseId", java.util.UUID.randomUUID().toString())); f.confirm(); f.request()
            assertTrue(f.status.contains("未确认完成"))
            runBlocking(Dispatchers.IO) {
                assertThrows(IllegalStateException::class.java) { f.credentials.verifyLocally(JSONObject(card.readText()).getString("credential").toCharArray()) }
            }
            assertTrue(f.credentialFile.exists()); assertEquals(1, f.server.requestCount)
        }
    }
    @Test fun rebootCannotUseGeneratedCardToExtendOriginalCollectionTrust() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card); f.reply(); f.confirm(); f.request()
            Settings.Global.putInt(f.app.contentResolver, Settings.Global.BOOT_COUNT, 6)
            runBlocking(Dispatchers.IO) {
                assertThrows(IllegalStateException::class.java) { f.credentials.verifyLocally(JSONObject(card.readText()).getString("credential").toCharArray()) }
            }
            assertTrue(f.credentialFile.exists()); assertEquals(1, f.server.requestCount)
        }
    }
    @Test fun closedSetupCannotBeRecoveredUsingItsOldCard() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val old = f.save(); f.verify(old)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.await { !f.dialog.isShowing }
            f.open(); val fresh = f.save()
            assertNotEquals(JSONObject(old.readText()).getString("credential"), JSONObject(fresh.readText()).getString("credential"))
            f.choose(R.id.verifyCard, old, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(f.credentialFile.exists()); assertEquals(0, f.server.requestCount)
        }
    }
    @Test fun recoveryFenceAfterReadbackRejectsWithoutPreparingOrPosting() {
        IndependentSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card)
            runBlocking(Dispatchers.IO) {
                f.app.database.sync().saveRecovery(com.acrylic.outbound.pda.data.LocalRecoveryControl(
                    observedServerEpoch = "1", mode = "RECONCILING"))
            }
            f.confirm(); assertTrue(f.status.contains("未确认完成"))
            assertFalse(f.credentialFile.exists()); assertEquals(0, f.server.requestCount)
        }
    }
}
