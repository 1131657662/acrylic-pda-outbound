package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import com.acrylic.outbound.pda.rescue.*
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
class RescueSignedSupervisorFileActivityTest {
    @Test fun confirmedTrustedFilePersistsEncryptedVerifierAndUnlocksOnlySignedScopeOffline() {
        RescueSignedFileFixture().use { f ->
            f.reply(); val original = f.select(f.token)
            assertFalse(f.credentialFile.exists()); assertEquals(0, f.server.requestCount)
            assertTrue(f.dialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            f.confirm(); f.assertAuthorityGet()
            assertTrue(f.model.state.value.message.contains("只读范围已预置"))
            assertFalse(f.model.state.value.unlocked)
            val cipher = f.credentialFile.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(cipher.contains(f.token)); assertFalse(cipher.contains(String(f.authority.password)))
            assertFalse(cipher.contains("synthetic-must-not-persist"))
            assertEquals(f.token, original.readText())
            f.vault.clear(); f.server.shutdown(); f.unlock()
            assertTrue(f.model.state.value.unlocked); assertTrue(f.model.state.value.orders.isEmpty())
            val reopened = RescueCredentials(f.app, f.app.session)
            val grant = reopened.unlockChoice("supervisor:" + f.authority.grantId, f.authority.password)
            grant.check(f.selected())
            for (field in listOf("source", "localOrderId", "operatorId", "deviceId", "warehouseId"))
                assertThrows(IllegalStateException::class.java) { grant.check(f.selected().put(field, "another")) }
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun cancellingTrustedFileDoesNotFetchAuthorityOrSaveVerifier() {
        RescueSignedFileFixture().use { f ->
            val original = f.select(f.token)
            f.dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            f.await { !f.dialog().isShowing }
            assertFalse(f.credentialFile.exists()); assertTrue(f.credentials.choices().isEmpty())
            assertEquals(0, f.server.requestCount); assertEquals(f.token, original.readText())
        }
    }

    @Test fun materialCannotPromoteItsOwnSigningKeyToTrustedAuthority() {
        RescueSignedFileFixture().use { f ->
            val attacker = RescueSupervisorFixture()
            val forged = attacker.token(f.authority.claims().put("deviceKeySha256", f.digest))
            val wrapper = JSONObject().put("signedAuthorization", forged).put("signingKey", attacker.trusted).toString()
            f.select(wrapper); f.confirm()
            assertFalse(f.credentialFile.exists()); assertTrue(f.credentials.choices().isEmpty())
            assertEquals(0, f.server.requestCount); assertFalse(f.model.state.value.unlocked)
            assertFalse(f.model.state.value.message.contains(forged))
        }
    }

    @Test fun trustedSignatureForAnotherDeviceCannotBeImported() {
        RescueSignedFileFixture().use { f ->
            val wrong = f.authority.token(f.authority.claims().put("deviceKeySha256", "b".repeat(64)))
            f.select(wrong); f.confirm()
            assertTrue(f.model.state.value.message.contains("设备或登记钥不匹配"))
            assertFalse(f.credentialFile.exists()); assertEquals(0, f.server.requestCount)
        }
    }

    @Test fun differentAuthenticatedActorCannotProvisionSignedScope() {
        RescueSignedFileFixture().use { f ->
            f.saveActor(f.authority.operator); f.reply(); f.select(f.token); f.confirm(); f.assertAuthorityGet()
            assertTrue(f.model.state.value.message.contains("指定救援人"))
            assertFalse(f.credentialFile.exists()); assertFalse(f.model.state.value.unlocked)
        }
    }

    @Test fun onlineOriginalMismatchCannotPersistImportedAuthorization() {
        RescueSignedFileFixture().use { f ->
            f.reply("synthetic-different-original"); f.select(f.token); f.confirm(); f.assertAuthorityGet()
            assertTrue(f.model.state.value.message.contains("原授权与导入原件不一致"))
            assertFalse(f.credentialFile.exists()); assertTrue(f.credentials.choices().isEmpty())
        }
    }

    @Test fun expiredTrustedAuthorizationCannotBeProvisionedThroughFile() {
        RescueSignedFileFixture().use { f ->
            val expired = f.authority.token(f.authority.claims(f.authority.now.plusSeconds(60)).put("deviceKeySha256", f.digest))
            f.reply(expired, f.authority.now.plusSeconds(120)); f.select(expired); f.confirm(); f.assertAuthorityGet()
            assertTrue(f.model.state.value.message.contains("期限到期"))
            assertFalse(f.credentialFile.exists()); assertFalse(f.model.state.value.unlocked)
        }
    }

    @Test fun signedRevocationFileRemovesStoredChoiceWithoutLoginOrNetwork() {
        RescueSignedFileFixture().use { f ->
            f.reply(); f.select(f.token); f.confirm(); f.assertAuthorityGet()
            val reopened = RescueCredentials(f.app, f.app.session)
            val grant = reopened.unlockChoice("supervisor:" + f.authority.grantId, f.authority.password)
            f.vault.clear(); f.server.shutdown()
            val revocation = f.authority.token(f.authority.revocation(f.token).put("deviceKeySha256", f.digest))
            val original = f.select(revocation); f.confirm()
            assertTrue(f.model.state.value.message.contains("撤销签名已核验并保存"))
            assertTrue(f.credentials.choices().isEmpty()); assertFalse(f.model.state.value.unlocked)
            assertThrows(IllegalStateException::class.java) { grant.recheck() }
            assertEquals(revocation, original.readText()); assertEquals(1, f.server.requestCount)
        }
    }
}
