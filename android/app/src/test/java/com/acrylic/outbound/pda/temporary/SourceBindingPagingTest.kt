package com.acrylic.outbound.pda.temporary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.support.TestInfrastructure.page
import com.acrylic.outbound.pda.sync.SyncRun
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SourceBindingPagingTest {
    @get:Rule val folder = TemporaryFolder()
    private val auth = JSONObject().put("userId", id(1)).put("deviceId", id(2)).put("warehouseId", id(3))
        .put("serverRecoveryEpoch", "1").put("localSessionId", "local").put("userAuthVersion", "1").put("deviceVersion", "1")
    private var database: PdaDatabase? = null
    private fun db(): PdaDatabase = database ?: Room.databaseBuilder(ApplicationProvider.getApplicationContext(),
        PdaDatabase::class.java, folder.root.resolve("binding.db").absolutePath).allowMainThreadQueries().build().also { database = it }
    @After fun close() { database?.close() }

    @Test fun restartContinuesAfterBoundedPassAndNextCycleRefreshesFromBeginning() {
        val seen = mutableListOf<String>()
        val request: (String, String) -> JSONObject = { path, _ ->
            seen.add(path)
            if (path.contains("cursor=")) page(listOf(row(10))) else page(listOf(row(20)), id(20))
        }
        assertTrue(SourceBindingRepository(db(), { auth }, request).installBindings(run = SyncRun(batches = 1)).more)
        database!!.close(); database = null
        assertFalse(SourceBindingRepository(db(), { auth }, request).installBindings(run = SyncRun(batches = 1)).more)
        assertTrue(seen.last().contains("cursor=${id(20)}")); assertEquals(2, db().sourceBindings().count())
        SourceBindingRepository(db(), { auth }, request).installBindings(run = SyncRun(batches = 1))
        assertFalse(seen.last().contains("cursor="))
    }

    @Test fun failedPageDoesNotAdvanceCursorOrLeavePartialBindings() {
        SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(20)), id(20)) }.installBindings(run = SyncRun(batches = 1))
        val before = SourceBindingCheckpoint(db(), auth).read()
        val failing = SourceBindingRepository(db(), { auth }) { path, _ ->
            assertTrue(path.contains("cursor=${id(20)}")); page(listOf(row(10), row(9).put("mappingVersion", "bad")))
        }
        assertThrows(Exception::class.java) { failing.installBindings() }
        assertEquals(before, SourceBindingCheckpoint(db(), auth).read()); assertEquals(1, db().sourceBindings().count())
    }

    @Test fun checkpointFinalWriteFailureRollsBackTheBindingPage() {
        db().openHelper.writableDatabase.execSQL("CREATE TRIGGER checkpoint_failure BEFORE INSERT ON local_sync_state " +
            "BEGIN SELECT RAISE(ABORT, 'checkpoint last write'); END")
        assertThrows(Exception::class.java) {
            SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(20)), id(20)) }.installBindings()
        }
        assertEquals(0, db().sourceBindings().count()); assertNull(SourceBindingCheckpoint(db(), auth).read())
    }

    @Test fun anotherConsumerAdvancingCheckpointRejectsStalePageAtomically() {
        val first = SourceBindingRepository(db(), { auth }) { _, _ ->
            SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(30)), id(30)) }.installBindings(run = SyncRun(batches = 1))
            page(listOf(row(20)), id(20))
        }
        assertThrows(Exception::class.java) { first.installBindings(run = SyncRun(batches = 1)) }
        assertEquals(listOf(id(30)), db().sourceBindings().all().map { it.scopeId })
        assertEquals(id(30), SourceBindingCheckpoint(db(), auth).read()!!.cursor)
    }

    @Test fun freshSourceReadDoesNotAdvanceBulkCursorAndRejectsIgnoredFilter() {
        SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(20)), id(20)) }.installBindings(run = SyncRun(batches = 1))
        val before = SourceBindingCheckpoint(db(), auth).read(); val wanted = row(10)
        SourceBindingRepository(db(), { auth }) { path, _ ->
            assertTrue(path.contains("clientOrderId=${wanted.getString("clientOrderId")}")); assertFalse(path.contains("cursor="))
            page(listOf(wanted))
        }.installSource(wanted.getString("clientOrderId"))
        assertEquals(before, SourceBindingCheckpoint(db(), auth).read())
        assertThrows(Exception::class.java) {
            SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(40))) }.installSource(wanted.getString("clientOrderId"))
        }
        assertNull(db().sourceBindings().get(id(40)))
    }

    @Test fun newAuthorizationUsesFreshCheckpointAndRepeatedCursorIsRejected() {
        SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(20)), id(20)) }.installBindings(run = SyncRun(batches = 1))
        assertThrows(Exception::class.java) {
            SourceBindingRepository(db(), { auth }) { _, _ -> page(listOf(row(10)), id(20)) }.installBindings()
        }
        assertNull(db().sourceBindings().get(id(10)))
        auth.put("userAuthVersion", "2")
        SourceBindingRepository(db(), { auth }) { path, _ -> assertFalse(path.contains("cursor=")); page(emptyList()) }.installBindings()
    }

    private fun row(n: Long) = JSONObject().put("scopeId", id(n)).put("clientOrderId", id(n + 100)).put("canonicalOrderId", id(n + 200))
        .put("mappingId", id(n + 300)).put("mappingVersion", "2").put("mappingOrigin", "CONTROLLED_LINK")
        .put("installRequired", true).put("workRound", "1").put("phase", "FROZEN").put("orderNo", "SYNTHETIC-$n")
    private fun id(n: Long) = UUID(0, n).toString()
}
