package com.acrylic.outbound.pda.ui

import android.view.WindowManager
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
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
class OwnRescueCardActivityTest {
    @Test fun savedGeneratedCardUnlocksAfterLogoutWithoutTypingOrNetwork() {
        OwnRescueCardUiFixture().use { f ->
            val original = f.card.readBytes(); f.base.vault.clear(); f.base.server.shutdown()
            f.choose()
            assertTrue(f.model.state.value.unlocked); assertTrue(f.model.state.value.orders.isEmpty())
            assertTrue(f.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertEquals("", f.activity.findViewById<EditText>(R.id.credential).text.toString())
            assertEquals(1, f.base.server.requestCount); assertArrayEquals(original, f.card.readBytes())
            assertFalse(f.model.state.value.message.contains(String(f.password())))
        }
    }
    @Test fun cancelledSelectionDoesNotUnlockOrChangeCredential() {
        OwnRescueCardUiFixture().use { f ->
            f.choose(null); assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("已取消"))
            f.base.credentials.unlockChoice("own:" + f.base.authority.rescuer, f.password()).recheck()
            assertEquals(1, f.base.server.requestCount)
            f.choose(); assertTrue(f.model.state.value.unlocked)
        }
    }
    @Test fun mismatchedScopeAndMalformedCardNeverUnlock() {
        OwnRescueCardUiFixture().use { f ->
            val original = f.card.readText()
            for (field in listOf("deviceId", "operatorId", "warehouseId", "deviceKeySha256")) {
                f.card.writeText(JSONObject(original).put(field, if (field == "deviceKeySha256") "b".repeat(64) else "other").toString())
                f.choose(); assertFalse(f.model.state.value.unlocked)
            }
            f.card.writeBytes(ByteArray(4097)); f.choose(); assertFalse(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun replacedCardCannotUnlockButReplacementStillCan() {
        OwnRescueCardUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            f.base.server.takeRequest(); f.choose()
            assertFalse(f.model.state.value.unlocked)
            f.base.credentials.unlockChoice("own:" + f.base.authority.rescuer, f.base.authority.password).recheck()
            assertEquals(2, f.base.server.requestCount)
        }
    }
    @Test fun revokedCardCannotRestorePreviouslyGrantedAccess() {
        OwnRescueCardUiFixture().use { f ->
            f.base.credentials.revokeOwn(); f.choose()
            assertFalse(f.model.state.value.unlocked); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun changedIdentityDuringSelectionCannotConsumeCard() {
        OwnRescueCardUiFixture().use { f ->
            f.base.saveActor(f.base.authority.operator); f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            f.base.server.takeRequest(); f.reload()
            val spinner = f.activity.findViewById<Spinner>(R.id.identity)
            val original = f.model.state.value.identities.indexOfFirst { it.id == "own:" + f.base.authority.rescuer }
            spinner.setSelection(original); f.base.await { spinner.selectedItemPosition == original }
            val picker = f.begin(); spinner.setSelection(1 - original)
            f.base.await { spinner.selectedItemPosition != original }; f.receive(picker, f.card)
            assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("已变化"))
        }
    }
    @Test fun forgedPasswordUsesOriginalPersistentAttemptLimit() {
        OwnRescueCardUiFixture().use { f ->
            val original = f.card.readText()
            f.card.writeText(JSONObject(original).put("credential", "x".repeat(32)).toString())
            repeat(5) { f.choose(); assertFalse(f.model.state.value.unlocked) }
            f.card.writeText(original); f.choose(); assertFalse(f.model.state.value.unlocked)
            assertThrows(IllegalStateException::class.java) { f.base.credentials.unlockChoice("own:" + f.base.authority.rescuer, f.password()) }
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun recreationDiscardsPendingChoiceAndRequiresNewExplicitSelection() {
        OwnRescueCardUiFixture().use { f ->
            val picker = f.begin(); f.recreate(); f.receive(picker, f.card)
            assertFalse(f.model.state.value.unlocked)
            f.choose(); assertTrue(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun supervisorChoiceCannotUseOwnCardEntry() {
        OwnRescueCardUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.importSupervisor(f.base.token, f.base.authority.password) }
            f.base.server.takeRequest(); f.reload()
            val spinner = f.activity.findViewById<Spinner>(R.id.identity)
            val supervisor = f.model.state.value.identities.indexOfFirst { it.id.startsWith("supervisor:") }
            spinner.setSelection(supervisor); f.base.await { spinner.selectedItemPosition == supervisor }
            f.activity.findViewById<android.widget.Button>(R.id.ownCardUnlock).performClick()
            assertNull(org.robolectric.Shadows.shadowOf(f.activity).nextStartedActivityForResult)
            assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("主管授权使用原独立入口"))
            assertEquals(2, f.base.server.requestCount)
        }
    }
}
