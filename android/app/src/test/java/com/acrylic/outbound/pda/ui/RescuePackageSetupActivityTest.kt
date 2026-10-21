package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.*
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescuePackageSetupActivityTest {
    @Test fun nonemptyOriginalRecordsEncryptOnlyAfterReadbackAndSeparateCustody() {
        RescuePackageSetupUiFixture().use { f ->
            f.vault.clear(); f.server.shutdown(); f.open()
            assertTrue(f.dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val material = f.save(); val bytes = material.readBytes(); val secret = JSONObject(material.readText()).getString("packagePassword")
            assertTrue(f.packages().isEmpty()); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            f.verify(material); assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            f.confirm(); assertFalse(f.dialog.isShowing); assertTrue(f.model.state.value.canCopy)
            val encrypted = f.packages().single(); val plain = RescueEnvelope.decrypt(encrypted.readBytes(), secret.toCharArray())
            val contents = ZipInputStream(plain.inputStream()).use { zip ->
                buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
            }
            assertTrue(contents.getValue("scans.ndjson").toString(Charsets.UTF_8).contains("300500"))
            assertFalse(contents.containsKey("submission.json")); assertFalse(plain.toString(Charsets.ISO_8859_1).contains(secret))
            assertFalse(f.model.state.value.toString().contains(secret)); assertEquals("", f.activity.findViewById<EditText>(R.id.packagePassword).text.toString())
            assertFalse(f.model.state.value.copyState.contains("目标写入与回读一致")); assertEquals(1, f.server.requestCount)
            assertArrayEquals(bytes, material.readBytes())
            runBlocking(Dispatchers.IO) { assertEquals(1L, f.work.db.scans().total(f.work.assignment).count); assertNull(f.work.db.submissions().forOrder(f.work.assignment)) }
        }
    }
    @Test fun cancellingDocumentAndDialogLeavesOriginalRecordAndNoNewPackage() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); f.receive(f.begin(R.id.savePackageSecret), null)
            assertTrue(f.status.contains("选择已取消")); f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertTrue(f.packages().isEmpty()); assertFalse(f.model.state.value.canCopy); assertTrue(f.model.state.value.canGenerate)
            f.await { f.activity.findViewById<Button>(R.id.generateSecurePackage).isEnabled }
            assertTrue(f.activity.findViewById<Button>(R.id.generateSecurePackage).isEnabled)
        }
    }
    @Test fun wrongReadbackCannotConfirmOrGenerate() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val material = f.save(); material.writeText("synthetic-wrong-material")
            f.receive(f.begin(R.id.verifyPackageSecret), material)
            assertTrue(f.status.contains("本次未完成")); assertFalse(f.dialog.findViewById<CheckBox>(R.id.packageCustody).isEnabled)
            assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled); assertTrue(f.packages().isEmpty())
        }
    }
    @Test fun revokedOriginalGrantCannotGenerateAfterMaterialVerification() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val material = f.save(); f.verify(material); f.revoke(); f.confirm()
            assertTrue(f.dialog.isShowing); assertTrue(f.status.contains("本次未完成")); assertTrue(f.packages().isEmpty())
            assertFalse(f.model.state.value.canCopy)
        }
    }
    @Test fun lockingOriginalScopeDiscardsPreparedMaterial() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val material = f.save(); f.verify(material); f.model.lock(); f.confirm()
            assertTrue(f.packages().isEmpty()); assertFalse(f.model.state.value.canCopy); assertFalse(f.model.state.value.unlocked)
        }
    }
    @Test fun replacementSnapshotCannotReuseVerifiedOldMaterial() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val material = f.save(); f.verify(material); f.model.readSnapshot()
            f.await { !f.model.state.value.busy }; f.confirm()
            assertTrue(f.packages().isEmpty()); assertFalse(f.model.state.value.canCopy)
        }
    }
    @Test fun recreationDropsPendingFileCallbackWithoutDeletingMaterial() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val material = f.save(); val bytes = material.readBytes(); val intent = f.begin(R.id.verifyPackageSecret)
            f.recreate(); f.receive(intent, material)
            assertTrue(f.packages().isEmpty()); assertArrayEquals(bytes, material.readBytes())
            assertFalse(f.model.state.value.canCopy)
        }
    }
    @Test fun closedDraftOldMaterialCannotBeUsedForNextPackage() {
        RescuePackageSetupUiFixture().use { f ->
            f.open(); val old = f.save(); f.verify(old); f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            f.await { f.activity.findViewById<Button>(R.id.generateSecurePackage).isEnabled }
            f.open(); val fresh = f.save(); assertNotEquals(JSONObject(old.readText()).getString("packagePassword"), JSONObject(fresh.readText()).getString("packagePassword"))
            f.receive(f.begin(R.id.verifyPackageSecret), old); assertTrue(f.status.contains("本次未完成"))
            assertFalse(f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled); assertTrue(f.packages().isEmpty())
        }
    }
}
