package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryStorageTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun midTransactionFailureRollsBackAllActivationRowsAndOriginalRequestCanRetry() = io {
        TemporaryFixture(folder.root).use { f ->
            val request = f.saveRequest()
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER temporary_abort BEFORE INSERT ON temporary_part_chunk
                WHEN NEW.part='rules' BEGIN SELECT RAISE(ABORT,'storage interruption'); END""")
            assertThrows(Exception::class.java) { f.activate(request) }; assertNull(f.repository.prepared(request))
            assertEquals(0, f.db.temporary().count(f.receipt.getString("authorizationId"), "inventory"))
            assertEquals(request, f.db.temporary().request(request.key)); f.reopen()
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER temporary_abort")
            assertNotNull(f.activate(request)); assertEquals(1, f.db.temporary().count(f.receipt.getString("authorizationId"), "inventory"))
        }
    }
    @Test fun multiMegabyteOriginalCanReopenWithoutSingleLargeCursorRow() = io {
        TemporaryFixture(folder.root).use { f ->
            val row = f.activate(); val bytes = ByteArray(3 * 1024 * 1024 + 17) { (it % 127).toByte() }
            val parts = f.payloads + ("inventory" to bytes); val manifest = JSONObject(f.manifest.toString())
            TemporaryVerifier.descriptors(manifest).first { it.getString("name") == "inventory" }
                .put("bytes", bytes.size).put("sha256", JwtVerifier.sha(bytes))
            f.db.runInTransaction {
                f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_part_chunk")
                TemporaryParts(f.db).insert(row.authorizationId, parts)
            }
            f.reopen(); assertEquals(25, f.db.temporary().count(row.authorizationId, "inventory"))
            assertArrayEquals(bytes, TemporaryParts(f.db).read(row.authorizationId, manifest).getValue("inventory"))
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_part_chunk WHERE part='inventory' AND ordinal=2")
            assertThrows(Exception::class.java) { TemporaryParts(f.db).read(row.authorizationId, manifest) }
            assertEquals(row, f.repository.prepared(f.repository.local()!!))
        }
    }
    @Test fun damagedDownloadCacheIsFetchedAgainWithoutIssuingNewQualification() = io {
        TemporaryFixture(folder.root).use { f ->
            f.beforeDownload = { if (it == "policy") throw IOException("interrupted") }
            assertThrows(IOException::class.java) { f.repository.prepare {} }
            val request = f.repository.local()!!
            File(folder.root, "downloads/${request.key}/inventory.gz").writeText("corrupt")
            f.reopen(); f.beforeDownload = {}; f.repository.prepare {}
            assertEquals(2, f.downloads.count { it == "inventory" }); assertEquals(1, f.downloads.count { it == "mapping" })
            assertEquals(1, f.calls.count { it.path == "pda/temporary-preparations" }); assertEquals(request, f.repository.local())
        }
    }
    @Test fun corruptLocalActivationDeclarationIsPreservedButNeverSent() = io {
        TemporaryFixture(folder.root).use { f ->
            val row = f.activate()
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_preparation SET ackBodyJson='{}'")
            assertThrows(Exception::class.java) { f.repository.prepare {} }
            assertFalse(f.calls.any { it.path.endsWith("download-ack") })
            assertEquals(row.authorizationId, f.repository.prepared(f.repository.local()!!)!!.authorizationId)
        }
    }
    private fun io(block: () -> Unit) = runBlocking<Unit>(Dispatchers.IO) { block() }
}
