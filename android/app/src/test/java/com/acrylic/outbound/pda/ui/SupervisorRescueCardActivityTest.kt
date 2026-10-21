package com.acrylic.outbound.pda.ui

import android.view.WindowManager
import android.widget.Button
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
class SupervisorRescueCardActivityTest {
    @Test fun generatedOriginalCardUnlocksSignedScopeAfterLogoutWithoutTypingOrNetwork() {
        SupervisorRescueCardUiFixture().use { f ->
            val bytes = f.card.readBytes(); f.base.vault.clear(); f.base.server.shutdown(); f.choose()
            assertTrue(f.model.state.value.unlocked); assertTrue(f.model.state.value.orders.isEmpty())
            assertTrue(f.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertEquals("", f.activity.findViewById<EditText>(R.id.credential).text.toString())
            assertFalse(f.model.state.value.message.contains(String(f.password())))
            val grant = f.grant(); grant.check(f.base.selected())
            for (field in listOf("source", "localOrderId", "operatorId", "deviceId", "warehouseId"))
                assertThrows(IllegalStateException::class.java) { grant.check(f.base.selected().put(field, "another")) }
            assertArrayEquals(bytes, f.card.readBytes()); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun cancelledSelectionLeavesCredentialUsableAndRequiresNewExplicitSelection() {
        SupervisorRescueCardUiFixture().use { f ->
            val cipher = f.base.credentialFile.readBytes(); f.choose(null)
            assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("已取消"))
            assertArrayEquals(cipher, f.base.credentialFile.readBytes()); f.grant().recheck()
            f.choose(); assertTrue(f.model.state.value.unlocked); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun wrongOriginalGrantRescuerIdentityOrDigestCannotUnlock() {
        SupervisorRescueCardUiFixture().use { f ->
            val original = f.card.readText()
            for (field in listOf("grantId", "rescuerId", "deviceId", "operatorId", "warehouseId",
                "deviceKeySha256", "authorizationSha256")) {
                f.card.writeText(JSONObject(original).put(field, if (field.endsWith("Sha256")) "b".repeat(64) else "other").toString())
                f.choose(); assertFalse(f.model.state.value.unlocked)
            }
            f.card.writeText(original); f.choose(); assertTrue(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun wrongPurposeMalformedAndOversizedCardsDoNotConsumeOriginalPasswordAttempts() {
        SupervisorRescueCardUiFixture().use { f ->
            val original = f.card.readText()
            for (bad in listOf("wrong", "x".repeat(4097), JSONObject(original).put("format", "own-card").toString())) {
                f.card.writeText(bad); f.choose(); assertFalse(f.model.state.value.unlocked)
            }
            f.card.writeText(original); f.choose(); assertTrue(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun selectionChangedDuringPickerCannotConsumePreviousAuthorizationCard() {
        SupervisorRescueCardUiFixture().use { f ->
            f.base.reply(); runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            assertEquals("/api/v1/pda/devices/me", f.base.server.takeRequest().path); f.reload()
            val spinner = f.activity.findViewById<Spinner>(R.id.identity)
            val supervisor = f.model.state.value.identities.indexOfFirst { it.id.startsWith("supervisor:") }
            spinner.setSelection(supervisor); f.base.await { spinner.selectedItemPosition == supervisor }
            val picker = f.begin(); spinner.setSelection(1 - supervisor)
            f.base.await { spinner.selectedItemPosition != supervisor }; f.receive(picker, f.card)
            assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("已变化"))
        }
    }
    @Test fun recreatedPageDiscardsPendingPickerAndRequiresNewSelection() {
        SupervisorRescueCardUiFixture().use { f ->
            val picker = f.begin(); f.recreate(); f.receive(picker, f.card)
            assertFalse(f.model.state.value.unlocked); f.choose(); assertTrue(f.model.state.value.unlocked)
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun ownIdentityCannotUseSupervisorCardEntry() {
        SupervisorRescueCardUiFixture().use { f ->
            f.base.reply(); runBlocking(Dispatchers.IO) { f.base.credentials.provisionOwn(f.base.authority.password) }
            f.base.server.takeRequest(); f.reload()
            val spinner = f.activity.findViewById<Spinner>(R.id.identity)
            val own = f.model.state.value.identities.indexOfFirst { it.id.startsWith("own:") }
            spinner.setSelection(own); f.base.await { spinner.selectedItemPosition == own }
            f.activity.findViewById<Button>(R.id.supervisorCardUnlock).performClick()
            assertNull(org.robolectric.Shadows.shadowOf(f.activity).nextStartedActivityForResult)
            assertFalse(f.model.state.value.unlocked)
            assertTrue(f.activity.findViewById<TextView>(R.id.status).text.contains("本人卡使用本人入口"))
        }
    }
}
