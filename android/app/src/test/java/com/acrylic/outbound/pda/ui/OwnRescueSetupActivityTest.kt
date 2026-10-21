package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import com.acrylic.outbound.pda.R
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
class OwnRescueSetupActivityTest {
    @Test fun generatedCardNeedsReopenAndCustodyBeforeOnlineSetupThenWorksOffline() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); assertTrue(f.dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val card = f.save(); val material = JSONObject(card.readText()); val password = material.getString("credential")
            assertEquals(32, password.length); assertEquals(0, f.base.server.requestCount)
            assertFalse(f.credentialFile.exists()); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            f.verify(card); assertFalse(f.credentialFile.exists())
            f.base.reply(); f.confirm(); assertFalse(f.dialog.isShowing)
            val request = f.base.server.takeRequest()
            assertEquals("GET", request.method); assertEquals("/api/v1/pda/devices/me", request.path)
            assertEquals(0L, request.bodySize); assertEquals(1, f.base.server.requestCount)
            val cipher = f.credentialFile.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(cipher.contains(password)); assertFalse(cipher.contains("synthetic-must-not-persist"))
            assertFalse(material.has("accessToken")); assertFalse(material.has("localSessionId"))
            f.base.vault.clear(); f.base.server.shutdown()
            f.ownGrant(password.toCharArray()).recheck()
            assertEquals(password, JSONObject(card.readText()).getString("credential"))
        }
    }

    @Test fun cancelledDocumentSelectionAndDialogNeverProvision() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); f.choose(R.id.saveCard, null, Intent.ACTION_CREATE_DOCUMENT)
            assertTrue(f.status.contains("选择已取消")); assertFalse(f.credentialFile.exists())
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.base.await { !f.dialog.isShowing }
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }

    @Test fun wrongReadbackCardCannotBeConfirmedOrProvisioned() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); card.writeText("synthetic-wrong-card")
            f.choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成"))
            assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(f.dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isEnabled)
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }

    @Test fun differentLoginAfterCardReadbackCannotProvisionOldDraft() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card); f.base.saveActor(f.base.authority.operator)
            f.confirm(); assertTrue(f.status.contains("未确认完成"))
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }

    @Test fun onlineResetReplacesLostCredentialAndInvalidatesHeldGrant() {
        OwnRescueSetupUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            assertEquals("GET", f.base.server.takeRequest().method)
            val old = f.ownGrant(f.base.authority.password)
            f.open(); val card = f.save(); f.verify(card); f.base.reply(); f.confirm()
            assertFalse(f.dialog.isShowing); assertEquals("GET", f.base.server.takeRequest().method)
            assertThrows(IllegalStateException::class.java) { old.recheck() }
            f.ownGrant(JSONObject(card.readText()).getString("credential").toCharArray()).recheck()
            assertThrows(IllegalStateException::class.java) { f.ownGrant(f.base.authority.password) }
        }
    }

    @Test fun cancelledSetupRetainsExistingCredentialAndHeldGrant() {
        OwnRescueSetupUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            f.base.server.takeRequest(); val old = f.ownGrant(f.base.authority.password)
            f.open(); val card = f.save(); f.verify(card)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.base.await { !f.dialog.isShowing }
            old.recheck(); f.ownGrant(f.base.authority.password).recheck()
            assertEquals(1, f.base.server.requestCount)
        }
    }

    @Test fun cancelledReadbackCannotConfirmCustodyOrProvision() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); f.save(); f.choose(R.id.verifyCard, null, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("选择已取消"))
            assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(f.dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isEnabled)
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }

    @Test fun closedDraftCannotBeRecoveredBySelectingItsOldCard() {
        OwnRescueSetupUiFixture().use { f ->
            f.open(); val old = f.save(); f.verify(old)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.base.await { !f.dialog.isShowing }
            f.open(); val fresh = f.save()
            assertNotEquals(JSONObject(old.readText()).getString("credential"), JSONObject(fresh.readText()).getString("credential"))
            f.choose(R.id.verifyCard, old, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }

    @Test fun expectedIdentityGuardRejectsChangedSessionAndClearsPasswordBeforeAnyGet() {
        OwnRescueSetupUiFixture().use { f ->
            val expected = f.base.app.session.requireSession(); f.base.saveActor(f.base.authority.operator)
            val password = f.base.authority.password
            assertThrows(IllegalStateException::class.java) {
                f.base.credentials.provisionOwn(password, expected)
            }
            assertTrue(password.all { it == '\u0000' })
            assertEquals(0, f.base.server.requestCount); assertFalse(f.credentialFile.exists())
        }
    }
}
