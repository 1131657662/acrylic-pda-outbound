package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.R
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SupervisorRescueSetupActivityTest {
    @Test fun generatedCardRequiresCustodyAndOriginalOnlineAuthorityBeforeOfflineScope() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); assertTrue(f.dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(f.activity.findViewById<Button>(R.id.unlock).isEnabled)
            val card = f.save(); val password = String(f.password(card)); f.verify(card)
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
            f.base.reply(); f.confirm(); assertFalse(f.dialog.isShowing)
            val request = checkNotNull(f.base.server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("GET", request.method); assertEquals(0L, request.bodySize)
            assertEquals("/api/v1/pda/rescue-grants/" + f.base.authority.grantId, request.path)
            assertFalse(request.headers.toString().contains(password))
            val cipher = f.base.credentialFile.readBytes().toString(Charsets.ISO_8859_1)
            for (secret in listOf(password, f.base.token, "synthetic-must-not-persist")) assertFalse(cipher.contains(secret))
            f.base.vault.clear(); f.base.server.shutdown(); f.unlock(card)
            assertTrue(f.base.model.state.value.unlocked)
            val grant = RescueCredentials(f.base.app, f.base.app.session)
                .unlockChoice("supervisor:" + f.base.authority.grantId, f.password(card))
            grant.check(f.base.selected())
            for (field in listOf("source", "localOrderId", "operatorId", "deviceId", "warehouseId"))
                assertThrows(IllegalStateException::class.java) { grant.check(f.base.selected().put(field, "another")) }
            assertEquals(password, String(f.password(card))); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun cancelledSetupRetainsOldVerifierAndHeldGrantWithoutAuthorityRequest() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.importSupervisor(f.base.token, f.base.authority.password) }
            f.base.assertAuthorityGet()
            val old = f.base.credentials.unlockChoice("supervisor:" + f.base.authority.grantId, f.base.authority.password)
            val bytes = f.base.credentialFile.readBytes()
            f.open(); val card = f.save(); f.verify(card)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.base.await { !f.dialog.isShowing }
            assertArrayEquals(bytes, f.base.credentialFile.readBytes()); old.recheck()
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun onlineReplacementInvalidatesPreviousGrantAndOldPassword() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.importSupervisor(f.base.token, f.base.authority.password) }
            f.base.assertAuthorityGet()
            val old = f.base.credentials.unlockChoice("supervisor:" + f.base.authority.grantId, f.base.authority.password)
            f.open(); val card = f.save(); f.verify(card); f.base.reply(); f.confirm(); f.base.assertAuthorityGet()
            assertFalse(f.dialog.isShowing)
            assertThrows(IllegalStateException::class.java) { old.recheck() }
            f.base.credentials.unlockChoice("supervisor:" + f.base.authority.grantId, f.password(card)).check(f.base.selected())
            assertThrows(IllegalStateException::class.java) {
                f.base.credentials.unlockChoice("supervisor:" + f.base.authority.grantId, f.base.authority.password)
            }
        }
    }
    @Test fun cancelledFileResultsNeverPermitCustodyOrProvision() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); f.choose(R.id.saveCard, null, Intent.ACTION_CREATE_DOCUMENT)
            assertTrue(f.status.contains("选择已取消")); f.save()
            f.choose(R.id.verifyCard, null, Intent.ACTION_OPEN_DOCUMENT)
            assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(f.dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isEnabled)
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
        }
    }
    @Test fun wrongOrOversizedCardCannotConfirm() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save()
            for (bad in listOf("wrong-card", "x".repeat(4097))) {
                card.writeText(bad); f.choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
                assertTrue(f.status.contains("未确认完成"))
                assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
        }
    }
    @Test fun changedActorAfterReadbackCannotProvision() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card); f.base.saveActor(f.base.authority.operator)
            f.confirm(); assertTrue(f.status.contains("未确认完成"))
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
        }
    }
    @Test fun changedPermissionVersionAfterReadbackCannotProvision() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card)
            f.base.vault.save(f.base.app.session.requireSession().put("userAuthVersion", "changed"))
            f.confirm(); assertTrue(f.status.contains("未确认完成"))
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
        }
    }
    @Test fun onlineOriginalMismatchPreservesCardWithoutProvision() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); val bytes = card.readBytes(); f.verify(card)
            f.base.reply("different-original"); f.confirm(); f.base.assertAuthorityGet()
            assertTrue(f.status.contains("未确认完成")); assertArrayEquals(bytes, card.readBytes())
            assertFalse(f.base.credentialFile.exists()); assertFalse(f.base.model.state.value.unlocked)
        }
    }
    @Test fun expiredOnlineAuthorityCannotProvisionGeneratedCard() {
        SupervisorRescueSetupUiFixture().use { f ->
            val token = f.base.authority.token(f.base.authority.claims(f.base.authority.now.plusSeconds(60))
                .put("deviceKeySha256", f.base.digest))
            f.open(token); val card = f.save(); f.verify(card)
            f.base.reply(token, f.base.authority.now.plusSeconds(120)); f.confirm(); f.base.assertAuthorityGet()
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.base.credentialFile.exists())
        }
    }
    @Test fun closedDraftCannotReusePreviouslySavedCard() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val old = f.save(); f.verify(old)
            f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); f.base.await { !f.dialog.isShowing }
            f.open(); val fresh = f.save(); assertNotEquals(String(f.password(old)), String(f.password(fresh)))
            f.choose(R.id.verifyCard, old, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.base.credentialFile.exists())
            assertEquals(0, f.base.server.requestCount)
        }
    }
    @Test fun recreatedPageDropsDraftWithoutProvisioningOrRetainingSecret() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.open(); val card = f.save(); f.verify(card); val oldDialog = f.dialog
            f.base.recreate(); assertFalse(oldDialog.isShowing)
            assertFalse(f.base.credentialFile.exists()); assertEquals(0, f.base.server.requestCount)
            f.open(); val fresh = f.save(); assertNotEquals(String(f.password(card)), String(f.password(fresh)))
            f.choose(R.id.verifyCard, card, Intent.ACTION_OPEN_DOCUMENT)
            assertTrue(f.status.contains("未确认完成")); assertFalse(f.base.credentialFile.exists())
        }
    }
    @Test fun onlineRevocationRejectsGeneratedReplacementAndPreservesOriginalCard() {
        SupervisorRescueSetupUiFixture().use { f ->
            f.base.reply()
            runBlocking(Dispatchers.IO) { f.base.credentials.importSupervisor(f.base.token, f.base.authority.password) }
            f.base.assertAuthorityGet()
            val revoked = f.base.authority.token(f.base.authority.revocation(f.base.token).put("deviceKeySha256", f.base.digest))
            val data = JSONObject().put("status", "REVOKED").put("revocation", JSONObject().put("signedRevocation", revoked))
                .put("authorization", JSONObject().put("signedAuthorization", f.base.token))
            f.open(); val card = f.save(); val bytes = card.readBytes(); f.verify(card)
            f.base.server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody(
                JSONObject().put("code", "OK").put("message", "synthetic").put("data", data)
                    .put("requestId", "synthetic-revocation").put("serverTime", f.base.authority.now.toString())
                    .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL").toString()))
            f.confirm(); f.base.assertAuthorityGet(); assertTrue(f.status.contains("未确认完成"))
            assertArrayEquals(bytes, card.readBytes()); assertTrue(f.base.credentials.choices().isEmpty())
        }
    }
    @Test fun destroyingPageDuringAuthorityReadCannotProvisionClosedDraft() {
        SupervisorRescueSetupUiFixture().use { f ->
            val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
            val complete = java.util.concurrent.CountDownLatch(1)
            f.activity.lifecycleScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion { complete.countDown() }
            val data = JSONObject().put("status", "AUTHORIZED").put("revocation", JSONObject.NULL)
                .put("authorization", JSONObject().put("signedAuthorization", f.base.token))
            val body = JSONObject().put("code", "OK").put("message", "synthetic").put("data", data)
                .put("requestId", "synthetic-cancel").put("serverTime", f.base.authority.now.toString())
                .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL").toString()
            f.base.server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                    entered.countDown(); check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            f.open(); val card = f.save(); f.verify(card)
            f.dialog.findViewById<CheckBox>(R.id.custodyConfirmed).isChecked = true
            f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            try { f.base.await { entered.count == 0L }; f.base.destroy() }
            finally { release.countDown() }
            f.base.await { complete.count == 0L }
            assertFalse(f.base.credentialFile.exists()); assertTrue(f.base.credentials.choices().isEmpty())
        }
    }
}
