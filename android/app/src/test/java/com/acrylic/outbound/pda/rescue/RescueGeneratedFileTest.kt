package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipInputStream
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueGeneratedFileTest {
    @get:Rule val folder = TemporaryFolder()
    private val password get() = "synthetic-package-passphrase-only".toCharArray()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }
    private fun grant(f: WorkFixture) = RescueReadGrant(RescueReadIdentity(f.session.getString("deviceId"),
        f.session.getString("userId"), f.session.getString("warehouseId"))) {}
    private fun signer(): RescueSigner {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return RescueSigner(key.public.encoded) { text -> Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(text.toByteArray(Charsets.UTF_8)); Base64.getEncoder().encodeToString(sign())
        } }
    }
    @Test fun generatedEncryptedFileSurvivesNewServiceAndCannotBecomeSuccessOrRelease() = scenario { f ->
        f.scan(); val access = grant(f)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        val files = RescueExportFiles(f.context); val secret = password
        val generated = files.generate(snapshot, access to signer(), secret)
        assertTrue(secret.all { it == '\u0000' })
        val recovered = RescueExportFiles(f.context).recover(generated.id, access)
        assertEquals(generated.id, RescueExportFiles(f.context).available(access).single().id)
        assertEquals(generated.sha256, recovered.sha256)
        val contents = ZipInputStream(RescueEnvelope.decrypt(recovered.file.readBytes(), password).inputStream()).use { zip ->
            buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
        }
        assertTrue(contents.getValue("scans.ndjson").toString(Charsets.UTF_8).contains("300500"))
        assertFalse(contents.containsKey("submission.json")); assertNull(f.db.submissions().forOrder(f.assignment))
        assertEquals("SCANNING", f.db.work().state(f.assignment)); assertEquals(1L, f.db.scans().total(f.assignment).count)
        File("build/rescue-generated-vectors").apply { mkdirs() }.resolve("room-generated.pdarescue").writeBytes(recovered.file.readBytes())
    }
    @Test fun corruptPrivateFileCannotBeRecoveredForExternalCopy() = scenario { f ->
        f.scan(); val access = grant(f)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        val files = RescueExportFiles(f.context)
        val generated = files.generate(snapshot, access to signer(), password)
        generated.file.appendBytes(byteArrayOf(1))
        assertEquals(generated.id, RescueExportFiles(f.context).available(access).single().id)
        assertThrows(IllegalStateException::class.java) { files.recover(generated.id, access) }
        assertTrue(generated.file.exists())
    }
    @Test fun anotherWarehouseCannotRecoverPreviouslyGeneratedPrivateFile() = scenario { f ->
        f.scan(); val access = grant(f)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        val files = RescueExportFiles(f.context)
        val generated = files.generate(snapshot, access to signer(), password)
        val bad = RescueReadGrant(access.identity.copy(warehouseId = "other")) {}
        assertTrue(RescueExportFiles(f.context).available(bad).isEmpty())
        assertThrows(IllegalStateException::class.java) { files.recover(generated.id, bad) }
        assertTrue(generated.file.exists())
    }
    @Test fun signerWithDifferentPublicKeyCannotPublishVerifiedPackage() = scenario { f ->
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), grant(f))
        val first = signer(); val second = signer()
        assertThrows(IllegalStateException::class.java) {
            RescueBundle.build(snapshot, RescueSigner(first.publicKey, second.sign))
        }
    }
    @Test fun failedGenerationJournalNeverBecomesRecoverableOrChangesOriginalFacts() = scenario { f ->
        f.scan(); val access = grant(f)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        val key = signer().publicKey
        val failing = RescueSigner(key) { throw java.io.IOException("synthetic interrupted generation") }
        val secret = password
        assertThrows(java.io.IOException::class.java) { RescueExportFiles(f.context).generate(snapshot, access to failing, secret) }
        assertTrue(secret.all { it == '\u0000' })
        assertTrue(RescueExportFiles(f.context).available(access).isEmpty())
        val journal = File(f.context.noBackupFilesDir, "rescue-exports").listFiles { file -> file.extension == "json" }!!.single()
        val state = JSONObject(journal.readText())
        assertEquals("GENERATION_INCOMPLETE", state.getString("state")); assertFalse(state.has("sha256"))
        assertNull(f.db.submissions().forOrder(f.assignment)); assertEquals("SCANNING", f.db.work().state(f.assignment))
        assertEquals(1L, f.db.scans().total(f.assignment).count)
    }
}
