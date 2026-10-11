package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueRoomSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    private fun grant(session: JSONObject) = RescueReadGrant(RescueReadIdentity(session.getString("deviceId"),
        session.getString("userId"), session.getString("warehouseId"))) {}
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }

    @Test fun originalFactsSurviveExpiredSessionAndReopenWithoutAnyBusinessWrite() = scenario { f ->
        val access = grant(f.session)
        val saved = f.scan(); val pending = f.receive(f.barcodes[1], "MANUAL")
        f.processor.drain(f.assignment)
        f.now = f.now.plusSeconds(172801); f.session = JSONObject()
        f.db.close(); f.db = PdaDatabase.open(f.context)
        val before = digest(f.db)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        assertEquals(listOf(saved.id, pending.id), snapshot.captures.map { it.getString("id") })
        assertEquals("WAITING_CONFIRM", snapshot.captures.last().getString("state"))
        assertEquals("1", RescueFacts.summary(snapshot).getString("active"))
        assertEquals(before, digest(f.db))
        assertFalse(snapshot.originals.containsKey("submission.json"))
        vector("formal.zip", snapshot)
    }
    @Test fun frozenPayloadAndReceiptAreCopiedFromRoomWithoutReserialization() = scenario { f ->
        val access = grant(f.session); f.scan()
        val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "合成短装核对")
        val receipt = "{ \"syntheticReceipt\" : true }\n"
        f.db.submissions().progress(frozen.id, "RESULT_UNKNOWN", receipt, "合成丢响应", false)
        val before = digest(f.db)
        val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        assertArrayEquals(frozen.payload.toByteArray(), snapshot.originals.getValue("submission.json"))
        assertArrayEquals(receipt.toByteArray(), snapshot.originals.getValue("receipt.json"))
        assertEquals(before, digest(f.db))
        vector("formal-frozen.zip", snapshot)
    }
    @Test fun scopeMismatchOrRevokedReadGrantNeverReturnsAnotherOrder() = scenario { f ->
        val real = grant(f.session)
        val bad = RescueReadGrant(real.identity.copy(operatorId = "other")) {}
        val restricted = RescueReadGrant(real.identity, setOf("other-order")) {}
        val revoked = RescueReadGrant(real.identity) { error("synthetic revoked credential") }
        val before = digest(f.db)
        for (access in listOf(bad, restricted, revoked)) {
            assertThrows(IllegalStateException::class.java) {
                RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
            }
        }
        assertEquals(before, digest(f.db))
    }
    @Test fun orderPickerDoesNotDiscloseForeignWarehouseOrOutOfScopeIdentifiers() = scenario { f ->
        val own = grant(f.session)
        assertEquals(f.assignment, RescueOrders(f.db).page(own).rows.single().target.localOrderId)
        val foreign = RescueReadGrant(own.identity.copy(warehouseId = "other-warehouse")) {}
        val restricted = RescueReadGrant(own.identity, setOf("other-order")) {}
        assertTrue(RescueOrders(f.db).page(foreign).rows.isEmpty())
        assertTrue(RescueOrders(f.db).page(restricted).rows.isEmpty())
    }
    @Test fun temporarySnapshotDoesNotConsultClearedConnectionOrRenewItsDeadline() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val access = grant(checkNotNull(f.fixture.auth)); f.start(); f.scan()
            val pending = f.receive(f.barcodes[1], "MANUAL"); f.processor.drain(f.id)
            f.fixture.auth = null; f.fixture.time = f.fixture.time.plusSeconds(172801)
            f.fixture.reopen()
            val before = digest(f.db)
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", f.id), access)
            assertEquals(pending.id, snapshot.captures.last().getString("id"))
            assertEquals("1", RescueFacts.summary(snapshot).getString("active"))
            assertEquals(before, digest(f.db)); vector("temporary.zip", snapshot)
        }
    }
    @Test fun concurrentSavingCannotMixCommittedRowsAcrossReadTransaction() = scenario { f ->
        val access = grant(f.session); f.scan()
        val next = f.receive(f.barcodes[1]); f.processor.reception.validate(next.id)
        val ready = CountDownLatch(1); val finished = CountDownLatch(1)
        var error: Throwable? = null
        lateinit var writer: Thread
        f.db.runInTransaction {
            val before = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
            writer = thread {
                ready.countDown()
                try { f.processor.saving.save(next.id) } catch (failure: Throwable) { error = failure }
                finally { finished.countDown() }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            val during = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
            assertEquals(before.scans.map { it.toString() }, during.scans.map { it.toString() })
            assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
        }
        writer.join(5000); assertFalse(writer.isAlive); assertNull(error)
        val after = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), access)
        assertEquals("2", RescueFacts.summary(after).getString("active"))
    }
    @Test fun secretBearingPublicMaterialIsRejectedBeforeAnyExternalCopy() {
        val original = "{ \"nested\" : { \"offlineToken\" : \"synthetic-do-not-export\" } }"
        assertThrows(IllegalStateException::class.java) { RescueOriginals.publicSource("MANIFEST", original) }
        val safe = "{ \"manifest\" : \"public text\" }\n"
        assertEquals(safe, RescueOriginals.publicSource("MANIFEST", safe).getString("originalText"))
    }
    private fun digest(db: PdaDatabase): String {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val all = tables.joinToString("\n") { name -> sql.query("SELECT * FROM \"${name.replace("\"", "\"\"")}\"").use { cursor ->
            val rows = buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") {
                if (cursor.isNull(it)) "NULL" else if (cursor.getType(it) == android.database.Cursor.FIELD_TYPE_BLOB)
                    Base64.getEncoder().encodeToString(cursor.getBlob(it)) else cursor.getString(it)
            }) }; name + rows.sorted().joinToString("\n")
        } }
        return RescueBundle.sha(all.toByteArray(Charsets.UTF_8))
    }
    private fun vector(name: String, snapshot: RescueSnapshot) {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val signer = RescueSigner(key.public.encoded) { text -> Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(text.toByteArray(Charsets.UTF_8)); Base64.getEncoder().encodeToString(sign())
        } }
        val bytes = RescueBundle.build(snapshot, signer)
        snapshot.originals.values.forEach { original ->
            val text = original.toString(Charsets.UTF_8)
            check(!text.contains("accessToken") && !text.contains("refreshToken") && !text.contains("offlineToken"))
        }
        File("build/rescue-room-vectors").apply { mkdirs() }.resolve(name).writeBytes(bytes)
    }
}
