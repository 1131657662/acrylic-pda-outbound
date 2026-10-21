package com.acrylic.outbound.pda.ui

import android.view.WindowManager
import android.widget.EditText
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
class IndependentCardActivityTest {
    @Test fun generatedCardVerifiesOfflineWithoutTypingUploadingOrRetainingGrant() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.card.readBytes(); f.base.server.shutdown(); f.choose()
            assertTrue(f.model.state.value.message.contains("原校验器核验通过"))
            assertTrue(f.model.state.value.message.contains("创建任务仍须原核验"))
            assertEquals("", f.activity.findViewById<EditText>(R.id.password).text.toString())
            assertTrue(f.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(f.model.state.value.message.contains(String(f.password())))
            assertArrayEquals(original, f.card.readBytes()); assertEquals(1, f.base.server.requestCount)
            val count = runBlocking(Dispatchers.IO) { f.base.app.database.independentTasks().count() }
            assertEquals(0L, count)
        }
    }
    @Test fun cancelledSelectionPreservesCredentialBytesAndAllowsFreshExplicitSelection() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.base.credentialFile.readBytes(); f.choose(null)
            assertTrue(f.model.state.value.message.contains("已取消"))
            assertArrayEquals(original, f.base.credentialFile.readBytes())
            f.choose(); assertTrue(f.model.state.value.message.contains("原校验器核验通过"))
            assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun wrongBindingPurposeAndMalformedCardsCannotVerify() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.card.readText(); val vault = f.base.credentialFile.readBytes()
            for (field in listOf("deviceId", "operatorId", "warehouseId", "deviceKeySha256", "purpose", "format")) {
                f.card.writeText(JSONObject(original).put(field, if (field == "deviceKeySha256") "b".repeat(64) else "other").toString())
                f.choose(); assertTrue(f.model.state.value.message.contains("未通过"))
            }
            f.card.writeBytes(ByteArray(4097)); f.choose(); assertTrue(f.model.state.value.message.contains("未通过"))
            assertArrayEquals(vault, f.base.credentialFile.readBytes()); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun changedLoginDuringSelectionDoesNotConsumeCard() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.base.credentialFile.readBytes(); val picker = f.begin()
            f.base.vault.save(JSONObject(f.base.vault.read().toString()).put("localSessionId", "different-login"))
            f.receive(picker, f.card)
            assertTrue(f.model.state.value.message.contains("已变化"))
            assertArrayEquals(original, f.base.credentialFile.readBytes()); assertEquals(1, f.base.server.requestCount)
        }
    }
    @Test fun recreationDiscardsPendingCardResultAndRequiresNewSelection() {
        IndependentCardUiFixture().use { f ->
            f.provision(); val original = f.base.credentialFile.readBytes(); val picker = f.begin(); f.recreate()
            f.receive(picker, f.card)
            assertFalse(f.model.state.value.message.contains("核验通过")); assertArrayEquals(original, f.base.credentialFile.readBytes())
            f.choose(); assertTrue(f.model.state.value.message.contains("原校验器核验通过"))
            assertEquals(1, f.base.server.requestCount)
        }
    }
}
