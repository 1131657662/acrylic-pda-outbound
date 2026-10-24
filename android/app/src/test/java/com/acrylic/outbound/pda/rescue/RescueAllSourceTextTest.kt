package com.acrylic.outbound.pda.rescue

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueAllSourceTextTest {
    @get:Rule val files = TemporaryFolder()
    private val gbk = Charset.forName("GBK")

    @Test fun temporaryOriginalHttpNameSurvivesReopenExpiryAndWritesOnlyActiveOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(files.newFolder()).use { f ->
            f.start(); val first = f.scan(); f.processor.corrections.voidLast(f.id, first.id, "合成未装车")
            f.scan(f.barcodes[1]); f.reopen(); f.fixture.auth = null; f.fixture.time = f.fixture.time.plusSeconds(3 * 86400)
            val draft = checkNotNull(f.db.temporaryDrafts().get(f.id))
            val grant = RescueReadGrant(RescueReadIdentity(draft.creatorDeviceId, draft.operatorId, draft.warehouseId)) {}
            val before = digest(f.db)

            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), grant)
            val active = snapshot.scans.single { it.getString("status") == "ACTIVE" }
            val output = write(snapshot, grant)

            assertEquals(1, output.toString(gbk).lineSequence().count { it.isNotBlank() })
            assertTrue(output.toString(gbk).startsWith(active.getString("model") + "\t" + active.getString("barcode") + "\t" + active.getString("rawWeight") + "\t原姓名甲\t"))
            assertFalse(output.toString(gbk).contains("后改姓名乙"));assertEquals(before, digest(f.db))
        }
    }

    @Test fun independentOriginalHttpProofSurvivesRevocationExpiryAndRequestPersistence() = runBlocking<Unit>(Dispatchers.IO) {
        RescueIndependentWireFixture().use { f ->
            f.save(); f.expireAndReopen(); val before = digest(f.db)

            val snapshot = f.read(); val output = write(snapshot, f.readGrant)

            assertTrue(output.toString(gbk).contains("\t003A0000001\t"))
            assertTrue(output.toString(gbk).contains("\t原姓名甲\t"))
            assertTrue(snapshot.order.getBoolean("pendingReconciliation"))
            assertFalse(snapshot.order.getBoolean("serverAuthorizationVerified"))
            assertEquals(before, digest(f.db))
        }
    }

    @Test fun legacyTemporaryWithoutProofRemainsReadableButTextIsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val contract = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryFixture"))).readText())
        contract.getJSONObject("receipt").remove("operatorNameProof")
        TemporaryWorkFixture(files.newFolder(), contract = contract).use { f ->
            f.start();f.scan(); val d = checkNotNull(f.db.temporaryDrafts().get(f.id))
            val grant = RescueReadGrant(RescueReadIdentity(d.creatorDeviceId, d.operatorId, d.warehouseId)) {}
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), grant)
            assertEquals(1, snapshot.scans.size)
            assertTrue(runCatching { RescueOutText.encode(snapshot) }.isFailure)
        }
    }

    @Test fun forgedTemporaryProofCannotProduceSnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        val contract = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryFixture"))).readText())
        contract.getJSONObject("receipt").put("operatorNameProof", "forged.name.signature")
        TemporaryWorkFixture(files.newFolder(), contract = contract).use { f ->
            f.start();f.scan(); val d = checkNotNull(f.db.temporaryDrafts().get(f.id))
            val grant = RescueReadGrant(RescueReadIdentity(d.creatorDeviceId, d.operatorId, d.warehouseId)) {}
            assertTrue(runCatching { RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), grant) }.isFailure)
        }
    }

    @Test fun temporaryCaptureIdentityMismatchCannotBorrowOriginalName() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(files.newFolder()).use { f ->
            f.start(); f.scan()
            val d = checkNotNull(f.db.temporaryDrafts().get(f.id))
            val grant = RescueReadGrant(RescueReadIdentity(d.creatorDeviceId, d.operatorId, d.warehouseId)) {}
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), grant)
            assertTrue(RescueOutText.encode(snapshot).isNotEmpty())

            listOf("operatorId", "originDeviceId").forEach { field ->
                val damaged = snapshot.copy(captures = snapshot.captures.map {
                    JSONObject(it.toString()).put(field, java.util.UUID.randomUUID().toString())
                })
                assertTrue(field, runCatching { RescueOutText.encode(damaged) }.isFailure)
            }
        }
    }

    private fun write(snapshot: RescueSnapshot, grant: RescueReadGrant): ByteArray {
        val target = files.newFile();val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val hash = RescueOutTextFiles(app).copy(snapshot, Uri.fromFile(target), grant)
        assertArrayEquals(RescueOutText.encode(snapshot), target.readBytes())
        assertEquals(RescueBundle.sha(target.readBytes()), hash)
        return target.readBytes()
    }
    private fun digest(db: PdaDatabase): String {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        val text = tables.joinToString("\n") { table ->
            sql.query("SELECT * FROM `$table`").use { c ->
                val rows = buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") {
                    if (c.getType(it) == android.database.Cursor.FIELD_TYPE_BLOB) java.util.Base64.getEncoder().encodeToString(c.getBlob(it)) else c.getString(it).orEmpty()
                }) }
                table + rows.sorted().joinToString("\n")
            }
        }
        return RescueBundle.sha(text.toByteArray(Charsets.UTF_8))
    }
}
